package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState

/**
 * Zip'e `_kripton/report.md` olarak konan denetim raporu: hangi düğüm ne yaptı, kaç denemede bitti ve
 * nerede eksik/kısaltılmış/çakışan çıktı var. Hiçbir uyarı sessizce yutulmaz.
 */
object RunReport {

    fun build(
        runId: Long,
        jobs: List<Job>,
        outputs: Map<Long, String>,
        notes: List<String>,
        finalPlaceholders: List<PlaceholderDetector.Finding>,
    ): String = buildString {
        appendLine("# Kripton Code — Run #$runId raporu")
        appendLine()
        appendLine("## Düğümler")
        appendLine()
        appendLine("| Düğüm | Tür | Başlık | Durum | Deneme | Çıktı (karakter) |")
        appendLine("|---|---|---|---|---|---|")
        jobs.sortedBy { it.id }.forEach { j ->
            val len = outputs[j.id]?.length ?: 0
            appendLine("| ${j.id} | ${kindName(j.kind)} | ${cell(j.title)} | ${j.state.name} | ${j.attempts} | $len |")
        }
        val failed = jobs.filter { it.state == JobState.FAILED }
        val warnings = mutableListOf<String>()
        failed.forEach { warnings += "Düğüm ${it.id} (${cell(it.title)}) BAŞARISIZ oldu; çıktısı pakete girmedi." }
        for (j in jobs.sortedBy { it.id }) {
            val text = outputs[j.id] ?: continue
            val a = CodeBlockParser.analyze(text)
            if (a.unclosedFence) warnings += "Düğüm ${j.id} (${cell(j.title)}): çıktı kapanmamış kod bloğuyla bitiyor (kesilmiş olabilir)."
            val pathless = a.blocks.count { it.path == null }
            if (pathless > 0 && j.kind == JobKind.WORK) {
                warnings += "Düğüm ${j.id} (${cell(j.title)}): $pathless yolsuz kod bloğu `unnamed/` altına alındı; elle gözden geçirin."
            }
            val ph = PlaceholderDetector.findInText(text)
            if (ph.isNotEmpty()) {
                warnings += "Düğüm ${j.id} (${cell(j.title)}): ${ph.size} yer tutucu satırı — " +
                    ph.take(5).joinToString("; ") { "${it.path}:${it.line}" } + if (ph.size > 5) " …" else ""
            }
        }
        if (finalPlaceholders.isNotEmpty()) {
            warnings += "NİHAİ PAKETTE ${finalPlaceholders.size} yer tutucu satırı kaldı: " +
                finalPlaceholders.take(8).joinToString("; ") { "${it.path}:${it.line}" } + if (finalPlaceholders.size > 8) " …" else ""
        }
        warnings += notes
        appendLine()
        appendLine("## Uyarılar")
        appendLine()
        if (warnings.isEmpty()) appendLine("Uyarı yok.") else warnings.forEach { appendLine("- $it") }
    }

    private fun kindName(k: JobKind) = when (k) {
        JobKind.PLAN -> "katalog"
        JobKind.WORK -> "prompt"
        JobKind.CATALOG_MERGE -> "katalog birleştirme"
        JobKind.FINAL_MERGE -> "nihai birleştirme"
    }

    private fun cell(s: String?) = (s ?: "-").replace("|", "/").replace("\n", " ")
}
