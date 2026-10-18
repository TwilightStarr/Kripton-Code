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

    /** CI döngüsünü başlatır: workflow bitene kadar yoklar, hata varsa düzeltip yeniden push'lar. */
    fun startCiLoop(runId: Long) {
        val request = OneTimeWorkRequestBuilder<CiLoopWorker>()
            .setInputData(workDataOf(CiLoopWorker.KEY_RUN_ID to runId))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(WorkNames.ciLoop(runId), ExistingWorkPolicy.REPLACE, request)
    }

    /**
     * Başarısız tek bir düğümü yeniden dener (isteğe bağlı başka hesapla); yalnız o düğüm ve çıktısına dayanan
     * birleştirmeler yeniden çalışır, tamamlanmış diğer düğümler korunur.
     * @return false: düğüm/run bu işlem için uygun değil (hiçbir şey değişmedi).
     */
    suspend fun retryNode(jobId: Long, accountId: Long? = null): Boolean {
        val runId = runs.prepareRetry(jobId, accountId) ?: return false
        workManager.cancelUniqueWork(WorkNames.ciLoop(runId)) // eski CI izlemesi yeni pakete ait değildir
        start(runId)
        return true
    }

    /** Duraklatır: devam eden iş yarım kalırsa devamda baştan yapılır; tamamlanan çıktılar korunur. */
    suspend fun pause(runId: Long) {
        runs.setRunStatus(runId, RunStatus.PAUSED)
        workManager.cancelUniqueWork(WorkNames.dispatch(runId))
    }

    /** Duraklatılmış run'ı kaldığı yerden sürdürür. */
    suspend fun unpause(runId: Long) {
        runs.setRunStatus(runId, RunStatus.RUNNING)
        start(runId)
    }

    suspend fun cancel(runId: Long) {
        runs.setRunStatus(runId, RunStatus.CANCELLED)
        workManager.cancelUniqueWork(WorkNames.dispatch(runId))
        workManager.cancelUniqueWork(WorkNames.ciLoop(runId))
    }

    private fun request(runId: Long, delayMs: Long) = OneTimeWorkRequestBuilder<PromptDispatchWorker>()
        .setInputData(workDataOf(PromptDispatchWorker.KEY_RUN_ID to runId))
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        .build()
}
