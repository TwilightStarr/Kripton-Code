package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.ai.AiGenerationException
import com.kriptoncod.app.data.ai.AiGenerationException.Kind
import com.kriptoncod.app.data.ai.AiGenerator
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
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.domain.UsageOutcome
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** PromptDispatcher + UsageRepository + AccountSelector birlikte: kullanım kaydı seçimi gerçekten yönlendiriyor mu. */
@RunWith(RobolectricTestRunner::class)
class AccountRotationTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: KriptonDatabase
    private lateinit var accounts: AiAccountRepository
    private lateinit var prompts: PromptRepository
    private lateinit var runs: RunRepository
    private lateinit var usage: UsageRepository
    private var clock = 1_000L

    private class ScriptedGenerator(val script: (modelId: String) -> String) : AiGenerator {
        val calls = mutableListOf<String>()
        override suspend fun generate(endpoint: ProviderEndpoint, modelId: String, credential: String, prompt: String): String {
            calls += modelId
            return script(modelId)
        }
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

    private fun dispatcher(generator: AiGenerator) = PromptDispatcher(runs, prompts, accounts, credentials, generator, now = { clock }, usage = usage)

    private suspend fun account(model: String, rank: Int, limit: Int? = null, backup: Boolean = false): Long = accounts.add(
        AiAccount(
            providerId = "openai",
            displayName = model,
            authType = AuthType.API_KEY,
            modelId = model,
            powerRank = rank,
            dailyLimit = limit,
            isBackup = backup,
        ),
        "sk-$model",
    )

    private suspend fun runWithOnePrompt(accountId: Long? = null, mode: AssignmentMode? = null): Long {
        val setId = prompts.createSet("set")
        val itemId = prompts.addItem(setId, "başlık", "gövde")
        if (accountId != null && mode != null) prompts.assign(itemId, accountId, mode)
        val runId = runs.createRun()
        runs.enqueueJobs(runId, listOf(JobSeed(itemId, accountId)))
        return runId
    }

    private suspend fun runOnce(gen: AiGenerator): DispatchOutcome = dispatcher(gen).process(runWithOnePrompt())

    @Test fun equalRankAccountsShareTheLoad(): Unit = runBlocking {
        account("a", 3)
        account("b", 3)
        val gen = ScriptedGenerator { "ok" }
        repeat(4) {
            clock += 1_000
            assertEquals(DispatchOutcome.Finished(RunStatus.DONE), runOnce(gen))
        }
        assertEquals(listOf("a", "b", "a", "b"), gen.calls)
    }

    @Test fun strongerAccountStillWinsOverRotation(): Unit = runBlocking {
        account("weak", 1)
        account("strong", 5)
        val gen = ScriptedGenerator { "ok" }
        repeat(3) {
            clock += 1_000
            runOnce(gen)
        }
        assertEquals(listOf("strong", "strong", "strong"), gen.calls)
    }

    @Test fun dailyLimitMovesWorkToNextAccountAndComesBackAfterWindow(): Unit = runBlocking {
        account("strong", 5, limit = 1)
        account("weak", 1)
        val gen = ScriptedGenerator { "ok" }

        clock += 1_000
        runOnce(gen)
        clock += 1_000
        runOnce(gen) // strong'un günlük hakkı doldu
        assertEquals(listOf("strong", "weak"), gen.calls)

        clock += AccountSelector.DAY_MS // ilk isteğin 24 saati doldu
        runOnce(gen)
        assertEquals(listOf("strong", "weak", "strong"), gen.calls)
    }

    @Test fun jobWaitsForQuotaResetWhenOnlyLimitedAccountExists(): Unit = runBlocking {
        account("solo", 3, limit = 1)
        val gen = ScriptedGenerator { "ok" }
        clock += 1_000
        runOnce(gen)
        val firstAt = clock

        clock += 5_000
        val outcome = runOnce(gen)

        assertTrue(outcome is DispatchOutcome.Wait)
        assertEquals(1, gen.calls.size) // ikinci istek hiç atılmadı
        // Bekleme, ilk isteğin 24 saati dolana kadar.
        assertEquals(firstAt + AccountSelector.DAY_MS - clock, (outcome as DispatchOutcome.Wait).delayMs)
    }

    @Test fun manualAssignmentFallsBackToPoolWhenQuotaIsFull(): Unit = runBlocking {
        val manual = account("manual", 1, limit = 1)
        account("pool", 5)
        val gen = ScriptedGenerator { "ok" }
        clock += 1_000
        dispatcher(gen).process(runWithOnePrompt(manual, AssignmentMode.MANUAL))
        clock += 1_000
        dispatcher(gen).process(runWithOnePrompt(manual, AssignmentMode.MANUAL))
        assertEquals(listOf("manual", "pool"), gen.calls)
    }

    @Test fun circuitBreakerSkipsFailingAccountThenRetriesAfterCooldown(): Unit = runBlocking {
        val flaky = account("flaky", 5)
        account("steady", 1)
        repeat(3) { usage.record(flaky, null, UsageOutcome.TRANSIENT, 10) }
        val gen = ScriptedGenerator { "ok" }

        clock += 1_000
        runOnce(gen)
        assertEquals(listOf("steady"), gen.calls)

        // Devre kapandı ama arıza hâlâ taze: flaky havuzda, yine de sağlam hesabın ardında.
        clock += AccountSelector.BREAKER_COOLDOWN_MS + 1
        runOnce(gen)
        assertEquals(listOf("steady", "steady"), gen.calls)

        // Arıza unutulunca güçlü hesap geri gelir.
        clock += AccountSelector.HEALTH_WINDOW_MS
        runOnce(gen)
        assertEquals(listOf("steady", "steady", "flaky"), gen.calls)
    }

    @Test fun backupAccountUsedOnlyAfterPrimaryFails(): Unit = runBlocking {
        account("primary", 1)
        account("backup", 9, backup = true)
        val healthy = ScriptedGenerator { "ok" }
        clock += 1_000
        runOnce(healthy)
        assertEquals(listOf("primary"), healthy.calls)

        val failing = ScriptedGenerator { m -> if (m == "primary") throw AiGenerationException(Kind.UNAUTHORIZED, 401) else "ok" }
        clock += 1_000
        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), runOnce(failing))
        assertEquals(listOf("primary", "backup"), failing.calls)
    }

    @Test fun healthSnapshotCountsOnlyServedRequestsTowardQuota(): Unit = runBlocking {
        val id = account("a", 1)
        usage.record(id, null, UsageOutcome.SUCCESS, 5)
        usage.record(id, null, UsageOutcome.RATE_LIMITED, 5)
        usage.record(id, null, UsageOutcome.UNAUTHORIZED, 5)
        usage.record(id, null, UsageOutcome.TRANSIENT, 5)

        val h = usage.healthSnapshot().getValue(id)
        assertEquals(2, h.requestTimes.size) // SUCCESS + TRANSIENT
        assertEquals(UsageOutcome.TRANSIENT, h.recent.first().outcome) // en yeni başta
        assertEquals(4, h.recent.size)
    }
}
