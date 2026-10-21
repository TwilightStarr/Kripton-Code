package com.kriptoncod.app.data.repo

import com.kriptoncod.app.data.ai.TokenUsage
import com.kriptoncod.app.data.db.AccountUsage
import com.kriptoncod.app.data.db.UsageEvent
import com.kriptoncod.app.data.db.UsageEventDao
import com.kriptoncod.app.domain.AccountHealth
import com.kriptoncod.app.domain.HealthEvent
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
        tokens: TokenUsage? = null,
        tokensEstimated: Boolean = false,
    ) {
        try {
            val t = now()
            dao.insert(
                UsageEvent(
                    accountId = accountId,
                    jobId = jobId,
                    at = t,
                    outcome = outcome,
                    durationMs = durationMs,
                    waitMs = waitMs,
                    promptTokens = tokens?.prompt,
                    completionTokens = tokens?.completion,
                    tokensEstimated = tokens != null && tokensEstimated,
                ),
            )
            dao.prune(t - RETENTION_MS)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // kullanım kaydı yardımcıdır; DB hatası işi düşürmemeli
        }
    }

    /** Son [windowMs] içindeki hesap başına özet (varsayılan 24 saat). Pencere abonelik anında sabitlenir. */
    fun observeSummary(windowMs: Long = DAY_MS): Flow<List<AccountUsage>> = dao.observeSummary(now() - windowMs)

    /** Kullanım paneli için son [windowMs] içindeki ham olaylar (eskiden yeniye). */
    fun observeEvents(windowMs: Long): Flow<List<UsageEvent>> = dao.observeSince(now() - windowMs)

    /**
     * Hesap seçimi için son 24 saatin sağlık özeti (hesap kimliğine göre). Okuma hatası seçimi durdurmaz:
     * boş harita döner ve seçici yalnız güce göre davranır.
     *
     * Kotaya yalnız sağlayıcıya gerçekten ulaşıp işlenen istekler sayılır: 429 (reddedildi) ve 401/403
     * (yetkisiz) sayılmaz.
     */
    suspend fun healthSnapshot(): Map<Long, AccountHealth> = try {
        val events = dao.since(now() - DAY_MS)
        events.groupBy { it.accountId }.mapValues { (id, list) ->
            AccountHealth(
                accountId = id,
                requestTimes = list
                    .filter { it.outcome != UsageOutcome.RATE_LIMITED && it.outcome != UsageOutcome.UNAUTHORIZED }
                    .map { it.at },
                recent = list.takeLast(AccountHealth.RECENT_WINDOW).asReversed().map { HealthEvent(it.at, it.outcome) },
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyMap()
    }

    companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
        const val RETENTION_MS = 30L * DAY_MS
    }
}
