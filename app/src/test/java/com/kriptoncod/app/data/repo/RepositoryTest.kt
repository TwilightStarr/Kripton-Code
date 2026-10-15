package com.kriptoncod.app.data.repo

import com.kriptoncod.app.data.db.AiAccount
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.files.HistoryStore
import com.kriptoncod.app.data.files.OutputStore
import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.testutil.FakeKeyVault
import com.kriptoncod.app.testutil.inMemoryDb
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
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

@RunWith(RobolectricTestRunner::class)
class RepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: KriptonDatabase
    private lateinit var vault: FakeKeyVault
    private lateinit var outputs: OutputStore
    private lateinit var history: HistoryStore
    private lateinit var accounts: AiAccountRepository
    private lateinit var prompts: PromptRepository
    private lateinit var runs: RunRepository
    private var clock = 1_000L

    @Before fun setUp() {
        db = inMemoryDb()
        vault = FakeKeyVault()
        outputs = OutputStore(tmp.root)
        history = HistoryStore(tmp.root)
        accounts = AiAccountRepository(db.aiAccountDao(), vault) { clock }
        prompts = PromptRepository(db)
        runs = RunRepository(db, outputs, history) { clock }
    }

    @After fun tearDown() { db.close() }

    private fun acc(name: String = "A") = AiAccount(
        providerId = "openai", displayName = name, authType = AuthType.API_KEY, modelId = "m", powerRank = 1,
    )

    private fun file(rel: String) = File(tmp.root, rel)

    // --- Hesap + kasa ---

    @Test fun secretLivesOnlyInVault() = runBlocking {
        val id = accounts.add(acc(), "sk-123")
        assertEquals("sk-123", accounts.getSecret(id))
        assertTrue(accounts.hasSecret(id))
        // Room'daki satır anahtarı içermez
        assertFalse(accounts.get(id).toString().contains("sk-123"))
    }

    @Test fun addRollsBackRowWhenVaultFails() = runBlocking {
        val failing = object : com.kriptoncod.app.data.vault.KeyVault by vault {
            override suspend fun put(keyId: String, secret: String) = error("kasa hatası")
        }
        val repo = AiAccountRepository(db.aiAccountDao(), failing)
        try {
            repo.add(acc(), "s"); throw AssertionError("hata bekleniyordu")
        } catch (_: IllegalStateException) {
        }
        assertTrue(db.aiAccountDao().allIds().isEmpty())
    }

    @Test fun deleteRemovesRowAndSecret() = runBlocking {
        val id = accounts.add(acc(), "sk")
        accounts.delete(id)
        assertNull(accounts.get(id))
        assertFalse(accounts.hasSecret(id))
    }

    @Test fun purgeOrphanSecretsKeepsValidOnes() = runBlocking {
        val keep = accounts.add(acc("keep"), "k")
        vault.put(AiAccount.vaultKeyId(9999), "yetim")
        vault.put("baska-bir-kayit", "dokunma")
        assertEquals(1, accounts.purgeOrphanSecrets())
        assertTrue(accounts.hasSecret(keep))
        assertTrue(vault.contains("baska-bir-kayit"))
    }

    @Test fun waitingStatusAndRelease() = runBlocking {
        val id = accounts.add(acc(), null)
        accounts.markWaiting(id, until = 1_500L)
        assertEquals(AccountStatus.WAITING, accounts.get(id)!!.status)
        assertEquals(0, accounts.releaseExpiredWaits())
        clock = 2_000L
        assertEquals(1, accounts.releaseExpiredWaits())
        assertEquals(AccountStatus.READY, accounts.get(id)!!.status)
    }

    // --- Prompt seti ---

    @Test fun itemsAppendInOrderAndReorder() = runBlocking {
        val set = prompts.createSet("S")
        val a = prompts.addItem(set, "a", "1")
        val b = prompts.addItem(set, "b", "2")
        val c = prompts.addItem(set, "c", "3")
        assertEquals(listOf(a, b, c), prompts.observeItems(set).first().map { it.id })

        prompts.reorder(set, listOf(c, a))
        assertEquals(listOf(c, a, b), prompts.observeItems(set).first().map { it.id })
        assertEquals(listOf(0, 1, 2), prompts.getItems(set).map { it.order })
    }

    @Test fun concurrentAddItemGivesDistinctPositions() = runBlocking {
        val set = prompts.createSet("S")
        (1..30).map { i -> async(Dispatchers.IO) { prompts.addItem(set, "t$i", "b") } }.awaitAll()
        val positions = prompts.getItems(set).map { it.order }
        assertEquals(30, positions.toSet().size)
    }

    @Test fun assignAndUnassign() = runBlocking {
        val set = prompts.createSet("S")
        val p = prompts.addItem(set, "t", "b")
        val a = accounts.add(acc(), null)
        prompts.assign(p, a, AssignmentMode.AUTO)
        assertEquals(1, prompts.observeAssignments(set).first().size)
        prompts.unassign(p)
        assertTrue(prompts.observeAssignments(set).first().isEmpty())
    }

    // --- Çalıştırma, çıktı, geçmiş ---

    @Test fun completeJobWritesFileAndMarksDone() = runBlocking {
        val run = runs.createRun("o/r")
        val job = runs.enqueueJobs(run, listOf(JobSeed(1, null))).single()
        runs.startJob(job)
        runs.completeJob(job, "çıktı")
        val j = runs.getJobs(run).single()
        assertEquals(JobState.DONE, j.state)
        assertEquals(1, j.attempts)
        assertEquals("outputs/$run/$job.txt", j.outputPath)
        assertEquals("çıktı", runs.readOutput(job))
    }

    @Test fun zipAndHistoryPersistAndDeleteRunCleansEverything() = runBlocking {
        val run = runs.createRun("o/r")
        val job = runs.enqueueJobs(run, listOf(JobSeed(1, null))).single()
        runs.completeJob(job, "x")
        runs.attachZip(run) { it.write(byteArrayOf(1)) }
        runs.setRunStatus(run, RunStatus.DONE)
        runs.saveHistory(run)

        assertEquals("zips/$run.zip", runs.getRun(run)!!.zipPath)
        val h = history.read(run)!!
        assertEquals("DONE", h.status)
        assertEquals(1, h.jobs.size)

        runs.deleteRun(run)
        assertNull(runs.getRun(run))
        assertTrue(runs.getJobs(run).isEmpty())
        assertFalse(file("outputs/$run").exists())
        assertFalse(file("zips/$run.zip").exists())
        assertNull(history.read(run))
    }

    // --- Kurtarma ---

    @Test fun recoverAdoptsFinishedOutputWhenCrashHitBetweenRenameAndDb() = runBlocking {
        val run = runs.createRun()
        val job = runs.enqueueJobs(run, listOf(JobSeed(1, null))).single()
        runs.startJob(job)
        outputs.write(run, job, "tam çıktı") // DB güncellenmeden çökme

        val s = runs.recover()

        assertEquals(1, s.adopted)
        val j = runs.getJobs(run).single()
        assertEquals(JobState.DONE, j.state)
        assertEquals("outputs/$run/$job.txt", j.outputPath)
    }

    @Test fun recoverRequeuesRunningJobWithOnlyPartialAndDiscardsPartial() = runBlocking {
        val run = runs.createRun()
        val job = runs.enqueueJobs(run, listOf(JobSeed(1, null))).single()
        runs.startJob(job)
        outputs.startPartial(run, job); outputs.appendPartial(run, job, "yarım yamalak")

        val s = runs.recover()

        assertEquals(1, s.requeued)
        assertEquals(listOf(com.kriptoncod.app.data.files.JobKey(run, job)), s.files.discardedPartials)
        assertEquals(JobState.PENDING, runs.getJobs(run).single().state)
        assertFalse(file("outputs/$run/$job.partial").exists())
        assertEquals(1, runs.getJobs(run).single().attempts) // deneme sayısı korunur
    }

    @Test fun recoverRequeuesDoneJobWhoseOutputIsMissingOrEmpty() = runBlocking {
        val run = runs.createRun()
        val ids = runs.enqueueJobs(run, listOf(JobSeed(1, null), JobSeed(2, null), JobSeed(3, null)))
        ids.forEach { runs.completeJob(it, "içerik") }
        file("outputs/$run/${ids[0]}.txt").delete()          // kayıp
        file("outputs/$run/${ids[1]}.txt").writeText("")     // boş/bozuk
        // ids[2] sağlam

        val s = runs.recover()

        assertEquals(2, s.lostOutputs)
        val byId = runs.getJobs(run).associateBy { it.id }
        assertEquals(JobState.PENDING, byId[ids[0]]!!.state)
        assertNull(byId[ids[0]]!!.outputPath)
        assertEquals(JobState.PENDING, byId[ids[1]]!!.state)
        assertEquals(JobState.DONE, byId[ids[2]]!!.state)
    }

    @Test fun recoverLeavesWaitingAndPendingWithoutOutputUntouched() = runBlocking {
        val run = runs.createRun()
        val ids = runs.enqueueJobs(run, listOf(JobSeed(1, null), JobSeed(2, null)))
        runs.waitJob(ids[1])
        val s = runs.recover()
        assertEquals(0, s.adopted + s.requeued + s.lostOutputs)
        assertEquals(listOf(JobState.PENDING, JobState.WAITING), runs.getJobs(run).map { it.state })
    }

    @Test fun recoverIsIdempotent() = runBlocking {
        val run = runs.createRun()
        val job = runs.enqueueJobs(run, listOf(JobSeed(1, null))).single()
        runs.startJob(job)
        outputs.write(run, job, "x")
        runs.recover()
        val second = runs.recover()
        assertEquals(0, second.adopted + second.requeued + second.lostOutputs)
    }

    @Test fun recoverSurvivesCorruptOutputPathInDb() = runBlocking {
        val run = runs.createRun()
        val job = runs.enqueueJobs(run, listOf(JobSeed(1, null))).single()
        db.jobDao().setResult(job, JobState.DONE, "../../etc/passwd", 1L)
        val s = runs.recover()
        assertEquals(1, s.lostOutputs)
        assertEquals(JobState.PENDING, runs.getJobs(run).single().state)
    }

    @Test fun requeueJobDeletesOldOutputSoRecoverDoesNotResurrectIt() = runBlocking {
        val run = runs.createRun()
        val job = runs.enqueueJobs(run, listOf(JobSeed(1, null))).single()
        runs.completeJob(job, "eski")
        runs.requeueJob(job)
        assertEquals(0, runs.recover().adopted)
        assertEquals(JobState.PENDING, runs.getJobs(run).single().state)
    }

    @Test fun concurrentCompleteJobsAllPersist() = runBlocking {
        val run = runs.createRun()
        val ids = runs.enqueueJobs(run, (1L..25L).map { JobSeed(it, null) })
        ids.map { id -> async(Dispatchers.IO) { runs.completeJob(id, "çıktı-$id") } }.awaitAll()
        ids.forEach { assertEquals("çıktı-$it", runs.readOutput(it)) }
        assertTrue(runs.getJobs(run).all { it.state == JobState.DONE })
    }
}
