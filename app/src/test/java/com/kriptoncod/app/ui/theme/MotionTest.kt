package com.kriptoncod.app.ui.theme

import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.LogoMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MotionTest {
    @Test fun inProgressStatusesAreExactlyTheActiveOnes() {
        val active = setOf(RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING)
        RunStatus.entries.forEach { assertEquals(it.name, it in active, it.isInProgress()) }
        assertFalse(RunStatus.PAUSED.isInProgress())
    }

    @Test fun moodFollowsStatus() {
        assertEquals(LogoMood.Working, RunStatus.RUNNING.logoMood())
        assertEquals(LogoMood.Waiting, RunStatus.PAUSED.logoMood())
        assertEquals(LogoMood.Success, RunStatus.DONE.logoMood())
        assertEquals(LogoMood.Error, RunStatus.FAILED.logoMood())
        RunStatus.entries.filter { it.isInProgress() }.forEach { assertEquals(LogoMood.Working, it.logoMood()) }
    }
}
