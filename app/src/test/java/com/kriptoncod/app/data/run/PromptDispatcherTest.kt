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
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
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

@RunWith(RobolectricTestRunner::class)
class PromptDispatcherTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: KriptonDatabase
    private lateinit var accounts: AiAccountRepository
    private lateinit var prompts: PromptRepository
    private lateinit var runs: RunRepository
    private var clock = 1_000L

    private class ScriptedGenerator(val script: (modelId: String) -> String) : AiGenerator {
        val calls = mutableListOf<String>()
        override suspend fun generate(
            endpoint: ProviderEndpoint,
            modelId: String,
            credential: String,
            prompt: String,
        ): String {
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
    }

    @After fun tearDown() { db.close() }

    private fun dispatcher(generator: AiGenerator) =
        PromptDispatcher(runs, prompts, accounts, credentials, generator, now = { clock })

    private suspend fun account(model: String, rank: Int): Long = accounts.add(
        AiAccount(
            providerId = "openai", displayName = model, authType = AuthType.API_KEY,
            modelId = model, powerRank = rank,
        ),
        "sk-$model",
    )

    /** Tek promptlu run kurar; [accountId] verilirse iş o hesaba atanır. */
    private suspend fun runWithOnePrompt(accountId: Long? = null, mode: AssignmentMode? = null): Long {
        val setId = prompts.createSet("set")
        val itemId = prompts.addItem(setId, "başlık", "gövde")
        if (accountId != null && mode != null) prompts.assign(itemId, accountId, mode)
        val runId = runs.createRun()
        runs.enqueueJobs(runId, listOf(JobSeed(itemId, accountId)))
        return runId
    }

    @Test fun autoPicksStrongestAccount() = runBlocking {
        account("weak", 1)
        account("strong", 5)
        val runId = runWithOnePrompt()
        val gen = ScriptedGenerator { "kod" }

        val outcome = dispatcher(gen).process(runId)

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), outcome)
        assertEquals(listOf("strong"), gen.calls)
        val job = runs.getJobs(runId).single()
        assertEquals(JobState.DONE, job.state)
        assertEquals("kod", runs.readOutput(job.id))
    }

    @Test fun rateLimitedAutoFallsBackToNextAccount() = runBlocking {
        val strong = account("strong", 5)
        account("weak", 1)
        val runId = runWithOnePrompt()
        val gen = ScriptedGenerator { model ->
            if (model == "strong") throw AiGenerationException(Kind.RATE_LIMITED, 429, retryAfterMs = 30_000) else "ok"
        }

        val outcome = dispatcher(gen).process(runId)

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), outcome)
        assertEquals(listOf("strong", "weak"), gen.calls)
        val a = accounts.get(strong)!!
        assertEquals(AccountStatus.WAITING, a.status)
        assertEquals(clock + 30_000, a.waitUntil)
    }

    @Test fun manualRateLimitedWaitsInsteadOfReassigning() = runBlocking {
        val strong = account("strong", 5)
        val other = account("other", 9)
        val runId = runWithOnePrompt(strong, AssignmentMode.MANUAL)
        val gen = ScriptedGenerator { throw AiGenerationException(Kind.RATE_LIMITED, 429, retryAfterMs = 30_000) }

        val outcome = dispatcher(gen).process(runId)

        assertEquals(DispatchOutcome.Wait(30_000), outcome)
        assertEquals(listOf("strong"), gen.calls) // "other" daha güçlü ama MANUAL atamaya saygı duyulur
        assertEquals(JobState.WAITING, runs.getJobs(runId).single().state)
        assertEquals(AccountStatus.READY, accounts.get(other)!!.status)

        // Bekleme bitince aynı hesapta devam eder.
        clock += 31_000
        val ok = ScriptedGenerator { "ok" }
        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(ok).process(runId))
        assertEquals(listOf("strong"), ok.calls)
    }

    @Test fun unauthorizedMarksAccountErrorAndFallsBack() = runBlocking {
        val bad = account("bad", 5)
        account("good", 1)
        val runId = runWithOnePrompt()
        val gen = ScriptedGenerator { model ->
            if (model == "bad") throw AiGenerationException(Kind.UNAUTHORIZED, 401) else "ok"
        }

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen).process(runId))
        assertEquals(AccountStatus.ERROR, accounts.get(bad)!!.status)
    }

    @Test fun manualAccountClosedFallsBackToPoolInsteadOfFailing() = runBlocking {
        val bad = account("bad", 5)
        account("good", 1)
        val runId = runWithOnePrompt(bad, AssignmentMode.MANUAL)
        val gen = ScriptedGenerator { model ->
            if (model == "bad") throw AiGenerationException(Kind.UNAUTHORIZED, 401) else "ok"
        }

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen).process(runId))
        assertEquals(listOf("bad", "good"), gen.calls)
        assertEquals(AccountStatus.ERROR, accounts.get(bad)!!.status)
    }

    @Test fun noUsableAccountKeepsJobWaitingAndResumesWhenAccountAdded() = runBlocking {
        val only = account("only", 1)
        val runId = runWithOnePrompt()
        val dead = ScriptedGenerator { throw AiGenerationException(Kind.UNAUTHORIZED, 401) }

        assertEquals(DispatchOutcome.Wait(PromptDispatcher.NO_ACCOUNT_WAIT_MS), dispatcher(dead).process(runId))
        assertEquals(JobState.WAITING, runs.getJobs(runId).single().state)
        assertEquals(AccountStatus.ERROR, accounts.get(only)!!.status)

        account("new", 1) // kullanıcı yeni/yedek hesap ekledi
        val ok = ScriptedGenerator { "ok" }
        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(ok).process(runId))
        assertEquals(listOf("new"), ok.calls)
    }

    @Test fun allAccountsRateLimitedReturnsWaitWithEarliestRelease() = runBlocking {
        account("a", 5)
        account("b", 1)
        val runId = runWithOnePrompt()
        val gen = ScriptedGenerator { model ->
            val ms = if (model == "a") 60_000L else 20_000L
            throw AiGenerationException(Kind.RATE_LIMITED, 429, retryAfterMs = ms)
        }

        val outcome = dispatcher(gen).process(runId)

        assertEquals(DispatchOutcome.Wait(20_000), outcome)
        assertEquals(RunStatus.RUNNING, runs.getRun(runId)!!.status)
    }

    @Test fun serverErrorRetriesThenFails() = runBlocking {
        account("a", 1)
        val runId = runWithOnePrompt()
        val gen = ScriptedGenerator { throw AiGenerationException(Kind.SERVER, 503) }
        val d = dispatcher(gen)

        assertEquals(DispatchOutcome.Wait(PromptDispatcher.RETRY_BASE_MS), d.process(runId))
        repeat(PromptDispatcher.MAX_ATTEMPTS - 2) { assertTrue(d.process(runId) is DispatchOutcome.Wait) }
        assertEquals(DispatchOutcome.Finished(RunStatus.FAILED), d.process(runId))
        assertEquals(PromptDispatcher.MAX_ATTEMPTS, gen.calls.size)
        assertEquals(JobState.FAILED, runs.getJobs(runId).single().state)
    }

    @Test fun cancelledRunDoesNothing() = runBlocking {
        account("a", 1)
        val runId = runWithOnePrompt()
        runs.setRunStatus(runId, RunStatus.CANCELLED)
        val gen = ScriptedGenerator { "x" }

        assertEquals(DispatchOutcome.Cancelled, dispatcher(gen).process(runId))
        assertTrue(gen.calls.isEmpty())
    }

    @Test fun staleRunningJobIsRequeuedAndCompleted() = runBlocking {
        account("a", 1)
        val runId = runWithOnePrompt()
        runs.startJob(runs.getJobs(runId).single().id) // önceki worker öldü: iş RUNNING'de kaldı
        val gen = ScriptedGenerator { "ok" }

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen).process(runId))
        assertEquals(1, gen.calls.size)
    }
}
