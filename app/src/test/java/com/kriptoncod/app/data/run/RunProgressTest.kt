package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunProgressTest {
    private var next = 1L
    private fun job(kind: JobKind, state: JobState) = Job(id = next++, runId = 1, promptId = 1, state = state, updatedAt = 0, kind = kind)
    private fun work(state: JobState) = job(JobKind.WORK, state)
    private fun of(status: RunStatus, jobs: List<Job>, created: Long = 0, now: Long = 0) = RunProgress.of(status, jobs, created, now)

    @Test fun onlyPlanNodeIsIndeterminateNotOneHundredPercent() {
        val running = of(RunStatus.RUNNING, listOf(job(JobKind.PLAN, JobState.RUNNING)))
        assertTrue(running.indeterminate)
        assertEquals(0f, running.fraction, 0f)
        assertEquals(RunProgress.Stage.PLANNING, running.stage)
        // Plan bitti ama grafik henüz kurulmadı: eskiden 1/1 = %100 görünürdü.
        val planned = of(RunStatus.RUNNING, listOf(job(JobKind.PLAN, JobState.DONE)))
        assertTrue(planned.indeterminate)
        assertEquals(0f, planned.fraction, 0f)
        assertEquals(0 to 0, RunProgress.counts(listOf(job(JobKind.PLAN, JobState.DONE))))
    }

    @Test fun progressDoesNotDropWhenGraphIsBuilt() {
        val before = of(RunStatus.RUNNING, listOf(job(JobKind.PLAN, JobState.DONE))).fraction
        val after = of(
            RunStatus.RUNNING,
            listOf(job(JobKind.PLAN, JobState.DONE)) + List(10) { work(JobState.PENDING) } + job(JobKind.FINAL_MERGE, JobState.PENDING),
        )
        assertTrue(after.fraction >= before)
        assertEquals(RunProgress.PLAN_SHARE, after.fraction, 0.0001f)
        assertFalse(after.indeterminate)
    }

    @Test fun mergeNodesWeighLessThanWorkNodes() {
        val jobs = listOf(
            job(JobKind.PLAN, JobState.DONE),
            work(JobState.DONE), work(JobState.PENDING), work(JobState.PENDING), work(JobState.PENDING),
            job(JobKind.FINAL_MERGE, JobState.PENDING),
        )
        // 1 / (4 + 0.25) = 0.2352...
        val p = of(RunStatus.RUNNING, jobs)
        val expected = RunProgress.PLAN_SHARE + RunProgress.NODES_SHARE * (1.0f / 4.25f)
        assertEquals(expected, p.fraction, 0.001f)
        assertEquals(1, p.done)
        assertEquals(5, p.total)
    }

    @Test fun failedNodeIsNotCountedAsDoneAndPartialNeverReachesFull() {
        val jobs = listOf(job(JobKind.PLAN, JobState.DONE), work(JobState.DONE), work(JobState.FAILED), job(JobKind.FINAL_MERGE, JobState.DONE))
        val p = of(RunStatus.PARTIAL, jobs)
        assertEquals(1, p.failed)
        assertEquals(RunProgress.Stage.PARTIAL, p.stage)
        assertTrue(p.fraction < 1f)
    }

    @Test fun pushCiAndFixOccupyTheLastSlice() {
        val done = listOf(job(JobKind.PLAN, JobState.DONE), work(JobState.DONE), job(JobKind.FINAL_MERGE, JobState.DONE))
        val nodes = of(RunStatus.RUNNING, done).fraction
        val push = of(RunStatus.PUSHING, done).fraction
        val ci = of(RunStatus.CI_WAITING, done).fraction
        assertEquals(RunProgress.PLAN_SHARE + RunProgress.NODES_SHARE, nodes, 0.0001f)
        assertTrue(push > nodes)
        assertTrue(ci > push)
        assertEquals(1f, of(RunStatus.DONE, done).fraction, 0f)
        assertEquals(RunProgress.Stage.CI, of(RunStatus.CI_WAITING, done).stage)
    }

    @Test fun etaNeedsEnoughFinishedNodes() {
        val one = listOf(job(JobKind.PLAN, JobState.DONE), work(JobState.DONE), work(JobState.PENDING), work(JobState.PENDING))
        assertNull(of(RunStatus.RUNNING, one, created = 0, now = 60_000).etaMs)
        val two = listOf(job(JobKind.PLAN, JobState.DONE), work(JobState.DONE), work(JobState.DONE), work(JobState.PENDING), work(JobState.PENDING))
        val eta = of(RunStatus.RUNNING, two, created = 0, now = 120_000).etaMs
        assertNotNull(eta)
        assertEquals(120_000L, eta) // 2 düğüm 120 sn -> kalan 2 düğüm 120 sn
        assertNull(of(RunStatus.RUNNING, two, created = 0, now = 0).etaMs)
    }

    @Test fun countsMatchGraphNodesOnly() {
        val jobs = listOf(job(JobKind.PLAN, JobState.DONE), work(JobState.DONE), work(JobState.RUNNING))
        assertEquals(1 to 2, RunProgress.counts(jobs))
    }
}
