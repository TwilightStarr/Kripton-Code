package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.UsageEvent
import com.kriptoncod.app.data.run.UsageReport.Window
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.UsageOutcome
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageReportTest {
    private val zone = ZoneId.of("UTC")
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 100 * day + 12 * hour // UTC 12:00

    private fun acc(id: Long, pin: Double? = null, pout: Double? = null) = AiAccount(
        id = id,
        providerId = "openai",
        displayName = "a$id",
        authType = AuthType.API_KEY,
        modelId = "m$id",
        powerRank = 1,
        priceInPerM = pin,
        priceOutPerM = pout,
    )

    private fun ev(
        acc: Long,
        at: Long,
        p: Int? = null,
        c: Int? = null,
        o: UsageOutcome = UsageOutcome.SUCCESS,
        est: Boolean = false,
    ) = UsageEvent(accountId = acc, at = at, outcome = o, promptTokens = p, completionTokens = c, tokensEstimated = est)

    private fun build(events: List<UsageEvent>, accounts: List<AiAccount>, w: Window = Window.DAY) = UsageReport.build(events, accounts, w, now, zone)

    @Test fun sumsTokensPerAccountAndSortsByTokens() {
        val r = build(
            listOf(ev(1, now - hour, 100, 50), ev(1, now - 2 * hour, 10, 5), ev(2, now - hour, 1_000, 500)),
            listOf(acc(1), acc(2)),
        )
        assertEquals(listOf(2L, 1L), r.rows.map { it.accountId })
        val one = r.rows.first { it.accountId == 1L }
        assertEquals(110, one.promptTokens)
        assertEquals(55, one.completionTokens)
        assertEquals(2, one.requests)
        assertEquals(1_665, r.totalTokens)
        assertEquals(3, r.requests)
    }

    @Test fun eventsOutsideWindowAreIgnored() {
        val r = build(listOf(ev(1, now - day - 1, 999, 999), ev(1, now - hour, 1, 1)), listOf(acc(1)))
        assertEquals(2, r.totalTokens)
        assertEquals(1, r.requests)
    }

    @Test fun idleAccountsStillAppearAndDeletedAccountsKeepTheirRows() {
        val r = build(listOf(ev(99, now - hour, 5, 5)), listOf(acc(1)))
        assertEquals(setOf(1L, 99L), r.rows.map { it.accountId }.toSet())
        assertNull(r.rows.first { it.accountId == 99L }.account)
        assertEquals(0, r.rows.first { it.accountId == 1L }.requests)
    }

    @Test fun costNeedsAPriceAndTreatsMissingSideAsFree() {
        val events = listOf(ev(1, now - hour, 2_000_000, 1_000_000), ev(2, now - hour, 5_000_000, 5_000_000))
        val r = build(events, listOf(acc(1, pin = 1.0, pout = 3.0), acc(2)))
        assertEquals(5.0, r.rows.first { it.accountId == 1L }.cost!!, 1e-9)
        assertNull(r.rows.first { it.accountId == 2L }.cost)
        assertEquals(5.0, r.cost!!, 1e-9)
        assertEquals(1, r.unpricedAccounts)
        assertEquals(3.0, UsageReport.costOf(acc(3, pout = 3.0), 9_000_000, 1_000_000)!!, 1e-9)
    }

    @Test fun noPricesAnywhereMeansNoCostAtAll() {
        val r = build(listOf(ev(1, now - hour, 10, 10)), listOf(acc(1)))
        assertNull(r.cost)
    }

    @Test fun estimatedAndUnknownRequestsAreCountedSeparately() {
        val r = build(
            listOf(
                ev(1, now - 3 * hour, 10, 10),
                ev(1, now - 2 * hour, 20, 20, est = true),
                ev(1, now - hour, o = UsageOutcome.FAILED),
            ),
            listOf(acc(1)),
        )
        val row = r.rows.single()
        assertEquals(1, row.estimatedRequests)
        assertEquals(1, row.unknownRequests)
        assertEquals(2, row.successes)
        assertEquals(1, r.estimatedRequests)
    }

    @Test fun dayWindowUsesHourlyBucketsWithoutGaps() {
        val r = build(listOf(ev(1, now - 30 * 60_000, 10, 0), ev(1, now - 5 * hour, 7, 0)), listOf(acc(1)))
        assertTrue(r.hourly)
        // pencere başı (11:00 dün) .. şimdi (12:00): 25 saat sınırı, boşluksuz ve artan
        assertTrue(r.buckets.size in 24..25)
        r.buckets.zipWithNext().forEach { (a, b) -> assertEquals(hour, b.startMs - a.startMs) }
        assertEquals(17, r.buckets.sumOf { it.tokens })
        assertEquals(10, r.buckets[r.buckets.size - 2].tokens) // 11:30 olayı, 11:00 diliminde
        assertEquals(0, r.buckets.last().tokens)
    }

    @Test fun weekWindowUsesDailyBuckets() {
        val r = build(listOf(ev(1, now - 2 * day, 100, 0), ev(1, now - hour, 1, 0)), listOf(acc(1)), Window.WEEK)
        assertTrue(!r.hourly)
        assertEquals(8, r.buckets.size)
        assertEquals(101, r.buckets.sumOf { it.tokens })
        assertNotNull(r.buckets.firstOrNull { it.tokens == 100L })
    }

    @Test fun formatsTokensAndCost() {
        assertEquals("999", UsageFormat.tokens(999))
        assertEquals("1.2K", UsageFormat.tokens(1_234))
        assertEquals("12K", UsageFormat.tokens(12_345))
        assertEquals("3.5M", UsageFormat.tokens(3_456_789))
        assertEquals("2B", UsageFormat.tokens(2_000_000_000))
        assertEquals("$0.004", UsageFormat.cost(0.0042))
        assertEquals("$12.35", UsageFormat.cost(12.345))
    }
}
