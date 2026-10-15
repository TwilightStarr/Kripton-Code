package com.kriptoncod.app.work

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.domain.RunStatus
import java.util.concurrent.TimeUnit

/** Run başına tek bir WorkManager zinciri yönetir (aynı run iki worker'da çalışmaz). */
class RunScheduler(
    private val workManager: WorkManager,
    private val runs: RunRepository,
) {
    /** Zaten bekleyen/çalışan zincir varsa dokunmaz (KEEP). */
    fun start(runId: Long) {
        workManager.enqueueUniqueWork(WorkNames.dispatch(runId), ExistingWorkPolicy.KEEP, request(runId, 0))
    }

    /** Çalışan worker'ın ardına, [delayMs] sonra çalışacak devam işi ekler. */
    fun resume(runId: Long, delayMs: Long) {
        workManager.enqueueUniqueWork(
            WorkNames.dispatch(runId),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(runId, delayMs),
        )
    }

    suspend fun cancel(runId: Long) {
        runs.setRunStatus(runId, RunStatus.CANCELLED)
        workManager.cancelUniqueWork(WorkNames.dispatch(runId))
    }

    private fun request(runId: Long, delayMs: Long) =
        OneTimeWorkRequestBuilder<PromptDispatchWorker>()
            .setInputData(workDataOf(PromptDispatchWorker.KEY_RUN_ID to runId))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
            .build()
}
