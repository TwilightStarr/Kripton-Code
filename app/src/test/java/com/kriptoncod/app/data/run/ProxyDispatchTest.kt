package com.kriptoncod.app.data.run

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
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Proxy hesabı dağıtıcıda kendi taban adresi ve kendi anahtarıyla çalışır. */
@RunWith(RobolectricTestRunner::class)
class ProxyDispatchTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: KriptonDatabase
    private lateinit var vault: FakeKeyVault
    private lateinit var accounts: AiAccountRepository
    private lateinit var prompts: PromptRepository
    private lateinit var runs: RunRepository

    private class Call(val chatUrl: String, val model: String, val credential: String)

    private class Recorder : AiGenerator {
        val calls = mutableListOf<Call>()
        override suspend fun generate(endpoint: ProviderEndpoint, modelId: String, credential: String, prompt: String): String {
            calls += Call(endpoint.chatUrl.toString(), modelId, credential)
            return "kod"
        }
    }

    @Before fun setUp() {
        db = inMemoryDb()
        vault = FakeKeyVault()
        accounts = AiAccountRepository(db.aiAccountDao(), vault) { 1_000L }
        prompts = PromptRepository(db)
        runs = RunRepository(db, OutputStore(tmp.root), HistoryStore(tmp.root)) { 1_000L }
    }

    @After fun tearDown() { db.close() }

    private suspend fun proxyAccount(name: String, url: String, key: String, model: String): Long = accounts.add(
        AiAccount(
            providerId = "claude", displayName = name, authType = AuthType.PROXY,
            modelId = model, powerRank = 5, baseUrl = url,
        ),
        key,
    )

    private suspend fun runFor(accountId: Long): Long {
        val itemId = prompts.addItem(prompts.createSet("set"), "başlık", "gövde")
        prompts.assign(itemId, accountId, AssignmentMode.MANUAL)
        val runId = runs.createRun()
        runs.enqueueJobs(runId, listOf(JobSeed(itemId, accountId)))
        return runId
    }

    private fun dispatcher(gen: AiGenerator) = PromptDispatcher(
        runs, prompts, accounts, credentials, gen, now = { 1_000L },
    )

    /** Üretimdeki VaultCredentialProvider ile aynı kural: API_KEY/PROXY anahtarı KeyVault'tan gelir. */
    private val credentials = object : CredentialProvider {
        override suspend fun credentialFor(account: AiAccount): String? = accounts.getSecret(account.id)
    }

    @Test fun proxyAccountsOfSameProviderHitTheirOwnServerWithTheirOwnKey() = runBlocking {
        val mine = proxyAccount("Benim", "http://192.168.1.20:8317/v1", "my-key", "claude-sonnet-5-5")
        val friend = proxyAccount("Arkadaşım", "https://friend.example.com/v1", "friend-key", "claude-opus-5-5")
        val gen = Recorder()

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen).process(runFor(mine)))
        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen).process(runFor(friend)))

        assertEquals("http://192.168.1.20:8317/v1/chat/completions", gen.calls[0].chatUrl)
        assertEquals("my-key", gen.calls[0].credential)
        assertEquals("claude-sonnet-5-5", gen.calls[0].model)
        assertEquals("https://friend.example.com/v1/chat/completions", gen.calls[1].chatUrl)
        assertEquals("friend-key", gen.calls[1].credential)
        assertEquals("claude-opus-5-5", gen.calls[1].model)
    }
}
