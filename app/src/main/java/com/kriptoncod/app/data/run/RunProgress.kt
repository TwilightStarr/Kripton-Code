package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus

/**
 * Run ilerlemesinin TEK hesap yeri (bildirim, widget, Run ekranı, ana ekran aynı sayıyı gösterir).
 *
 * Eski hesap "biten iş / toplam iş"ti ve üç yerde yanlıştı:
 *  1. Başlangıçta yalnız PLAN düğümü vardır: plan bitince 1/1 = %100 görünür, grafik kurulunca %2'ye düşerdi.
 *  2. Birleştirme düğümleri (kodla yapılır, çoğu saniyeler sürer) iş düğümleriyle aynı ağırlıktaydı.
 *  3. GitHub'a gönderme, CI bekleme ve düzeltme hiç sayılmazdı: düğümler bitince çubuk %100'de takılı kalırdı.
 *
 * Yeni hesap: planlama %5, düğümler %80 (iş düğümü 1, birleştirme 0,25 ağırlık), gönderme/CI son %15.
 * Başarısız düğüm "bitti" sayılmaz: kısmi sonuç hiçbir zaman %100 görünmez.
 */
data class RunProgress(
    /** 0..1. Belirsizken ([indeterminate]) 0. */
    val fraction: Float,
    val stage: Stage,
    /** Grafik düğümleri (PLAN hariç). */
    val done: Int,
    val failed: Int,
    val running: Int,
    val waiting: Int,
    val pending: Int,
    val total: Int,
    /** Ölçülemeyen aşama (planlama): çubuk belirsiz gösterilmeli. */
    val indeterminate: Boolean,
    /** Tahmini kalan süre; yeterli veri yoksa null. */
    val etaMs: Long?,
) {
    enum class Stage { PLANNING, NODES, PUSHING, CI, FIXING, FINISHED, PARTIAL, FAILED, CANCELLED, PAUSED }

    val percent: Int get() = (fraction * 100f).toInt().coerceIn(0, 100)

    companion object {
        const val PLAN_SHARE = 0.05f
        const val NODES_SHARE = 0.80f
        const val PUSHING_AT = 0.88f
        const val FIXING_AT = 0.90f
        const val CI_AT = 0.92f

        private const val WORK_WEIGHT = 1.0
        private const val MERGE_WEIGHT = 0.25
        private const val MIN_DONE_FOR_ETA = 2

        private fun weight(kind: JobKind): Double = when (kind) {
            JobKind.WORK -> WORK_WEIGHT
            JobKind.CATALOG_MERGE, JobKind.FINAL_MERGE -> MERGE_WEIGHT
            JobKind.PLAN -> 0.0
        }

        /** Bildirim/widget için (biten, toplam) çifti; planlama aşamasında (0, 0) = belirsiz çubuk. */
        fun counts(jobs: List<Job>): Pair<Int, Int> {
            val graph = jobs.filter { it.kind != JobKind.PLAN }
            if (graph.isEmpty()) return 0 to 0
            return graph.count { it.state == JobState.DONE } to graph.size
        }

        fun of(status: RunStatus, jobs: List<Job>, createdAt: Long = 0L, now: Long = 0L): RunProgress {
            val plan = jobs.filter { it.kind == JobKind.PLAN }
            val graph = jobs.filter { it.kind != JobKind.PLAN }
            val planDone = plan.isNotEmpty() && plan.all { it.state == JobState.DONE }

            val done = graph.count { it.state == JobState.DONE }
            val failed = graph.count { it.state == JobState.FAILED }
            val running = graph.count { it.state == JobState.RUNNING }
            val waiting = graph.count { it.state == JobState.WAITING }
            val pending = graph.count { it.state == JobState.PENDING }

            val totalWeight = graph.sumOf { weight(it.kind) }
            val doneWeight = graph.filter { it.state == JobState.DONE }.sumOf { weight(it.kind) }
            val nodesFraction = if (totalWeight <= 0.0) 0f else (doneWeight / totalWeight).toFloat().coerceIn(0f, 1f)
            val nodesOverall = (if (planDone || graph.isNotEmpty()) PLAN_SHARE else 0f) + NODES_SHARE * nodesFraction

            val planning = graph.isEmpty() && status in ACTIVE_BUILD
            fun make(stage: Stage, fraction: Float, indeterminate: Boolean = false, eta: Long? = null) = RunProgress(
                fraction = fraction.coerceIn(0f, 1f),
                stage = stage,
                done = done,
                failed = failed,
                running = running,
                waiting = waiting,
                pending = pending,
                total = graph.size,
                indeterminate = indeterminate,
                etaMs = eta,
            )

            return when (status) {
                RunStatus.DONE -> make(Stage.FINISHED, 1f)

                RunStatus.PUSHING -> make(Stage.PUSHING, PUSHING_AT)

                RunStatus.CI_WAITING -> make(Stage.CI, CI_AT)

                RunStatus.FIXING -> make(Stage.FIXING, FIXING_AT)

                RunStatus.PARTIAL -> make(Stage.PARTIAL, nodesOverall)

                RunStatus.FAILED -> make(Stage.FAILED, nodesOverall)

                RunStatus.CANCELLED -> make(Stage.CANCELLED, nodesOverall)

                RunStatus.PAUSED -> if (planning) make(Stage.PAUSED, 0f) else make(Stage.PAUSED, nodesOverall)

                RunStatus.PREPARING, RunStatus.RUNNING ->
                    if (planning) {
                        make(Stage.PLANNING, 0f, indeterminate = true)
                    } else {
                        make(Stage.NODES, nodesOverall, eta = eta(doneWeight, totalWeight, done, createdAt, now))
                    }
            }
        }

        private val ACTIVE_BUILD = setOf(RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PAUSED)

        /** Geçen süreyi biten ağırlığa bölerek kalanı tahmin eder; erken (az veri) ya da sıfır süre için null. */
        private fun eta(doneWeight: Double, totalWeight: Double, doneCount: Int, createdAt: Long, now: Long): Long? {
            if (doneCount < MIN_DONE_FOR_ETA || doneWeight <= 0.0 || now <= createdAt) return null
            val remaining = totalWeight - doneWeight
            if (remaining <= 0.0) return 0L
            return ((now - createdAt) / doneWeight * remaining).toLong().coerceAtLeast(0L)
        }
    }
}
