package com.kriptoncod.app.ui.theme

import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.LogoAnimation
import com.kriptoncod.app.ui.components.LogoMood
import com.kriptoncod.app.ui.components.traceFrame
import com.kriptoncod.app.ui.components.variantFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test fun variantsCycleThroughAll() {
        val seen = (0 until 60).map { variantFor(it) }.toSet()
        assertEquals(LogoAnimation.entries.toSet(), seen)
        assertEquals(variantFor(0), variantFor(1))
        assertEquals(variantFor(0), variantFor(LogoAnimation.entries.size * 2))
    }

    @Test fun newVariantsExistWithPositiveLaps() {
        assertTrue(LogoAnimation.entries.size >= 6)
        LogoAnimation.entries.forEach { assertTrue(it.name, it.lapMs > 0) }
        assertTrue(LogoAnimation.entries.containsAll(listOf(LogoAnimation.Twin, LogoAnimation.Pulse, LogoAnimation.Scan)))
    }

    @Test fun traceStartsAndEndsWhole() {
        val a = 10f
        val b = 90f
        val start = traceFrame(0f, a, b)
        val end = traceFrame(1f, a, b)
        assertEquals(a, start.from, 0.001f)
        assertEquals(0f, start.dot, 0.001f)
        assertEquals(a, end.from, 0.001f)
        assertEquals(0f, end.dot, 0.001f)
    }

    @Test fun traceErasesThenRedraws() {
        val a = 10f
        val b = 90f
        assertEquals(b, traceFrame(0.40f, a, b).from, 0.5f)
        assertTrue(traceFrame(0.2f, a, b).from in a..b)
        assertEquals(a, traceFrame(0.80f, a, b).from, 0.5f)
    }
}
