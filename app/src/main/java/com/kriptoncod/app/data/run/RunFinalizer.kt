package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.JobState
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Run tamamlandığında: tüm iş çıktılarını tek zip'te toplar,
 * eksik/çakışan çıktıları raporlar.
 *
 * Zip içeriği:
 *  - AI çıktısında ```yol/dosya biçiminde kod blokları varsa her blok kendi yoluna yazılır
 *    (güvenli olmayan yollar atlanır; iş sırasıyla ilerlenir, aynı yola yazan sonraki blok öncekini ezer),
 *  - hiç güvenli blok yoksa ham çıktı `output_<promptId>.txt` olarak konur.
 */
class RunFinalizer(private val runs: RunRepository) {

    sealed interface Result {
        data object Success : Result

        /** En az bir iş çıktısı eksik veya boş. */
        data class HasTruncated(val jobIds: List<Long>) : Result

        /** Aynı prompt için birden fazla tamamlanmış iş var: promptId -> iş kimlikleri. */
        data class NeedsConflictResolution(val conflicts: Map<Long, List<Long>>) : Result
    }

    /**
     * [runId] için zip üretir.
     * Önce tüm DONE işlerin çıktılarını okur; sonra çakışma denetimi yapar.
     */
    suspend fun finalize(runId: Long): Result {
        val jobs = runs.getJobs(runId).filter { it.state == JobState.DONE }
        if (jobs.isEmpty()) return Result.HasTruncated(emptyList())

        val outputs = mutableMapOf<Long, String>()
        val truncated = mutableListOf<Long>()
        for (job in jobs) {
            val text = runs.readOutput(job.id)
            if (text.isNullOrBlank()) {
                truncated += job.id
            } else {
                outputs[job.id] = text
            }
        }
        if (truncated.isNotEmpty()) return Result.HasTruncated(truncated)

        // Çakışma: aynı promptId için birden fazla tamamlanmış iş.
        val byPrompt = jobs.groupBy { it.promptId }
        val conflicts = byPrompt.filter { it.value.size > 1 }
            .mapValues { entry -> entry.value.map { it.id } }
        if (conflicts.isNotEmpty()) return Result.NeedsConflictResolution(conflicts)

        val files = linkedMapOf<String, String>()
        for (job in jobs.sortedBy { it.id }) {
            val text = outputs[job.id] ?: continue
            val blocks = CodeBlockParser.parse(text).mapNotNull { b ->
                when (val r = PathSafety.validate(b.path)) {
                    is PathSafety.Result.Safe -> r.normalized to b.content
                    is PathSafety.Result.Rejected -> null
                }
            }
            if (blocks.isEmpty()) {
                files["output_${job.promptId}.txt"] = text
            } else {
                blocks.forEach { (path, content) -> files[path] = content }
            }
        }

        runs.attachZip(runId) { os ->
            ZipOutputStream(os).use { zos ->
                for ((path, content) in files) {
                    zos.putNextEntry(ZipEntry(path))
                    zos.write(content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        }
        return Result.Success
    }
}
