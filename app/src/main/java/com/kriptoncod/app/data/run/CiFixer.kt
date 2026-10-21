package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerator
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.providers.AccountEndpoints
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.CiLogParser
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first

/**
 * CI hatasını AI'a düzelttirir. Eski sürüm yalnızca hata satırlarını gönderiyordu (AI dosyayı görmeden
 * tahmin ediyordu) ve tek hesap deniyordu. Şimdi:
 *  - hatanın geçtiği dosyaların GÜNCEL içeriği ve projenin arayüz özeti istemde,
 *  - hesaplar güce göre sırayla denenir; hata/boş/geçersiz cevapta sıradaki hesaba geçilir,
 *  - başlangıç hesabı her deneme turunda kaydırılır: aynı hesap aynı düzeltmeyi tekrar tekrar yanlış yapamaz,
 *  - AI bir dosyayı çok kısaltırsa (kesik çıktı) o dosya reddedilir.
 */
class CiFixer(
    private val runs: RunRepository,
    private val accounts: AiAccountRepository,
    private val credentials: CredentialProvider,
    private val generator: AiGenerator,
    private val resolveEndpoint: (AiAccount) -> ProviderEndpoint? = AccountEndpoints::resolve,
) {

    sealed interface Result {
        data class Fixed(val files: List<CodeBlockParser.Block>, val accountName: String) : Result
        data class Failed(val reason: String) : Result
    }

    /** @param attempt 1'den başlayan CI düzeltme turu; başlangıç hesabını belirler. */
    suspend fun fix(runId: Long, errors: List<CiLogParser.CiError>, attempt: Int): Result {
        val project = projectFiles(runId)
        val prompt = CiFixPrompt.build(errors, project)
        val candidates = accounts.observeAll().first()
            .filter { it.enabled && it.status == AccountStatus.READY && it.authType != AuthType.WEB_SESSION }
            .sortedByDescending { it.powerRank }
        if (candidates.isEmpty()) return Result.Failed("Kullanılabilir AI hesabı yok.")
        val start = (attempt - 1).coerceAtLeast(0) % candidates.size
        val ordered = candidates.drop(start) + candidates.take(start)

        var last = "Hiçbir hesap geçerli düzeltme üretmedi."
        for (account in ordered) {
            val endpoint = resolveEndpoint(account)
            val credential = credentials.credentialFor(account)
            if (endpoint == null || credential.isNullOrBlank()) continue
            val text = try {
                generator.generate(endpoint, account.modelId, credential, prompt)
            } catch (e: AiGenerationException) {
                last = "${account.displayName}: ${e.kind}"
                continue
            }
            val blocks = acceptable(text, project)
            if (blocks.isEmpty()) {
                last = "${account.displayName}: yanıtta kullanılabilir dosya yok."
                continue
            }
            return Result.Fixed(blocks, account.displayName)
        }
        return Result.Failed(last)
    }

    /** Düzeltmeyi run'ın zip'ine işler; sonraki turlar güncel içerikle çalışsın. */
    suspend fun applyToZip(runId: Long, files: List<CodeBlockParser.Block>) {
        val entries = LinkedHashMap<String, ByteArray>()
        runs.readZipFiles(runId).forEach { (p, b) -> entries[p] = b }
        files.forEach { entries[it.path] = it.content.toByteArray(Charsets.UTF_8) }
        runs.attachZip(runId) { os ->
            ZipOutputStream(os).use { zos ->
                for ((p, b) in entries) {
                    zos.putNextEntry(ZipEntry(p))
                    zos.write(b)
                    zos.closeEntry()
                }
            }
        }
    }

    private suspend fun projectFiles(runId: Long): Map<String, String> = runs.readZipFiles(runId)
        .filter { (p, b) -> !p.startsWith("_kripton/") && b.none { it == 0.toByte() } }
        .associate { (p, b) -> p to String(b, Charsets.UTF_8) }

    private fun acceptable(text: String, project: Map<String, String>): List<CodeBlockParser.Block> = CodeBlockParser.parse(text).mapNotNull { b ->
        val safe = (PathSafety.validate(b.path) as? PathSafety.Result.Safe)?.normalized ?: return@mapNotNull null
        if (b.content.isBlank()) return@mapNotNull null
        val old = project[safe]
        // Mevcut büyük bir dosyanın yarısından kısa "düzeltme" büyük olasılıkla kesik çıktıdır.
        if (old != null && old.length > 400 && b.content.length * 2 < old.length) return@mapNotNull null
        b.copy(path = safe)
    }.associateBy { it.path }.values.toList() // aynı yol iki kez gelirse sonuncusu
}

