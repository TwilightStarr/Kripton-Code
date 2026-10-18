package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.data.web.WebOutputCheck
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Run tamamlandığında: tüm iş çıktılarını tek zip'te toplar, eksik/çakışan çıktıları raporlar.
 *
 * Zip içeriği:
 *  - AI çıktısındaki ```yol/dosya blokları kendi yoluna yazılır (güvenli olmayan yollar atlanır),
 *  - yolsuz (yalnız dil etiketli) bloklar KAYBOLMAZ: `unnamed/` altına alınır ve raporda uyarılır,
 *  - aynı yola yazan sonraki blok öncekini ezer; ancak çok daha KISA ise ana dosya korunur, kısa sürüm
 *    `_kripton/conflicts/` altına konur,
 *  - ikili dosyalar (görsel vb.) ve sağlayıcının orijinal zip'i (`_kripton/raw/`) korunur,
 *  - `_kripton/report.md`: düğüm tablosu, yer tutucu/kesik/çakışma uyarıları.
 */
class RunFinalizer(private val runs: RunRepository) {

    sealed interface Result {
        data object Success : Result

        /** En az bir iş çıktısı eksik veya boş. */
        data class HasTruncated(val jobIds: List<Long>) : Result

        /** Aynı prompt için birden fazla tamamlanmış iş var: promptId -> iş kimlikleri. */
        data class NeedsConflictResolution(val conflicts: Map<Long, List<Long>>) : Result
    }

    /** Zip'e girecek dosyalar ve birleştirme notları. */
    private class FileSet {
        val files = linkedMapOf<String, ByteArray>()
        val notes = mutableListOf<String>()

        fun putText(path: String, content: String, source: String) = put(path, content.toByteArray(Charsets.UTF_8), source)

        /** Aynı yol: sonraki kazanır; ama çok daha kısaysa (<%60) ana dosya korunur, kısa sürüm ayrı saklanır. */
        fun put(path: String, bytes: ByteArray, source: String) {
            val old = files[path]
            if (old != null && old.size > MIN_GUARD && bytes.size * 10 < old.size * 6) {
                files["_kripton/conflicts/$source/$path"] = bytes
                notes += "Çakışma: $path — $source sürümü çok daha kısa olduğu için ana dosya korundu " +
                    "(kısa sürüm: _kripton/conflicts/$source/$path)."
            } else {
                files[path] = bytes
            }
        }
    }

    suspend fun finalize(runId: Long): Result {
        val all = runs.getJobs(runId)
        if (all.any { it.kind != JobKind.WORK }) return finalizeGraph(runId, all)
        val jobs = all.filter { it.state == JobState.DONE }
        if (jobs.isEmpty()) return Result.HasTruncated(emptyList())

        val outputs = mutableMapOf<Long, String>()
        val truncated = mutableListOf<Long>()
        for (job in jobs) {
            val text = runs.readOutput(job.id)
            if (text.isNullOrBlank()) truncated += job.id else outputs[job.id] = text
        }
        if (truncated.isNotEmpty()) return Result.HasTruncated(truncated)

        // Çakışma: aynı promptId için birden fazla tamamlanmış iş.
        val byPrompt = jobs.groupBy { it.promptId }
        val conflicts = byPrompt.filter { it.value.size > 1 }.mapValues { entry -> entry.value.map { it.id } }
        if (conflicts.isNotEmpty()) return Result.NeedsConflictResolution(conflicts)

        val set = FileSet()
        for (job in jobs.sortedBy { it.id }) {
            val text = outputs[job.id] ?: continue
            val blocks = safeBlocks(text, job.id)
            if (blocks.isEmpty()) {
                set.putText("output_${job.promptId}.txt", text, "job${job.id}")
            } else {
                blocks.forEach { (path, content) -> set.putText(path, content, "job${job.id}") }
            }
        }
        addAssets(set, jobs)
        writeZip(runId, all, outputs, set, extra = emptyMap())
        return Result.Success
    }

    /**
     * Düğüm grafiği: nihai birleştirmenin dosyaları zip'in köküne yazılır (blok yoksa `final.md`).
     * Birleştirme AI'ı bir dosyayı düşürmüş ya da çok kısaltmışsa kaynak düğümlerden tamamlanır; bunlar raporlanır.
     * Her düğümün çıktısı eksiksiz kayıt için ayrıca `_kripton/nodes/` altına konur.
     */
    private suspend fun finalizeGraph(runId: Long, all: List<Job>): Result {
        val final = all.firstOrNull { it.kind == JobKind.FINAL_MERGE && it.state == JobState.DONE }
        val finalText = final?.let { runs.readOutput(it.id) }
        if (final == null || finalText.isNullOrBlank()) return Result.HasTruncated(listOfNotNull(final?.id))

        val outputs = mutableMapOf<Long, String>()
        for (job in all.filter { it.state == JobState.DONE }) runs.readOutput(job.id)?.let { outputs[job.id] = it }

        val set = FileSet()
        val finalBlocks = safeBlocks(finalText, final.id)
        if (finalBlocks.isEmpty()) {
            set.putText("final.md", finalText, "final")
        } else {
            finalBlocks.forEach { (p, c) -> set.putText(p, c, "final") }
        }

        // Tamamlık denetimi: birleştirmeye girmesi gereken düğümlerin dosyaları nihai pakette var mı?
        val shareable = all.filter {
            it.state == JobState.DONE && (
                it.kind == JobKind.CATALOG_MERGE ||
                    (it.kind == JobKind.WORK && FlowDirectivesParser.parse(it.body.orEmpty()).share)
                )
        }
        val mergeVersion = linkedMapOf<String, Pair<String, Long>>()
        val workVersion = linkedMapOf<String, Pair<String, Long>>()
        for (job in shareable.sortedBy { it.id }) {
            val text = outputs[job.id] ?: continue
            val target = if (job.kind == JobKind.CATALOG_MERGE) mergeVersion else workVersion
            for ((path, content) in safeBlocks(text, job.id)) {
                val prev = target[path]
                if (prev == null || content.length >= prev.first.length) target[path] = content to job.id
            }
        }
        val best = linkedMapOf<String, Pair<String, Long>>()
        workVersion.forEach { (p, v) -> best[p] = v }
        mergeVersion.forEach { (p, v) -> best[p] = v }
        for ((path, v) in best) {
            val (content, jobId) = v
            val have = set.files[path]
            if (have == null) {
                set.putText(path, content, "job$jobId")
                set.notes += "Nihai birleştirmede EKSİKTİ, düğüm $jobId'den tamamlandı: $path"
            } else if (content.length > 400 && have.size * 2 < content.toByteArray(Charsets.UTF_8).size) {
                set.files["_kripton/alternatives/job$jobId/$path"] = content.toByteArray(Charsets.UTF_8)
                set.notes += "Kısalmış olabilir: $path nihai pakette, düğüm $jobId sürümünün yarısından kısa " +
                    "(tam sürüm: _kripton/alternatives/job$jobId/$path)."
            }
        }

        addAssets(set, all.filter { it.state == JobState.DONE && it.kind == JobKind.WORK })

        val nodeFiles = linkedMapOf<String, ByteArray>()
        for (job in all.filter { it.state == JobState.DONE }) {
            val text = outputs[job.id] ?: continue
            val name = (job.catalog ?: "").replace(Regex("[^A-Za-z0-9_-]"), "_")
            nodeFiles["_kripton/nodes/${job.id}_${job.kind.name.lowercase()}${if (name.isEmpty()) "" else "_$name"}.md"] =
                text.toByteArray(Charsets.UTF_8)
        }
        writeZip(runId, all, outputs, set, extra = nodeFiles)
        return Result.Success
    }

    // --- yardımcılar ----------------------------------------------------------------------------

    /** Güvenli yollu bloklar; yolsuz bloklar `unnamed/job<id>_part_N.<uzantı>` olarak kurtarılır. */
    private fun safeBlocks(text: String, jobId: Long): List<Pair<String, String>> = CodeBlockParser.parse(WebOutputCheck.canonical(text)).mapNotNull { b ->
        val path = if (b.path.startsWith("unnamed/part_")) "unnamed/job${jobId}_${b.path.removePrefix("unnamed/")}" else b.path
        when (val r = PathSafety.validate(path)) {
            is PathSafety.Result.Safe -> r.normalized to b.content
            is PathSafety.Result.Rejected -> null
        }
    }

    /** İşlerin ham/ikili dosyaları: özgün yoluna, doluysa `_kripton/alternatives/`; orijinal zip `_kripton/raw/`. */
    private suspend fun addAssets(set: FileSet, jobs: List<Job>) {
        for (job in jobs.sortedBy { it.id }) {
            for ((path, bytes) in runs.readAssets(job.id)) {
                when {
                    path.startsWith("_kripton/raw/") ->
                        set.files["_kripton/raw/job${job.id}_${path.removePrefix("_kripton/raw/")}"] = bytes

                    path in set.files -> {
                        set.files["_kripton/alternatives/job${job.id}/$path"] = bytes
                        set.notes += "İkili dosya yolu çakıştı: $path — düğüm ${job.id} sürümü _kripton/alternatives/ altında."
                    }

                    else -> set.files[path] = bytes
                }
            }
        }
    }

    private suspend fun writeZip(
        runId: Long,
        all: List<Job>,
        outputs: Map<Long, String>,
        set: FileSet,
        extra: Map<String, ByteArray>,
    ) {
        val scan = set.files.filter { (p, b) -> !p.startsWith("_kripton/") && b.none { it == 0.toByte() } }
            .map { (p, b) -> CodeBlockParser.Block(p, String(b, Charsets.UTF_8)) }
        // Push/CI'dan önce bozuk (kesik, dengesiz parantezli, geçersiz JSON/XML) dosyaları rapora işle.
        SyntaxCheck.checkBlocks(scan).forEach { set.notes += "Sözdizimi uyarısı: ${it.path}:${it.line} — ${it.message}" }
        val report = RunReport.build(runId, all, outputs, set.notes, PlaceholderDetector.find(scan))

        val finalFiles = LinkedHashMap<String, ByteArray>(set.files)
        finalFiles.putAll(extra)
        finalFiles["_kripton/report.md"] = report.toByteArray(Charsets.UTF_8)

        runs.attachZip(runId) { os ->
            ZipOutputStream(os).use { zos ->
                for ((path, bytes) in finalFiles) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(bytes)
                    zos.closeEntry()
                }
            }
        }
    }

    private companion object {
        const val MIN_GUARD = 200
    }
}
