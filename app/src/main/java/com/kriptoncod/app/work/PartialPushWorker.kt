package com.kriptoncod.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp
import kotlin.coroutines.cancellation.CancellationException

/** Kullanıcının onayladığı kısmi sonucu GitHub'a gönderir ve CI izlemesini başlatır. */
class PartialPushWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val runId = inputData.getLong(KEY_RUN_ID, -1L)
        if (runId < 0) return Result.failure()
        val container = (applicationContext as KriptonCodApp).container
        return try {
            container.runCompleter.approvePush(runId)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Gönderim sırasında durum PUSHING'de kalmış olabilir; kullanıcı yeniden onaylayabilsin.
            runCatching {
                if (container.runRepository.getRun(runId)?.status == com.kriptoncod.app.domain.RunStatus.PUSHING) {
                    container.runRepository.setRunStatus(runId, com.kriptoncod.app.domain.RunStatus.PARTIAL)
                }
            }
            Result.failure()
        }
    }

    companion object {
        const val KEY_RUN_ID = "run_id"
    }
}
