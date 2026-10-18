package com.kriptoncod.app.widget

import com.kriptoncod.app.data.db.Job
import com.kriptoncod.app.data.db.RunRecord
import com.kriptoncod.app.domain.JobKind
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.domain.RunStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RunWidgetDataTest {
    private fun run(id: Long, status: RunStatus) = RunRecord(id = id, createdAt = id, status = status)
    private fun job(id: Long, state: JobState, title: String? = null, kind: JobKind = JobKind.WORK) =
        Job(id = id, runId = 1, promptId = id, state = state, updatedAt = 0, kind = kind, title = title)

    @Test fun picksNewestActiveRun() {
        val runs = listOf(run(5, RunStatus.DONE), run(4, RunStatus.PAUSED), run(3, RunStatus.RUNNING))
        assertEquals(4L, RunWidgetData.pickActive(runs)?.id)
    }

    @Test fun noActiveRunMeansNull() {
        assertNull(RunWidgetData.pickActive(listOf(run(2, RunStatus.DONE), run(1, RunStatus.FAILED), run(0, RunStatus.CANCELLED))))
        assertNull(RunWidgetData.pickActive(emptyList()))
    }

    @Test fun summarizesProgressAndCurrentNode() {
        val s = RunWidgetData.summarize(
            run(1, RunStatus.RUNNING),
            listOf(job(1, JobState.DONE), job(2, JobState.RUNNING, "Giriş ekranı"), job(3, JobState.PENDING), job(4, JobState.PENDING)),
        )
        assertEquals(1, s.done)
        assertEquals(4, s.total)
        assertEquals("Giriş ekranı", s.nodeTitle)
        assertEquals(0.25f, s.progress, 0.0001f)
        assertTrue(s.canPause)
        assertFalse(s.canResume)
        assertTrue(s.canCancel)
    }

    @Test fun emptyJobListHasZeroProgress() {
        val s = RunWidgetData.summarize(run(1, RunStatus.PREPARING), emptyList())
        assertEquals(0f, s.progress, 0f)
        assertNull(s.nodeTitle)
    }

    @Test fun pausedRunOffersResumeNotPause() {
        val s = RunWidgetData.summarize(run(1, RunStatus.PAUSED), listOf(job(1, JobState.DONE)))
        assertFalse(s.canPause)
        assertTrue(s.canResume)
        assertTrue(s.canCancel)
        assertEquals(1f, s.progress, 0f)
    }
}
