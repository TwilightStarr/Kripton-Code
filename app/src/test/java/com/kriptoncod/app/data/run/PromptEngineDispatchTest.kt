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
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Gelişmiş prompt motoru: değişkenler, çıktı bağlama, doğrulama + yeniden deneme kuralları (dağıtıcı düzeyinde). */
@RunWith(RobolectricTestRunner::class)
class PromptEngineDispatchTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: KriptonDatabase
    private lateinit var accounts: AiAccountRepository
    private lateinit var prompts: PromptRepository
    private lateinit var runs: RunRepository
    private var clock = 1_000L

    private class Recorder(val script: (call: Int, prompt: String) -> String) : AiGenerator {
        val prompts = mutableListOf<String>()
        override suspend fun generate(endpoint: ProviderEndpoint, modelId: String, credential: String, prompt: String): String {
            prompts += prompt
            return script(prompts.size, prompt)
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

    @After fun tearDown() {
        db.close()
    }

    private fun dispatcher(g: AiGenerator) = PromptDispatcher(runs, prompts, accounts, credentials, g, now = { clock }, sleep = {})

    private suspend fun oneAccount() = accounts.add(
        AiAccount(providerId = "openai", displayName = "m", authType = AuthType.API_KEY, modelId = "m", powerRank = 1),
        "sk-m",
    )

    private suspend fun run(vararg nodes: Pair<String, String>): Long {
        val runId = runs.createRun()
        runs.enqueueJobs(runId, nodes.map { (t, b) -> JobSeed(0, null, JobKind.WORK, "k", t, b) })
        return runId
    }

    @Test fun outputOfEarlierNodeIsInjectedAndVariablesResolve() = runBlocking {
        oneAccount()
        val runId = run(
            "Ayarlar" to "[[değişken: uygulama = Kervan]]",
            "A" to "{{uygulama}} için A yaz",
            "B" to "Şunu kullan: {{çıktı:A}} ({{başlık}})",
        )
        val gen = Recorder { n, _ -> if (n == 1) "ALPHA" else "BETA" }

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen).process(runId))

        assertEquals(2, gen.prompts.size) // tanım düğümü AI çağırmaz
        assertEquals("Kervan için A yaz", gen.prompts[0])
        assertEquals("Şunu kullan: ALPHA (B)", gen.prompts[1])
    }

    @Test fun validationFailureRetriesWithFeedbackThenSucceeds() = runBlocking {
        oneAccount()
        val runId = run("A" to "[[doğrula: içerir:OK]]\n[[tekrar: 2]]\nGörev")
        val gen = Recorder { n, _ -> if (n < 3) "kötü" else "OK" }

        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen).process(runId))

        assertEquals(3, gen.prompts.size)
        assertEquals("Görev", gen.prompts[0])
        assertTrue(gen.prompts[1].contains("REDDEDİLDİ"))
        assertEquals("OK", runs.readOutput(runs.getJobs(runId).single().id))
    }

    @Test fun exhaustedRetriesFailTheNodeUnlessContinue() = runBlocking {
        oneAccount()
        val stop = run("A" to "[[doğrula: içerir:OK]]\n[[tekrar: 1]]\nGörev")
        val gen = Recorder { _, _ -> "kötü" }
        assertEquals(DispatchOutcome.Finished(RunStatus.FAILED), dispatcher(gen).process(stop))
        assertEquals(2, gen.prompts.size)
        assertEquals(JobState.FAILED, runs.getJobs(stop).single().state)

        val keep = run("A" to "[[doğrula: içerir:OK]]\n[[tekrar: 1]]\n[[hatada: devam]]\nGörev")
        val gen2 = Recorder { _, _ -> "kötü" }
        assertEquals(DispatchOutcome.Finished(RunStatus.DONE), dispatcher(gen2).process(keep))
        assertEquals("kötü", runs.readOutput(runs.getJobs(keep).single().id))
    }

    @Test fun requiredSourceFailureSkipsTheAiCall() = runBlocking {
        oneAccount()
        val runId = run("A" to "[[doğrula: içerir:OK]]\nGörev", "B" to "[[kaynak: zorunlu]]\n{{çıktı:A}}")
        val gen = Recorder { _, _ -> "kötü" }

        assertEquals(DispatchOutcome.Finished(RunStatus.FAILED), dispatcher(gen).process(runId))

        assertEquals(1, gen.prompts.size) // B için AI çağrılmadı
        assertTrue(runs.getJobs(runId).all { it.state == JobState.FAILED })
        assertFalse(gen.prompts.any { it.contains("kaynak çıktı yok") })
    }
}
