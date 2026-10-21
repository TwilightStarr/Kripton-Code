package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.run.AccountSelector.Choice
import com.kriptoncod.app.domain.AccountHealth
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.HealthEvent
import com.kriptoncod.app.domain.UsageOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountSelectorTest {
    private val t = 1_000_000_000L
    private val day = AccountSelector.DAY_MS

    private fun acc(
        id: Long,
        rank: Int = 3,
        status: AccountStatus = AccountStatus.READY,
        waitUntil: Long? = null,
        limit: Int? = null,
        backup: Boolean = false,
    ) = AiAccount(
        id = id,
        providerId = "openai",
        displayName = "a$id",
        authType = AuthType.API_KEY,
        modelId = "m$id",
        powerRank = rank,
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

    private fun choose(
        accounts: List<AiAccount>,
        health: List<AccountHealth> = emptyList(),
        tried: Set<Long> = emptySet(),
        preferred: Long? = null,
        now: Long = t,
    ) = AccountSelector.choose(accounts, health.associateBy { it.accountId }, tried, preferred, now)

    private fun usedId(c: Choice): Long = (c as Choice.Use).account.id

    @Test fun picksStrongestWithoutHistory() {
        assertEquals(2L, usedId(choose(listOf(acc(1, rank = 1), acc(2, rank = 5), acc(3, rank = 3)))))
    }

    @Test fun equalRankRotatesToLeastRecentlyUsed() {
        val a = acc(1)
        val b = acc(2)
        val h = listOf(
            health(1, recent = listOf(t - 10 to UsageOutcome.SUCCESS)),
            health(2, recent = listOf(t - 500 to UsageOutcome.SUCCESS)),
        )
        assertEquals(2L, usedId(choose(listOf(a, b), h))) // 2 daha uzun süredir kullanılmıyor
        val neverUsed = acc(3)
        assertEquals(3L, usedId(choose(listOf(a, b, neverUsed), h))) // hiç kullanılmayan en başta
    }

    @Test fun strongerRankBeatsRotation() {
        val h = listOf(health(1, recent = listOf(t - 1 to UsageOutcome.SUCCESS)))
        assertEquals(1L, usedId(choose(listOf(acc(1, rank = 5), acc(2, rank = 1)), h)))
    }

    @Test fun backupOnlyWhenPrimariesUnavailable() {
        val primary = acc(1, rank = 1)
        val backup = acc(2, rank = 9, backup = true)
        assertEquals(1L, usedId(choose(listOf(primary, backup))))
        assertEquals(1L, usedId(choose(listOf(primary, backup), tried = setOf(2L))))
        // Birincil denendi ya da kullanılamıyor → yedek devreye girer.
        assertEquals(2L, usedId(choose(listOf(primary, backup), tried = setOf(1L))))
        val limited = primary.copy(status = AccountStatus.WAITING, waitUntil = t + 5_000)
        assertEquals(2L, usedId(choose(listOf(limited, backup))))
    }

    @Test fun unhealthyAccountIsDemotedBelowHealthyEvenIfStronger() {
        val flaky = health(
            1,
            recent = listOf(
                t - 1 to UsageOutcome.FAILED,
                t - 2 to UsageOutcome.FAILED,
                t - 3 to UsageOutcome.SUCCESS,
            ),
        )
        assertEquals(2L, usedId(choose(listOf(acc(1, rank = 9), acc(2, rank = 1)), listOf(flaky))))
        // Sağlıksız olsa da tek kalan hesap kullanılır (iş boş yere beklemez).
        assertEquals(1L, usedId(choose(listOf(acc(1, rank = 9)), listOf(flaky))))
    }

    @Test fun healthScoreIgnoresRateLimits() {
        val h = health(1, recent = listOf(t to UsageOutcome.RATE_LIMITED, t - 1 to UsageOutcome.RATE_LIMITED))
        assertEquals(1.0, AccountSelector.healthScore(h, t), 0.0)
        val mixed = health(1, recent = listOf(t to UsageOutcome.SUCCESS, t - 1 to UsageOutcome.FAILED))
        assertEquals(0.5, AccountSelector.healthScore(mixed, t), 0.0)
        assertEquals(1.0, AccountSelector.healthScore(null, t), 0.0)
    }

    @Test fun oldFailuresAreForgottenSoStrongAccountComesBack() {
        val old = t - AccountSelector.HEALTH_WINDOW_MS - 1
        val past = health(
            1,
            recent = listOf(old to UsageOutcome.FAILED, old - 1 to UsageOutcome.FAILED, old - 2 to UsageOutcome.FAILED),
        )
        assertEquals(1.0, AccountSelector.healthScore(past, t), 0.0)
        assertEquals(1L, usedId(choose(listOf(acc(1, rank = 9), acc(2, rank = 1)), listOf(past))))
    }

    @Test fun dailyQuotaRemovesAccountFromPoolUntilWindowFrees() {
        val limited = acc(1, rank = 5, limit = 2)
        val other = acc(2, rank = 1)
        val used = health(1, requests = listOf(t - 3_000, t - 2_000))
        assertEquals(2L, usedId(choose(listOf(limited, other), listOf(used))))

        // Yalnız sınırlı hesap varsa iş, en eski isteğin 24 saati dolunca açılır.
        val wait = choose(listOf(limited), listOf(used)) as Choice.WaitUntil
        assertEquals(t - 3_000 + day, wait.untilMs)

        // Pencere geçince hesap yeniden havuzda.
        assertEquals(1L, usedId(choose(listOf(limited), listOf(used), now = t - 3_000 + day + 1)))
    }

    @Test fun quotaCountsOnlyRequestsInsideWindow() {
        val limited = acc(1, limit = 2)
        val h = health(1, requests = listOf(t - day - 1, t - 5))
        assertNull(AccountSelector.quotaResetAt(limited, h, t)) // biri pencere dışında → 1 < 2
    }

    @Test fun nonPositiveLimitMeansUnlimited() {
        val h = health(1, requests = List(50) { t - it })
        assertNull(AccountSelector.quotaResetAt(acc(1, limit = 0), h, t))
        assertNull(AccountSelector.quotaResetAt(acc(1, limit = -3), h, t))
        assertNull(AccountSelector.quotaResetAt(acc(1, limit = null), h, t))
    }

    @Test fun breakerOpensAfterThreeInfrastructureFailuresAndReopensAfterCooldown() {
        val a = acc(1, rank = 5)
        val b = acc(2, rank = 1)
        val failing = health(
            1,
            recent = listOf(
                t - 100 to UsageOutcome.TRANSIENT,
                t - 200 to UsageOutcome.UNAUTHORIZED,
                t - 300 to UsageOutcome.TRANSIENT,
            ),
        )
        assertEquals(2L, usedId(choose(listOf(a, b), listOf(failing))))
        val reopenAt = t - 100 + AccountSelector.BREAKER_COOLDOWN_MS
        assertEquals(Choice.WaitUntil(reopenAt), choose(listOf(a), listOf(failing)))
        // Dinlenme bitince hesap yeniden açılır (başka seçenek yoksa denenir).
        assertEquals(1L, usedId(choose(listOf(a), listOf(failing), now = reopenAt + 1)))
        // Arıza hâlâ tazeyse sağlam hesap yine öndedir.
        assertEquals(2L, usedId(choose(listOf(a, b), listOf(failing), now = reopenAt + 1)))
    }

    @Test fun contentFailuresAndRateLimitsDoNotTripBreaker() {
        val failed = health(1, recent = listOf(t - 1 to UsageOutcome.FAILED, t - 2 to UsageOutcome.FAILED, t - 3 to UsageOutcome.FAILED))
        assertNull(AccountSelector.breakerReopenAt(failed, t)) // istek içeriği sorunu hesabı suçlamaz
        val limited = health(1, recent = listOf(t - 1 to UsageOutcome.RATE_LIMITED, t - 2 to UsageOutcome.TRANSIENT, t - 3 to UsageOutcome.TRANSIENT))
        assertNull(AccountSelector.breakerReopenAt(limited, t))
        val two = health(1, recent = listOf(t - 1 to UsageOutcome.TRANSIENT, t - 2 to UsageOutcome.TRANSIENT))
        assertNull(AccountSelector.breakerReopenAt(two, t))
    }

    @Test fun successBreaksTheFailureStreak() {
        val h = health(
            1,
            recent = listOf(t - 1 to UsageOutcome.SUCCESS, t - 2 to UsageOutcome.TRANSIENT, t - 3 to UsageOutcome.TRANSIENT, t - 4 to UsageOutcome.TRANSIENT),
        )
        assertNull(AccountSelector.breakerReopenAt(h, t))
    }

    @Test fun preferredAccountWinsOnlyWhenInPool() {
        val strong = acc(1, rank = 9)
        val chosen = acc(2, rank = 1)
        assertEquals(2L, usedId(choose(listOf(strong, chosen), preferred = 2L)))
        val waiting = chosen.copy(status = AccountStatus.WAITING, waitUntil = t + 1_000)
        assertEquals(1L, usedId(choose(listOf(strong, waiting), preferred = 2L)))
    }

    @Test fun errorAndTriedAccountsAreNeverChosen() {
        val err = acc(1, rank = 9, status = AccountStatus.ERROR)
        assertEquals(Choice.None, choose(listOf(err)))
        assertEquals(2L, usedId(choose(listOf(err, acc(2)))))
        assertEquals(Choice.None, choose(listOf(acc(2)), tried = setOf(2L)))
        assertEquals(Choice.None, choose(emptyList()))
    }

    @Test fun waitsUntilEarliestOfAllBlockers() {
        val waiting = acc(1, status = AccountStatus.WAITING, waitUntil = t + 30_000)
        val limited = acc(2, limit = 1)
        val used = health(2, requests = listOf(t - day + 10_000)) // 10 sn sonra açılır
        assertEquals(Choice.WaitUntil(t + 10_000), choose(listOf(waiting, limited), listOf(used)))
    }

    @Test fun expiredProviderWaitIsUsableAgain() {
        val expired = acc(1, status = AccountStatus.WAITING, waitUntil = t - 1)
        assertEquals(1L, usedId(choose(listOf(expired))))
        assertEquals(true, AccountSelector.isUsable(expired, t))
        assertEquals(false, AccountSelector.isUsable(expired.copy(waitUntil = t + 1), t))
    }

    @Test fun poolBlockUntilCombinesQuotaAndBreaker() {
        val a = acc(1, limit = 1)
        val h = health(
            1,
            requests = listOf(t - 10),
            recent = listOf(t - 10 to UsageOutcome.TRANSIENT, t - 20 to UsageOutcome.TRANSIENT, t - 30 to UsageOutcome.TRANSIENT),
        )
        val quota = t - 10 + day
        val breaker = t - 10 + AccountSelector.BREAKER_COOLDOWN_MS
        assertEquals(maxOf(quota, breaker), AccountSelector.poolBlockUntil(a, h, t))
        assertNull(AccountSelector.poolBlockUntil(acc(1), null, t))
    }
}
