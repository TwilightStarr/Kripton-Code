package com.kriptoncod.app.data.repo

import com.kriptoncod.app.data.db.AccountUsage
import com.kriptoncod.app.data.db.UsageEvent
import com.kriptoncod.app.data.db.UsageEventDao
import com.kriptoncod.app.domain.UsageOutcome
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow

/** Hesap başına istek sonuçlarını kaydeder ve özetler. Kayıt hatası üretimi asla bozmaz. */
class UsageRepository(
    private val dao: UsageEventDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun record(
        accountId: Long,
        jobId: Long?,
        outcome: UsageOutcome,
        durationMs: Long,
        waitMs: Long? = null,
    ) {
        try {
            val t = now()
            dao.insert(UsageEvent(accountId = accountId, jobId = jobId, at = t, outcome = outcome, durationMs = durationMs, waitMs = waitMs))
            dao.prune(t - RETENTION_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // kullanım kaydı yardımcıdır; DB hatası işi düşürmemeli
        }
    }

    /** Son [windowMs] içindeki hesap başına özet (varsayılan 24 saat). Pencere abonelik anında sabitlenir. */
    fun observeSummary(windowMs: Long = DAY_MS): Flow<List<AccountUsage>> = dao.observeSummary(now() - windowMs)

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val RETENTION_MS = 30L * DAY_MS
    }
}
