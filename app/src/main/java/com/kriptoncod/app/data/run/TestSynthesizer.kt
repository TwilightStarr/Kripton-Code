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
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first

/**
 * Push'tan önce projede gerçek test yoksa AI'a test dosyası yazdırır ve zip'e ekler. Test olmadan CI yalnızca
 * "derleniyor mu"yu bilir; test varsa davranışı da doğrular.
 *
 *  - Projenin kendi `.kriptoncod/verify.sh` dosyası varsa, tür algılanamıyorsa ya da test zaten varsa dokunmaz.
 *  - Yalnız test yollarına yazılabilir ([VerifyScripts.isAllowedTestPath]); kaynak kod değişmez.
 *  - Hesaplar güce göre sırayla denenir; biri hata verirse sıradaki denenir.
 *  - Sonuç `_kripton/tests.md` olarak zip'e yazılır (push'a girmez); başarısızlık sessiz kalmaz.
 */
class TestSynthesizer(
    private val runs: RunRepository,
    private val accounts: AiAccountRepository,
    private val credentials: CredentialProvider,
    private val generator: AiGenerator,
    private val resolveEndpoint: (AiAccount) -> ProviderEndpoint? = AccountEndpoints::resolve,
) {

    sealed interface Outcome {
        data object NotNeeded : Outcome
        data class Added(val paths: List<String>) : Outcome
        data class Failed(val reason: String) : Outcome
    }

    suspend fun ensure(runId: Long): Outcome {
        val all = runs.readZipFiles(runId)
        if (all.isEmpty()) return Outcome.NotNeeded
        val project = all.filter { (p, _) -> !p.startsWith("_kripton/") && !p.startsWith(".github/") }.toMap()
        if (VerifyScripts.PATH in project) return Outcome.NotNeeded
        val type = VerifyScripts.detect(project.keys)
        if (type == VerifyScripts.ProjectType.UNKNOWN || VerifyScripts.hasTests(type, project)) return Outcome.NotNeeded

        val prompt = buildPrompt(type, project)
        val candidates = accounts.observeAll().first()
            .filter { it.enabled && it.status == AccountStatus.READY && it.authType != AuthType.WEB_SESSION }
            .sortedByDescending { it.powerRank }
        if (candidates.isEmpty()) return finish(runId, all, Outcome.Failed("Kullanılabilir AI hesabı yok."))

        var lastError = "Hiçbir hesap kullanılabilir test üretmedi."
        for (account in candidates) {
            val endpoint = resolveEndpoint(account)
            val credential = credentials.credentialFor(account)
            if (endpoint == null || credential.isNullOrBlank()) continue
            val text = try {
                generator.generate(endpoint, account.modelId, credential, prompt)
            } catch (e: AiGenerationException) {
                lastError = "${account.displayName}: ${e.message}"
                continue
            }
            val tests = CodeBlockParser.parse(text).mapNotNull { b ->
                val safe = (PathSafety.validate(b.path) as? PathSafety.Result.Safe)?.normalized ?: return@mapNotNull null
                if (b.content.isBlank() || !VerifyScripts.isAllowedTestPath(type, safe)) null else safe to b.content
            }.toMap()
            if (tests.isEmpty()) {
                lastError = "${account.displayName}: yanıtta geçerli test dosyası yok."
                continue
            }
            return finish(runId, all, Outcome.Added(tests.keys.toList()), extra = tests.mapValues { it.value.toByteArray(Charsets.UTF_8) })
        }
        return finish(runId, all, Outcome.Failed(lastError))
    }

    private fun buildPrompt(type: VerifyScripts.ProjectType, project: Map<String, ByteArray>): String = buildString {
        appendLine("Aşağıdaki projeye GERÇEK, çalıştırılabilir testler yaz. Testler CI'da ağ erişimi olmadan çalışacak.")
        appendLine(VerifyScripts.testGuidance(type))
        appendLine(
            "Kurallar: yalnızca test dosyaları yaz (kaynak kodu, yapılandırmayı ve workflow'u DEĞİŞTİRME); " +
                "her test bir davranışı sınamalı (yalnızca `assert true` ya da boş test yazma); " +
                "dosyaları ```tam/dosya/yolu biçiminde kod blokları olarak döndür, açıklama yazma. " +
                "Aşağıda kaynak dosyaların bildirim özeti var (gövdeler çıkarıldı).",
        )
        appendLine()
        val files = project.entries.sortedBy { it.key }.mapNotNull { (p, b) ->
            if (b.any { it == 0.toByte() }) null else p to String(b, Charsets.UTF_8)
        }
        appendLine(InterfaceSummarizer.summarizeFiles(files, SUMMARY_BUDGET))
    }

    private suspend fun finish(
        runId: Long,
        all: List<Pair<String, ByteArray>>,
        outcome: Outcome,
        extra: Map<String, ByteArray> = emptyMap(),
    ): Outcome {
        val entries = LinkedHashMap<String, ByteArray>()
        all.forEach { (p, b) -> entries[p] = b }
        extra.forEach { (p, b) -> entries[p] = b }
        val note = when (outcome) {
            is Outcome.Added -> "AI ${outcome.paths.size} test dosyası ekledi:\n" + outcome.paths.joinToString("\n") { "- $it" }
            is Outcome.Failed -> "Test yazdırılamadı: ${outcome.reason}\nCI'da \"TEST YOK\" hatası beklenir; testleri elle ekleyin."
            Outcome.NotNeeded -> return outcome
        }
        entries["_kripton/tests.md"] = ("# Test üretimi\n\n$note\n").toByteArray(Charsets.UTF_8)
        runs.attachZip(runId) { os ->
            ZipOutputStream(os).use { zos ->
                for ((path, bytes) in entries) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
        return outcome
    }

    private companion object {
        const val SUMMARY_BUDGET = 24_000
    }
}
