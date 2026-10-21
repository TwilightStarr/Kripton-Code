package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.ai.AiGenerator
import com.kriptoncod.app.data.ai.TokenMeter
import com.kriptoncod.app.data.ai.TokenUsage
import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.files.HistoryStore
import com.kriptoncod.app.data.files.OutputStore
import com.kriptoncod.app.data.providers.ProviderEndpoint
import com.kriptoncod.app.data.repo.AiAccountRepository
import com.kriptoncod.app.data.repo.JobSeed
import com.kriptoncod.app.data.repo.PromptRepository
import com.kriptoncod.app.data.repo.RunRepository
import com.kriptoncod.app.data.repo.UsageRepository
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.UsageOutcome
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.inMemoryDb
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Dağıtıcı her denemede bir TokenMeter kurar; gerçek kullanım varsa o, yoksa (başarıda) tahmin kaydedilir. */
@RunWith(RobolectricTestRunner::class)
class TokenRecordingTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: KriptonDatabase
    private lateinit var accounts: AiAccountRepository
    private lateinit var prompts: PromptRepository
    private lateinit var runs: RunRepository
    private lateinit var usage: UsageRepository
    private var clock = 1_000L

    private class Gen(val body: suspend () -> String) : AiGenerator {
        override suspend fun generate(endpoint: ProviderEndpoint, modelId: String, credential: String, prompt: String): String = body()
    }

    private val credentials = object : CredentialProvider {
        override suspend fun credentialFor(account: AiAccount): String = "key"
    }

    @Before fun setUp() {
        db = inMemoryDb()
        accounts = AiAccountRepository(db.aiAccountDao(), FakeKeyVault()) { clock }
        prompts = PromptRepository(db)
        runs = RunRepository(db, OutputStore(tmp.root), HistoryStore(tmp.root)) { clock }
        usage = UsageRepository(db.usageEventDao()) { clock }
    }

    @After fun tearDown() {
        db.close()
    }

    private suspend fun runWith(gen: AiGenerator) {
        accounts.add(AiAccount(providerId = "openai", displayName = "a", authType = AuthType.API_KEY, modelId = "m", powerRank = 1), "sk")
        val setId = prompts.createSet("set")
        val itemId = prompts.addItem(setId, "başlık", "gövde")
        val runId = runs.createRun()
        runs.enqueueJobs(runId, listOf(JobSeed(itemId, null)))
        PromptDispatcher(runs, prompts, accounts, credentials, gen, now = { clock }, usage = usage).process(runId)
    }

    @Test fun providerReportedUsageIsStoredAsExact(): Unit = runBlocking {
        runWith(Gen { coroutineContext[TokenMeter]!!.add(TokenUsage(321, 45)); "ok" })
        val e = db.usageEventDao().since(0).single()
        assertEquals(UsageOutcome.SUCCESS, e.outcome)
        assertEquals(321, e.promptTokens)
        assertEquals(45, e.completionTokens)
        assertFalse(e.tokensEstimated)
    }

    @Test fun missingUsageOnSuccessIsEstimatedAndFlagged(): Unit = runBlocking {
        runWith(Gen { "tamam" }) // girdi "gövde" (5 karakter) -> 2, çıktı "tamam" (5) -> 2
        val e = db.usageEventDao().since(0).single()
        assertEquals(2, e.promptTokens)
        assertEquals(2, e.completionTokens)
        assertTrue(e.tokensEstimated)
    }

    @Test fun failedRequestWithoutUsageStaysUnknown(): Unit = runBlocking {
        runWith(Gen { throw AiGenerationException(Kind.BAD_REQUEST) })
        val events = db.usageEventDao().since(0)
        assertTrue(events.isNotEmpty())
        events.forEach {
            assertNull(it.promptTokens)
            assertNull(it.completionTokens)
            assertFalse(it.tokensEstimated)
        }
    }

    @Test fun failedRequestKeepsTokensTheProviderAlreadyBilled(): Unit = runBlocking {
        runWith(Gen { coroutineContext[TokenMeter]!!.add(TokenUsage(50, 0)); throw AiGenerationException(Kind.BAD_REQUEST) })
        val e = db.usageEventDao().since(0).first()
        assertEquals(50, e.promptTokens)
        assertEquals(0, e.completionTokens)
        assertFalse(e.tokensEstimated)
    }
}
