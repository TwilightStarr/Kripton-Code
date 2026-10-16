package com.kriptoncod.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.kriptoncod.app.domain.AuthType
import kotlinx.coroutines.flow.first
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

        // Web oturumu (WebView) işleri arka planda kısılmasın: hesaplardan biri web oturumuysa ön plana al.
        if (runCatching { container.aiAccountRepository.observeAll().first().any { it.enabled && it.authType == AuthType.WEB_SESSION } }.getOrDefault(false)) {
            runCatching { setForeground(foregroundInfo()) }
        }

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

    private fun foregroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Üretim", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Krypton Code")
            .setContentText("Web hesaplarıyla üretim sürüyor…")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "krypton_run"
        private const val NOTIFICATION_ID = 4711
        const val KEY_RUN_ID = "run_id"
        private const val MAX_WORKER_ATTEMPTS = 3
    }
}
