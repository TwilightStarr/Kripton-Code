package com.kriptoncod.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp
import com.kriptoncod.app.data.serial.SerialStatus
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay

/**
 * Seri çalışmayı arka planda canlı tutan ön plan işçisi. Asıl işi [com.kriptoncod.app.data.serial.SerialExecutor]
 * yürütür; bu işçi yalnız ön plan servisi bildirimini tutar ve bitene kadar bekler (süreç ölürse yeniden başlatır).
 */
class SerialWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo = RunNotifier.foregroundInfo(applicationContext, "Seri mod çalışıyor", runId = null)

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id < 0) return Result.failure()
        val container = (applicationContext as KriptonCodApp).container
        val store = container.serialStore
        var foreground = trySetForeground("Seri mod çalışıyor", 0, 0)
        container.serialExecutor.ensure(id)
        try {
            while (true) {
                val s = store.get(id) ?: break
                if (s.status != SerialStatus.RUNNING) break
                container.serialExecutor.ensure(id)
                if (foreground) {
                    foreground = trySetForeground("Seri ${s.index + 1}/${s.total}: ${s.message.ifBlank { s.current?.title.orEmpty() }}", s.index, s.total)
                }
                delay(POLL_MS)
            }
        } catch (e: CancellationException) {
            throw e
        }
        return Result.success()
    }

    private suspend fun trySetForeground(text: String, done: Int, total: Int): Boolean = try {
        setForeground(RunNotifier.foregroundInfo(applicationContext, text, done, total, runId = null))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    companion object {
        const val KEY_ID = "serial_id"
        private const val POLL_MS = 5_000L
    }
}
