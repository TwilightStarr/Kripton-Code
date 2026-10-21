package com.kriptoncod.app.work

import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.work.RunWatchdog.Action
import com.kriptoncod.app.work.RunWatchdog.RunView
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunWatchdogTest {
    private fun r(id: Long, s: RunStatus) = RunView(id, s)

    @Test fun restartsDispatchOnlyForUnchainedDispatchingRuns() {
        val runs = listOf(r(1, RunStatus.RUNNING), r(2, RunStatus.RUNNING), r(3, RunStatus.PREPARING), r(4, RunStatus.PUSHING))
        val actions = RunWatchdog.plan(runs, activeDispatch = setOf(2L), activeCi = emptySet())
        assertEquals(listOf<Action>(Action.RestartDispatch(1), Action.RestartDispatch(3), Action.RestartDispatch(4)), actions)
    }

    @Test fun restartsCiLoopForUnchainedCiRuns() {
        val runs = listOf(r(1, RunStatus.CI_WAITING), r(2, RunStatus.FIXING), r(3, RunStatus.CI_WAITING))
        val actions = RunWatchdog.plan(runs, activeDispatch = emptySet(), activeCi = setOf(3L))
        assertEquals(listOf<Action>(Action.RestartCi(1), Action.RestartCi(2)), actions)
    }

    @Test fun neverTouchesPausedFinishedOrPartialRuns() {
        val runs = listOf(
            r(1, RunStatus.PAUSED),
            r(2, RunStatus.DONE),
            r(3, RunStatus.FAILED),
            r(4, RunStatus.CANCELLED),
            r(5, RunStatus.PARTIAL),
        )
        assertTrue(RunWatchdog.plan(runs, emptySet(), emptySet()).isEmpty())
    }

    @Test fun sweepAppliesActionsAndSkipsHealthyChains() = runBlocking {
        val runs = listOf(r(1, RunStatus.RUNNING), r(2, RunStatus.RUNNING), r(3, RunStatus.CI_WAITING), r(4, RunStatus.CI_WAITING))
        val dispatched = mutableListOf<Long>()
        val ci = mutableListOf<Long>()
        val actions = RunWatchdog.sweep(
            runs,
            isDispatchActive = { it == 2L },
            isCiActive = { it == 4L },
            restartDispatch = { dispatched += it },
            restartCi = { ci += it },
        )
        assertEquals(listOf(1L), dispatched)
        assertEquals(listOf(3L), ci)
        assertEquals(2, actions.size)
    }
}
