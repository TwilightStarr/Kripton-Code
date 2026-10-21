package com.kriptoncod.app.work

import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.work.RunControl.Action
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RunControlTest {
    @Test fun pauseOnlyWhileDispatching() {
        assertTrue(RunControl.isApplicable(Action.PAUSE, RunStatus.RUNNING))
        assertTrue(RunControl.isApplicable(Action.PAUSE, RunStatus.PREPARING))
        assertFalse(RunControl.isApplicable(Action.PAUSE, RunStatus.PAUSED))
        assertFalse(RunControl.isApplicable(Action.PAUSE, RunStatus.DONE))
        assertFalse(RunControl.isApplicable(Action.PAUSE, null))
    }

    @Test fun resumeOnlyWhenPaused() {
        assertTrue(RunControl.isApplicable(Action.RESUME, RunStatus.PAUSED))
        RunStatus.entries.filter { it != RunStatus.PAUSED }
            .forEach { assertFalse("$it", RunControl.isApplicable(Action.RESUME, it)) }
    }

    @Test fun cancelAnyUnfinishedRunButNeverFinishedOnes() {
        listOf(RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING, RunStatus.PAUSED)
            .forEach { assertTrue("$it", RunControl.isApplicable(Action.CANCEL, it)) }
        listOf(RunStatus.DONE, RunStatus.PARTIAL, RunStatus.FAILED, RunStatus.CANCELLED)
            .forEach { assertFalse("$it", RunControl.isApplicable(Action.CANCEL, it)) }
    }

    @Test fun everyStatusIsEitherActiveOrFinished() {
        val finished = setOf(RunStatus.DONE, RunStatus.PARTIAL, RunStatus.FAILED, RunStatus.CANCELLED)
        assertEquals(RunStatus.entries.toSet(), RunControl.ACTIVE + finished)
    }
}
