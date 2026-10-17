package com.kriptoncod.app.data.db

import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.UsageOutcome
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UsageDaoTest {
    private lateinit var db: KriptonDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private suspend fun account(name: String) = db.aiAccountDao().insert(
        AiAccount(providerId = "openai", displayName = name, authType = AuthType.API_KEY, modelId = "m", powerRank = 1),
    )

    private fun ev(acc: Long, at: Long, o: UsageOutcome, wait: Long? = null) =
        UsageEvent(accountId = acc, at = at, outcome = o, waitMs = wait)

    @Test fun summaryCountsPerAccountWithinWindow() = runBlocking {
        val a = account("A"); val b = account("B")
        val dao = db.usageEventDao()
        dao.insert(ev(a, 100, UsageOutcome.SUCCESS))
        dao.insert(ev(a, 200, UsageOutcome.SUCCESS))
        dao.insert(ev(a, 300, UsageOutcome.RATE_LIMITED, wait = 5_000))
        dao.insert(ev(a, 400, UsageOutcome.TRANSIENT))
        dao.insert(ev(b, 50, UsageOutcome.SUCCESS)) // pencere dışı

        val rows = dao.observeSummary(since = 100).first().associateBy { it.accountId }
        assertEquals(setOf(a), rows.keys)
        val r = rows.getValue(a)
        assertEquals(4, r.requests)
        assertEquals(2, r.successes)
        assertEquals(1, r.rateLimited)
        assertEquals(1, r.errors)
        assertEquals(5_000L, r.totalWaitMs)
        assertEquals(400L, r.lastAt)
    }

    @Test fun pruneRemovesOldEventsOnly() = runBlocking {
        val a = account("A")
        val dao = db.usageEventDao()
        dao.insert(ev(a, 10, UsageOutcome.SUCCESS))
        dao.insert(ev(a, 1_000, UsageOutcome.SUCCESS))
        assertEquals(1, dao.prune(cutoff = 500))
        assertEquals(1, dao.observeSummary(since = 0).first().single().requests)
    }

    @Test fun deletingAccountCascadesEvents() = runBlocking {
        val a = account("A")
        db.usageEventDao().insert(ev(a, 10, UsageOutcome.SUCCESS))
        db.aiAccountDao().deleteById(a)
        assertTrue(db.usageEventDao().observeSummary(since = 0).first().isEmpty())
    }
}
