package com.kriptoncod.app.work

import android.content.Context
import kotlinx.coroutines.flow.first
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.R
import com.kriptoncod.app.widget.RunWidgetUpdater
import com.kriptoncod.app.data.run.DispatchOutcome
import com.kriptoncod.app.domain.RunStatus
import kotlin.coroutines.cancellation.CancellationException

/**
 * Bir run'ın işlerini dağıtır; uygulama kapalıyken de ön plan servisi olarak sürer ve ilerleme bildirimi gösterir.
 * Ayrıntılı kurallar için [com.kriptoncod.app.data.run.PromptDispatcher].
 */
class PromptDispatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runId = inputData.getLong(KEY_RUN_ID, -1L)
        if (runId < 0) return Result.failure()
        val container = (applicationContext as KriptonCodApp).container
        val notify = runCatching { container.themeStore.notifyFlow.first() }.getOrDefault(true)

        // Arka planda kısılmamak için HER çalışma ön plana alınır (web oturumu WebView'ı da buna bağlıdır).
        val ctx = applicationContext
        runCatching {
            setForeground(RunNotifier.foregroundInfo(ctx, ctx.getString(R.string.notif_running), runId = runId))
        }
        RunNotifier.clearPaused(ctx, runId)
        RunWidgetUpdater.refresh(ctx)

        return try {
            val outcome = container.promptDispatcher.process(runId) { done, total ->
                runCatching {
                    setForeground(
                        RunNotifier.foregroundInfo(ctx, ctx.getString(R.string.notif_progress, done, total), done, total, runId),
                    )
                }
                RunWidgetUpdater.refresh(ctx)
            }
            when (outcome) {
                is DispatchOutcome.Wait -> {
                    container.runScheduler.resume(runId, outcome.delayMs)
                    if (notify && outcome.delayMs >= LONG_WAIT_MS) {
                        RunNotifier.event(
                            ctx,
                            runId,
                            RunNotifier.KIND_WAITING,
                            ctx.getString(R.string.notif_waiting_title),
                            ctx.getString(R.string.notif_waiting_text),
                        )
                    }
                }

                is DispatchOutcome.Finished -> if (notify) {
                    if (outcome.status == RunStatus.DONE) {
                        RunNotifier.event(
                            ctx,
                            runId,
                            RunNotifier.KIND_DONE,
                            ctx.getString(R.string.notif_done_title),
                            ctx.getString(R.string.notif_done_text, runId),
                        )
                    } else if (outcome.status == RunStatus.FAILED) {
                        RunNotifier.event(
                            ctx,
                            runId,
                            RunNotifier.KIND_FAILED,
                            ctx.getString(R.string.notif_failed_title),
                            ctx.getString(R.string.notif_failed_text, runId),
                        )
                    }
                }

                is DispatchOutcome.Cancelled -> Unit
            }
            RunWidgetUpdater.refresh(ctx)
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
                if (notify) {
                    RunNotifier.event(
                        ctx,
                        runId,
                        RunNotifier.KIND_FAILED,
                        ctx.getString(R.string.notif_failed_title),
                        ctx.getString(R.string.notif_crashed_text, runId),
                    )
                }
                Result.failure()
            }
        }
    }

    companion object {
        const val KEY_RUN_ID = "run_id"
        private const val MAX_WORKER_ATTEMPTS = 3
        private const val LONG_WAIT_MS = 5L * 60 * 1000
    }
}
