package com.kriptoncod.app.ui.usage

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.UsageRepository
import com.kriptoncod.app.data.run.PoolStatus
import com.kriptoncod.app.data.run.UsageReport
import com.kriptoncod.app.data.ai.TokenUsage
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.UsageOutcome
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.awaitValue
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class UsageViewModelTest {
    private lateinit var db: KriptonDatabase
    private lateinit var accounts: AiAccountRepository
    private lateinit var usage: UsageRepository

    @Before fun setUp() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = inMemoryDb()
        accounts = AiAccountRepository(db.aiAccountDao(), FakeKeyVault())
        usage = UsageRepository(db.usageEventDao())
    }

    @After fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun addAccount(limit: Int? = null, pin: Double? = null): Long = runBlocking {
        accounts.add(
            AiAccount(
                providerId = "openai",
                displayName = "hesap",
                authType = AuthType.API_KEY,
                modelId = "m",
                powerRank = 1,
                dailyLimit = limit,
                priceInPerM = pin,
            ),
            "sk",
        )
    }

    @Test fun reportCombinesTokensCostAndQuota() {
        val id = addAccount(limit = 2, pin = 10.0)
        runBlocking {
            usage.record(id, null, UsageOutcome.SUCCESS, 5, tokens = TokenUsage(1_000_000, 200))
            usage.record(id, null, UsageOutcome.SUCCESS, 5, tokens = TokenUsage(500, 100), tokensEstimated = true)
        }
        val s = UsageViewModel(accounts, usage).state.awaitValue { it.report != null && it.pool[id] != null }
        val row = s.report!!.rows.single()
        assertEquals(1_000_500L, row.promptTokens)
        assertEquals(300L, row.completionTokens)
        assertEquals(1, row.estimatedRequests)
        assertEquals(10.005, row.cost!!, 1e-9)
        assertEquals(PoolStatus.State.RESTING_QUOTA, s.pool.getValue(id).state) // 2/2 doldu
        assertEquals(UsageReport.Window.DAY, s.window)
    }

    @Test fun switchingWindowRebuildsTheReport() {
        val id = addAccount()
        runBlocking { usage.record(id, null, UsageOutcome.SUCCESS, 5, tokens = TokenUsage(10, 10)) }
        val vm = UsageViewModel(accounts, usage)
        vm.state.awaitValue { it.report != null && it.report.requests == 1 }
        vm.selectWindow(UsageReport.Window.MONTH)
        val s = vm.state.awaitValue { it.window == UsageReport.Window.MONTH && it.report?.window == UsageReport.Window.MONTH }
        assertEquals(1, s.report!!.requests)
        assertEquals(false, s.report!!.hourly)
    }
}
