package com.kriptoncod.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp
import kotlinx.coroutines.flow.first

/** 15 dakikada bir çalışır (WorkManager'ın en kısa periyodu); zinciri kopmuş run'ları yeniden başlatır. */
class RunWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as KriptonCodApp).container
        runCatching { c.runWatchdogSweep() }
        return Result.success()
    }
}
