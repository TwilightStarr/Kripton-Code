package com.kriptoncod.app.data.run

import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunCompleterTest {

    private class FakeStore(var states: List<JobState>, var status: RunStatus? = RunStatus.RUNNING) : RunCompleter.Store {
        var history = 0
        override suspend fun jobStates(runId: Long) = states
        override suspend fun status(runId: Long) = status
        override suspend fun setStatus(runId: Long, status: RunStatus) {
            this.status = status
        }
        override suspend fun saveHistory(runId: Long) {
            history++
        }
    }

    private class Harness(
        val store: FakeStore,
        var packaged: Boolean = true,
        var repo: Boolean = true,
        var publishOk: Boolean = true,
    ) {
        val calls = mutableListOf<String>()
        val completer = RunCompleter(
            runs = store,
            packageRun = {
                calls += "package"
                packaged
            },
            ensureTests = { calls += "tests" },
            shouldPublish = { repo },
            publish = {
                calls += "publish"
                publishOk
            },
            startCiLoop = { calls += "ci" },
        )
    }

    private val allDone = listOf(JobState.DONE, JobState.DONE)
    private val oneFailed = listOf(JobState.DONE, JobState.FAILED)

    @Test fun failedNodeYieldsPartialAndNeverPushes() = runBlocking {
        val h = Harness(FakeStore(oneFailed))
        assertEquals(RunStatus.PARTIAL, h.completer.complete(1))
        assertEquals(listOf("package"), h.calls) // ne test üretimi ne push
    }

    @Test fun partialWithoutRepoIsStillPartialNotDone() = runBlocking {
        val h = Harness(FakeStore(oneFailed), repo = false)
        assertEquals(RunStatus.PARTIAL, h.completer.complete(1))
    }

    @Test fun cleanRunWithRepoGeneratesTestsThenPushesThenWatchesCi() = runBlocking {
        val h = Harness(FakeStore(allDone))
        assertEquals(RunStatus.CI_WAITING, h.completer.complete(1))
        assertEquals(listOf("package", "tests", "publish", "ci"), h.calls)
    }

    @Test fun cleanRunWithoutRepoIsDone() = runBlocking {
        val h = Harness(FakeStore(allDone), repo = false)
        assertEquals(RunStatus.DONE, h.completer.complete(1))
        assertEquals(listOf("package"), h.calls)
    }

    @Test fun packagingFailureIsFailed() = runBlocking {
        val h = Harness(FakeStore(allDone), packaged = false)
        assertEquals(RunStatus.FAILED, h.completer.complete(1))
    }

    @Test fun publishFailureOnCleanRunIsFailed() = runBlocking {
        val h = Harness(FakeStore(allDone), publishOk = false)
        assertEquals(RunStatus.FAILED, h.completer.complete(1))
        assertTrue("ci" !in h.calls)
    }

    @Test fun testGenerationErrorDoesNotBlockPush() = runBlocking {
        val calls = mutableListOf<String>()
        val c = RunCompleter(
            runs = FakeStore(allDone),
            packageRun = { true },
            ensureTests = { error("AI çöktü") },
            shouldPublish = { true },
            publish = {
                calls += "publish"
                true
            },
            startCiLoop = { calls += "ci" },
        )
        assertEquals(RunStatus.CI_WAITING, c.complete(1))
        assertEquals(listOf("publish", "ci"), calls)
    }

    @Test fun approvePushOnPartialPublishesAndStartsCi() = runBlocking {
        val h = Harness(FakeStore(oneFailed, status = RunStatus.PARTIAL))
        assertEquals(RunStatus.CI_WAITING, h.completer.approvePush(1))
        assertEquals(RunStatus.CI_WAITING, h.store.status)
        assertEquals(listOf("tests", "publish", "ci"), h.calls)
        assertEquals(1, h.store.history)
    }

    @Test fun approvePushFailureKeepsPartialSoUserCanRetry() = runBlocking {
        val h = Harness(FakeStore(oneFailed, status = RunStatus.PARTIAL), publishOk = false)
        assertEquals(RunStatus.PARTIAL, h.completer.approvePush(1))
        assertEquals(RunStatus.PARTIAL, h.store.status)
    }

    @Test fun approvePushIgnoredUnlessPartial() = runBlocking {
        for (st in listOf(RunStatus.DONE, RunStatus.FAILED, RunStatus.RUNNING, RunStatus.CI_WAITING, null)) {
            val h = Harness(FakeStore(oneFailed, status = st))
            assertNull("$st", h.completer.approvePush(1))
            assertTrue(h.calls.isEmpty())
        }
    }

    @Test fun approvePushWithoutRepoDoesNothing() = runBlocking {
        val h = Harness(FakeStore(oneFailed, status = RunStatus.PARTIAL), repo = false)
        assertNull(h.completer.approvePush(1))
        assertEquals(RunStatus.PARTIAL, h.store.status)
    }
}
