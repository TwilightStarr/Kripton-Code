package com.kriptoncod.app.data.db

import com.kriptoncod.app.domain.AccountStatus
import com.kriptoncod.app.domain.AssignmentMode
import com.kriptoncod.app.domain.AuthType
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.testutil.inMemoryDb
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DaoTest {
    private lateinit var db: KriptonDatabase

    @Before fun setUp() { db = inMemoryDb() }
    @After fun tearDown() { db.close() }

    private fun account(name: String = "A", rank: Int = 1) = AiAccount(
        providerId = "openai", displayName = name, authType = AuthType.API_KEY, modelId = "m1", powerRank = rank,
    )

    @Test fun accountCrudAndFlow() = runBlocking {
        val dao = db.aiAccountDao()
        val id = dao.insert(account("Ana", 5))
        assertEquals("Ana", dao.get(id)?.displayName)
        assertEquals(1, dao.observeAll().first().size)

        dao.update(dao.get(id)!!.copy(displayName = "Yeni", modelId = "m2"))
        assertEquals("m2", dao.get(id)!!.modelId)

        dao.setStatus(id, AccountStatus.WAITING, 1234L)
        val waiting = dao.get(id)!!
        assertEquals(AccountStatus.WAITING, waiting.status)
        assertEquals(1234L, waiting.waitUntil)

        dao.setEnabled(id, false)
        assertTrue(dao.observeEnabled().first().isEmpty())

        dao.deleteById(id)
        assertNull(dao.get(id))
    }

    @Test fun accountsOrderedByPowerRankDesc() = runBlocking {
        val dao = db.aiAccountDao()
        dao.insert(account("zayıf", 1))
        dao.insert(account("güçlü", 9))
        assertEquals(listOf("güçlü", "zayıf"), dao.observeAll().first().map { it.displayName })
    }

    @Test fun releaseExpiredWaitsOnlyReleasesDue() = runBlocking {
        val dao = db.aiAccountDao()
        val due = dao.insert(account("due"))
        val later = dao.insert(account("later"))
        dao.setStatus(due, AccountStatus.WAITING, 100L)
        dao.setStatus(later, AccountStatus.WAITING, 10_000L)
        assertEquals(1, dao.releaseExpiredWaits(500L, AccountStatus.WAITING, AccountStatus.READY))
        assertEquals(AccountStatus.READY, dao.get(due)!!.status)
        assertNull(dao.get(due)!!.waitUntil)
        assertEquals(AccountStatus.WAITING, dao.get(later)!!.status)
    }

    @Test fun promptItemsOrderedAndCascadeOnSetDelete() = runBlocking {
        val setId = db.promptSetDao().insert(PromptSet(name = "S"))
        val items = db.promptItemDao()
        items.insert(PromptItem(setId = setId, title = "b", body = "x", order = 1))
        items.insert(PromptItem(setId = setId, title = "a", body = "y", order = 0))
        assertEquals(listOf("a", "b"), items.observeBySet(setId).first().map { it.title })
        assertEquals(1, items.maxPosition(setId))

        db.promptSetDao().deleteById(setId)
        assertTrue(items.getBySet(setId).isEmpty())
    }

    @Test fun assignmentUpsertAndCascades() = runBlocking {
        val setId = db.promptSetDao().insert(PromptSet(name = "S"))
        val promptId = db.promptItemDao().insert(PromptItem(setId = setId, title = "t", body = "b", order = 0))
        val acc1 = db.aiAccountDao().insert(account("1"))
        val acc2 = db.aiAccountDao().insert(account("2"))
        val dao = db.assignmentDao()

        dao.upsert(Assignment(promptId, acc1, AssignmentMode.AUTO))
        dao.upsert(Assignment(promptId, acc2, AssignmentMode.MANUAL))
        val a = dao.get(promptId)!!
        assertEquals(acc2, a.accountId)
        assertEquals(AssignmentMode.MANUAL, a.mode)
        assertEquals(1, dao.observeForSet(setId).first().size)

        db.aiAccountDao().deleteById(acc2)
        assertNull(dao.get(promptId))
    }

    @Test fun runAndJobLifecycle() = runBlocking {
        val runDao = db.runDao()
        val jobDao = db.jobDao()
        val accId = db.aiAccountDao().insert(account())
        val runId = runDao.insert(RunRecord(createdAt = 10L))
        val jobId = jobDao.insert(Job(runId = runId, promptId = 7L, accountId = accId, updatedAt = 10L))
        assertEquals(JobState.PENDING, jobDao.get(jobId)!!.state)

        jobDao.markStarted(jobId, JobState.RUNNING, 20L)
        jobDao.markStarted(jobId, JobState.RUNNING, 21L)
        assertEquals(2, jobDao.get(jobId)!!.attempts)

        jobDao.setResult(jobId, JobState.DONE, "outputs/1/1.txt", 30L)
        val done = jobDao.get(jobId)!!
        assertEquals(JobState.DONE, done.state)
        assertEquals("outputs/1/1.txt", done.outputPath)
        assertEquals(30L, done.updatedAt)

        runDao.incrementCiAttempts(runId)
        runDao.incrementCiAttempts(runId)
        runDao.setStatus(runId, RunStatus.CI_WAITING)
        runDao.setRepo(runId, "sahip/depo")
        runDao.setZip(runId, "zips/1.zip")
        val run = runDao.observe(runId).first()!!
        assertEquals(2, run.ciAttempts)
        assertEquals(RunStatus.CI_WAITING, run.status)
        assertEquals("sahip/depo", run.repo)
        assertEquals("zips/1.zip", run.zipPath)
    }

    @Test fun deletingAccountNullsJobAccountButKeepsJob() = runBlocking {
        val accId = db.aiAccountDao().insert(account())
        val runId = db.runDao().insert(RunRecord(createdAt = 1L))
        val jobId = db.jobDao().insert(Job(runId = runId, promptId = 1L, accountId = accId, updatedAt = 1L))
        db.aiAccountDao().deleteById(accId)
        val job = db.jobDao().get(jobId)
        assertNotNull(job)
        assertNull(job!!.accountId)
    }

    @Test fun deletingRunCascadesJobs() = runBlocking {
        val runId = db.runDao().insert(RunRecord(createdAt = 1L))
        db.jobDao().insert(Job(runId = runId, promptId = 1L, updatedAt = 1L))
        db.runDao().deleteById(runId)
        assertTrue(db.jobDao().getByRun(runId).isEmpty())
    }

    @Test fun runsNewestFirst() = runBlocking {
        db.runDao().insert(RunRecord(createdAt = 1L))
        val newest = db.runDao().insert(RunRecord(createdAt = 5L))
        assertEquals(newest, db.runDao().observeAll().first().first().id)
    }

    @Test fun concurrentInsertsAllPersistWithUniqueIds() = runBlocking {
        val dao = db.aiAccountDao()
        val ids = (1..100).map { i -> async(kotlinx.coroutines.Dispatchers.IO) { dao.insert(account("n$i")) } }.awaitAll()
        assertEquals(100, ids.toSet().size)
        assertEquals(100, dao.observeAll().first().size)
    }

    @Test fun noSecretColumnsInSchema() {
        val sensitive = listOf("key", "token", "secret", "password")
        val tables = listOf("ai_accounts", "prompt_sets", "prompt_items", "assignments", "runs", "jobs")
        val cursorDb = db.openHelper.readableDatabase
        tables.forEach { table ->
            cursorDb.query("PRAGMA table_info($table)").use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) {
                    val col = c.getString(nameIdx).lowercase()
                    assertTrue("$table.$col gizli veri sütunu gibi görünüyor", sensitive.none { col.contains(it) })
                }
            }
        }
    }
}