/** CI düzeltme istemi: hatalar + hatalı dosyaların içeriği + projenin arayüz özeti. */
object CiFixPrompt {

    private const val PER_FILE_CHARS = 20_000
    private const val TOTAL_FILE_CHARS = 60_000
    private const val SUMMARY_BUDGET = 12_000
    private const val WINDOW_LINES = 200

    /** Günlükteki (genelde mutlak) [logPath] ile eşleşen proje yolu; yoksa null. En uzun sonek kazanır. */
    fun resolvePath(logPath: String, projectPaths: Collection<String>): String? {
        val norm = logPath.replace('\\', '/').removePrefix("./")
        if (norm.isEmpty()) return null
        return projectPaths.filter { norm == it || norm.endsWith("/$it") }.maxByOrNull { it.length }
    }

    fun build(errors: List<CiLogParser.CiError>, project: Map<String, String>): String = buildString {
        appendLine(
            "Aşağıdaki CI hatalarını düzelt. Yalnızca DEĞİŞTİRDİĞİN dosyaları, her birini baştan sona EKSİKSİZ " +
                "olarak ```tam/dosya/yolu biçiminde kod bloklarıyla döndür; açıklama yazma, dosyayı kısaltma, " +
                "'geri kalanı aynı' gibi yer tutucu kullanma. Hata bir test dosyasındaysa testi değil, " +
                "gerçek hatayı düzeltmeyi tercih et; testi yalnızca testin kendisi yanlışsa değiştir.",
        )
        appendLine()
        appendLine(CiLogParser.toPromptSummary(errors))

        // Hatanın geçtiği dosyalar (günceli): ilk hata sırasına göre.
        val firstLine = linkedMapOf<String, Int>()
        for (e in errors) {
            if (e.file.isEmpty()) continue
            val p = resolvePath(e.file, project.keys) ?: continue
            if (p !in firstLine) firstLine[p] = e.line
        }
        var used = 0
        val included = mutableListOf<String>()
        for ((path, line) in firstLine) {
            if (used >= TOTAL_FILE_CHARS) break
            val body = excerpt(project.getValue(path), line, minOf(PER_FILE_CHARS, TOTAL_FILE_CHARS - used))
            appendLine()
            appendLine("##### HATALI DOSYANIN GÜNCEL İÇERİĞİ: $path")
            appendLine(body)
            used += body.length
            included += path
        }

        val rest = project.filterKeys { it !in included }.entries.sortedBy { it.key }.map { it.key to it.value }
        if (rest.isNotEmpty()) {
            appendLine()
            appendLine("##### PROJENİN DİĞER DOSYALARI (arayüz özeti; gövdeler çıkarıldı, yeniden yazma)")
            appendLine(InterfaceSummarizer.summarizeFiles(rest, SUMMARY_BUDGET))
        }
    }

    /** [content] [cap] karakteri aşıyorsa hata satırı [line] çevresindeki pencereyi (yoksa başı) verir. */
    fun excerpt(content: String, line: Int, cap: Int): String {
        if (content.length <= cap) return content
        val lines = content.split('\n')
        val center = if (line > 0) (line - 1).coerceIn(0, lines.size - 1) else 0
        var from = (center - WINDOW_LINES).coerceAtLeast(0)
        var to = (center + WINDOW_LINES).coerceAtMost(lines.size)
        var text = lines.subList(from, to).joinToString("\n")
        while (text.length > cap && to - from > 20) {
            from += 5
            to -= 5
            text = lines.subList(from, to).joinToString("\n")
        }
        return "(dosya uzun; yalnız ${from + 1}-$to. satırlar)\n$text"
    }
}
