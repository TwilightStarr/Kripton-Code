package com.kriptoncod.app.widget

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
) {
    val progress: Float get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    val canPause: Boolean get() = RunControl.isApplicable(RunControl.Action.PAUSE, status)
    val canResume: Boolean get() = RunControl.isApplicable(RunControl.Action.RESUME, status)
    val canCancel: Boolean get() = RunControl.isApplicable(RunControl.Action.CANCEL, status)
}

object RunWidgetData {

    /** En yeni etkin (sürüyor/duraklatılmış) run; yoksa null. [runs] en yeniden eskiye sıralıdır. */
    fun pickActive(runs: List<RunRecord>): RunRecord? = runs.firstOrNull { it.status in RunControl.ACTIVE }

    fun summarize(run: RunRecord, jobs: List<Job>): WidgetRun {
        val current = jobs.firstOrNull { it.state == JobState.RUNNING }
        return WidgetRun(
            id = run.id,
            status = run.status,
            done = jobs.count { it.state == JobState.DONE },
            total = jobs.size,
            nodeTitle = current?.title,
            nodeKind = current?.kind,
        )
    }

    suspend fun load(container: AppContainer): WidgetRun? {
        val run = pickActive(container.runRepository.observeRuns().first()) ?: return null
        return summarize(run, container.runRepository.getJobs(run.id))
    }
}
