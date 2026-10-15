package com.kriptoncod.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.data.run.DispatchOutcome
import com.kriptoncod.app.domain.RunStatus
import kotlin.coroutines.cancellation.CancellationException

/** Bir run'ın işlerini dağıtır. Ayrıntılı kurallar için [com.kriptoncod.app.data.run.PromptDispatcher]. */
class PromptDispatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runId = inputData.getLong(KEY_RUN_ID, -1L)
        if (runId < 0) return Result.failure()
        val container = (applicationContext as KriptonCodApp).container

        return try {
            when (val outcome = container.promptDispatcher.process(runId)) {
                is DispatchOutcome.Wait -> container.runScheduler.resume(runId, outcome.delayMs)
                is DispatchOutcome.Finished, is DispatchOutcome.Cancelled -> Unit
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e // WorkManager durdurdu; zincir yeniden planlanır, RUNNING işler process() başında toparlanır
        } catch (e: Exception) {
            if (runAttemptCount < MAX_WORKER_ATTEMPTS) {
                Result.retry()
            } else {
                runCatching {
                    container.runRepository.setRunStatus(runId, RunStatus.FAILED)
                    container.runRepository.saveHistory(runId)
                }
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_RUN_ID = "run_id"
        private const val MAX_WORKER_ATTEMPTS = 3
    }
}
