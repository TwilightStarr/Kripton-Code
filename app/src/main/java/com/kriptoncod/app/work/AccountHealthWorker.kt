package com.kriptoncod.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kriptoncod.app.KriptonCodApp

/** Altı saatte bir hesap anahtarlarını yoklar (kota harcamaz); bozulanları havuzdan çıkarır, düzelenleri geri alır. */
class AccountHealthWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as KriptonCodApp).container
        runCatching { c.accountHealthChecker.checkAll() }
        return Result.success()
    }
}
