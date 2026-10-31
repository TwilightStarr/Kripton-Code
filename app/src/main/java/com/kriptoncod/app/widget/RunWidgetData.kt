package com.kriptoncod.app.widget

import com.kriptoncod.app.data.run.RunProgress
import com.kriptoncod.app.AppContainer
import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.db.RunRecord
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.work.RunControl
import kotlinx.coroutines.flow.first

/** Widget'ın gösterdiği run özeti. [nodeTitle] şu an üretilen düğümdür (yoksa null). */
data class WidgetRun(
    val id: Long,
    val status: RunStatus,
    val done: Int,
    val total: Int,
    val nodeTitle: String?,
    val nodeKind: JobKind?,
    /** Yalnız plan düğümü var: toplam henüz bilinmiyor, çubuk 0'da kalır (eskiden 1/1 = %100 görünürdü). */
    val planning: Boolean = false,
) {
    val progress: Float get() = if (planning || total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val canPause: Boolean get() = RunControl.isApplicable(RunControl.Action.PAUSE, status)
    val canResume: Boolean get() = RunControl.isApplicable(RunControl.Action.RESUME, status)
    val canCancel: Boolean get() = RunControl.isApplicable(RunControl.Action.CANCEL, status)
}

object RunWidgetData {

    /** En yeni etkin (sürüyor/duraklatılmış) run; yoksa null. [runs] en yeniden eskiye sıralıdır. */
    fun pickActive(runs: List<RunRecord>): RunRecord? = runs.firstOrNull { it.status in RunControl.ACTIVE }

    fun summarize(run: RunRecord, jobs: List<Job>): WidgetRun {
        val current = jobs.firstOrNull { it.state == JobState.RUNNING }
        val (done, total) = RunProgress.counts(jobs).let { (d, t) -> if (t == 0 && jobs.none { it.kind == JobKind.PLAN }) jobs.count { j -> j.state == JobState.DONE } to jobs.size else d to t }
        return WidgetRun(
            id = run.id,
            status = run.status,
            done = done,
            total = total,
            nodeTitle = current?.title,
            nodeKind = current?.kind,
            planning = jobs.isNotEmpty() && jobs.all { it.kind == JobKind.PLAN },
        )
    }

    suspend fun load(container: AppContainer): WidgetRun? {
        val run = pickActive(container.runRepository.observeRuns().first()) ?: return null
        return summarize(run, container.runRepository.getJobs(run.id))
    }
}
