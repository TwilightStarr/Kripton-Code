package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.run.PoolStatus.State
import com.kriptoncod.app.domain.AccountHealth
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.HealthEvent
import com.kriptoncod.app.domain.UsageOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolStatusTest {
    private val t = 1_000_000_000L
    private val day = AccountSelector.DAY_MS

    private fun acc(
        id: Long,
        rank: Int = 3,
        status: AccountStatus = AccountStatus.READY,
        waitUntil: Long? = null,
        limit: Int? = null,
        backup: Boolean = false,
        enabled: Boolean = true,
    ) = AiAccount(
        id = id,
        providerId = "openai",
        displayName = "a$id",
        authType = AuthType.API_KEY,
        modelId = "m$id",
        powerRank = rank,
        enabled = enabled,
        status = status,
        waitUntil = waitUntil,
        dailyLimit = limit,
        isBackup = backup,
    )

    private fun health(
        id: Long,
        requests: List<Long> = emptyList(),
        recent: List<Pair<Long, UsageOutcome>> = emptyList(),
    ) = AccountHealth(id, requests, recent.map { HealthEvent(it.first, it.second) })

    private fun resolve(accounts: List<AiAccount>, vararg h: AccountHealth) = PoolStatus.resolve(accounts, h.associateBy { it.accountId }, t)

    @Test fun healthyPrimaryIsActiveAndBackupIsStandby() {
        val r = resolve(listOf(acc(1), acc(2, backup = true)))
        assertEquals(State.ACTIVE, r.getValue(1).state)
        assertEquals(State.STANDBY, r.getValue(2).state)
        assertNull(r.getValue(1).untilMs)
    }

    @Test fun chainOrderMatchesSelectorOrderBackupsLast() {
        val accounts = listOf(acc(1, rank = 2), acc(2, rank = 9, backup = true), acc(3, rank = 5))
        val r = resolve(accounts)
        assertEquals(1, r.getValue(3).order)
        assertEquals(2, r.getValue(1).order)
        assertEquals(3, r.getValue(2).order)
        // seçici gerçekten aynı hesabı seçiyor
        val choice = AccountSelector.choose(accounts, emptyMap(), emptySet(), null, t)
        assertEquals(3L, (choice as AccountSelector.Choice.Use).account.id)
    }

    @Test fun providerWaitShowsRestingUntilItEnds() {
        val r = resolve(listOf(acc(1, status = AccountStatus.WAITING, waitUntil = t + 5_000)))
        val e = r.getValue(1)
        assertEquals(State.RESTING_PROVIDER, e.state)
        assertEquals(t + 5_000, e.untilMs)
        assertNull(e.order)
        assertFalse(e.inPool)
    }

    @Test fun expiredWaitReturnsToPool() {
        val r = resolve(listOf(acc(1, status = AccountStatus.WAITING, waitUntil = t - 1)))
        assertEquals(State.ACTIVE, r.getValue(1).state)
    }

    @Test fun fullQuotaRestsUntilOldestRequestExpires() {
        val oldest = t - day + 60_000
        val r = resolve(listOf(acc(1, limit = 2)), health(1, requests = listOf(oldest, t - 1_000)))
        val e = r.getValue(1)
        assertEquals(State.RESTING_QUOTA, e.state)
        assertEquals(oldest + day, e.untilMs)
        assertEquals(2, e.used24h)
        assertEquals(2, e.dailyLimit)
    }

    @Test fun quotaBelowLimitStaysInPoolAndReportsUsage() {
        val r = resolve(listOf(acc(1, limit = 5)), health(1, requests = listOf(t - 10, t - 20)))
        val e = r.getValue(1)
        assertEquals(State.ACTIVE, e.state)
        assertEquals(2, e.used24h)
    }

    @Test fun requestsOlderThanADayAreNotCounted() {
        val r = resolve(listOf(acc(1, limit = 5)), health(1, requests = listOf(t - day - 1, t - 5)))
        assertEquals(1, r.getValue(1).used24h)
    }

    @Test fun threeTransientFailuresOpenBreaker() {
        val recent = listOf(
            t - 1_000 to UsageOutcome.TRANSIENT,
            t - 2_000 to UsageOutcome.TRANSIENT,
            t - 3_000 to UsageOutcome.TRANSIENT,
        )
        val e = resolve(listOf(acc(1)), health(1, recent = recent)).getValue(1)
        assertEquals(State.RESTING_BREAKER, e.state)
        assertEquals(t - 1_000 + AccountSelector.BREAKER_COOLDOWN_MS, e.untilMs)
    }

    @Test fun laterReopenTimeWinsWhenSeveralBlocksApply() {
        val recent = listOf(
            t - 1_000 to UsageOutcome.TRANSIENT,
            t - 2_000 to UsageOutcome.TRANSIENT,
            t - 3_000 to UsageOutcome.TRANSIENT,
        )
        val quotaEnd = t + 3_600_000
        val account = acc(1, limit = 1)
        val h = health(1, requests = listOf(quotaEnd - day), recent = recent)
        val e = resolve(listOf(account), h).getValue(1)
        assertEquals(State.RESTING_QUOTA, e.state)
        assertEquals(quotaEnd, e.untilMs)
    }

    @Test fun errorAndDisabledAreOutOfPool() {
        val r = resolve(listOf(acc(1, status = AccountStatus.ERROR), acc(2, enabled = false)))
        assertEquals(State.ERROR, r.getValue(1).state)
        assertEquals(State.DISABLED, r.getValue(2).state)
        assertNull(r.getValue(1).order)
        assertNull(r.getValue(2).order)
    }

    @Test fun lowSuccessRateMarksUnhealthyAndPushesToTheBack() {
        val recent = listOf(
            t - 100 to UsageOutcome.FAILED,
            t - 200 to UsageOutcome.FAILED,
            t - 300 to UsageOutcome.SUCCESS,
        )
        val r = resolve(listOf(acc(1, rank = 9), acc(2, rank = 1)), health(1, recent = recent))
        assertTrue(r.getValue(1).unhealthy)
        assertFalse(r.getValue(2).unhealthy)
        assertEquals(1, r.getValue(2).order)
        assertEquals(2, r.getValue(1).order)
    }

    @Test fun summaryCountsEachGroup() {
        val accounts = listOf(
            acc(1),
            acc(2, backup = true),
            acc(3, status = AccountStatus.WAITING, waitUntil = t + 1_000),
            acc(4, status = AccountStatus.ERROR),
            acc(5, enabled = false),
        )
        val s = PoolStatus.summarize(resolve(accounts).values)
        assertEquals(PoolStatus.Summary(active = 1, standby = 1, resting = 1, error = 1), s)
    }
}
