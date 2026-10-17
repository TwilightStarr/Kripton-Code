package com.kriptoncod.app.ui.theme

import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.lineVisibility
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

    @Test fun noGapMeansFullyVisible() {
        listOf(0f, 90f, 180f, 359f).forEach { assertEquals(1f, lineVisibility(it, gap = 0f, fade = 0f), 0f) }
    }

    @Test fun lineIsErasedRightBehindTheDot() {
        assertEquals(0f, lineVisibility(0f, gap = 80f, fade = 50f), 0f)
        assertEquals(0f, lineVisibility(79f, gap = 80f, fade = 50f), 0f)
    }

    @Test fun lineComesBackSoftlyBehindTheGap() {
        val a = lineVisibility(90f, gap = 80f, fade = 50f)
        val b = lineVisibility(110f, gap = 80f, fade = 50f)
        assertTrue(a in 0f..1f && b in 0f..1f)
        assertTrue("görünürlük arkaya doğru artmalı", b > a)
        assertEquals(1f, lineVisibility(200f, gap = 80f, fade = 50f), 0f)
    }

    @Test fun lineFadesOutJustAheadOfTheDot() {
        val near = lineVisibility(355f, gap = 80f, fade = 50f)
        val far = lineVisibility(320f, gap = 80f, fade = 50f)
        assertTrue(near < far)
        assertEquals(1f, lineVisibility(250f, gap = 80f, fade = 50f), 0f)
    }
}
