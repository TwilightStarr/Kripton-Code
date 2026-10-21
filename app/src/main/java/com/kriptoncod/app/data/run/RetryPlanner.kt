package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState

/**
 * Başarısız bir düğüm yeniden denendiğinde hangi düğümlerin sıfırlanacağını belirler (saf mantık).
 *
 * Sıfırlananlar: düğümün kendisi + çıktısına dayanan her şey, YALNIZ zaten bitmiş (DONE/FAILED) olanlar:
 *  - `[[girdi: ...]]` ya da `{{çıktı:..}}` türü başvurularla bu düğümün çıktısını isteyen WORK düğümleri (zincirleme),
 *  - etkilenen WORK'lerin katalog birleştirmesi,
 *  - nihai birleştirme.
 * Aksi halde birleştirmeler, başarısız düğümün eksik çıktısıyla üretilmiş eski sonucu taşırdı.
 * Hâlâ bekleyen/çalışan düğümlere dokunulmaz. Hepsi bitince zip yeniden derlenir.
 */
object RetryPlanner {

    /** Kendisi ilk sırada olmak üzere sıfırlanacak düğüm kimlikleri; düğüm FAILED değilse boş. */
    fun plan(failed: Job, all: List<Job>): List<Long> {
        if (failed.state != JobState.FAILED) return emptyList()
        val reset = linkedSetOf(failed.id)
        val done = { j: Job -> j.state == JobState.DONE || j.state == JobState.FAILED }

        if (failed.kind == JobKind.WORK) {
            // girdi zinciri
            val titles = mutableSetOf(FlowDirectivesParser.normalize(failed.title))
            val sourceIds = mutableMapOf(FlowDirectivesParser.normalize(failed.title) to failed.id)
            var changed = true
            while (changed) {
                changed = false
                for (w in all) {
                    if (w.kind != JobKind.WORK || w.id in reset || !done(w)) continue
                    val wanted = FlowDirectivesParser.parse(w.body.orEmpty()).dependencies
                        .map { FlowDirectivesParser.normalize(it) }
                    val dependsOnChanged = wanted.any { name -> name in titles && (sourceIds[name] ?: Long.MAX_VALUE) < w.id }
                    if (dependsOnChanged) {
                        reset += w.id
                        val key = FlowDirectivesParser.normalize(w.title)
                        titles += key
                        sourceIds.putIfAbsent(key, w.id)
                        changed = true
                    }
                }
            }
            val catalogs = all.filter { it.id in reset && it.kind == JobKind.WORK }.mapNotNull { it.catalog }.toSet()
            all.filter { it.kind == JobKind.CATALOG_MERGE && it.catalog in catalogs && done(it) }.forEach { reset += it.id }
        }
        if (failed.kind != JobKind.PLAN) {
            all.filter { it.kind == JobKind.FINAL_MERGE && done(it) }.forEach { reset += it.id }
        }
        return reset.toList()
    }
}
