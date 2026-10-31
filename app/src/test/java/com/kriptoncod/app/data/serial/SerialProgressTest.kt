package com.kriptoncod.app.data.serial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialProgressTest {
    private fun state(
        index: Int,
        total: Int,
        phase: SerialPhase = SerialPhase.PROMPT,
        status: SerialStatus = SerialStatus.RUNNING,
        fixRound: Int = 0,
    ) = SerialState(
        id = 1, createdAt = 0, name = "n", repo = "o/r", branch = "main", accountId = 1,
        prompts = List(total) { SerialPrompt("p$it", "b") },
        index = index, phase = phase, status = status, fixRound = fixRound,
    )

    @Test fun runningPromptIsNotCountedAsCompleted() {
        val p = SerialProgress.of(state(index = 2, total = 5))
        assertEquals(2, p.completed) // eski gösterim "3 / 5" idi
        assertEquals(3, p.currentNumber)
        assertTrue(p.fraction > 0.4f && p.fraction < 0.5f)
    }

    @Test fun phasesAdvanceWithinOnePrompt() {
        val a = SerialProgress.of(state(0, 4, SerialPhase.PROMPT)).fraction
        val b = SerialProgress.of(state(0, 4, SerialPhase.COMMIT)).fraction
        val c = SerialProgress.of(state(0, 4, SerialPhase.CI)).fraction
        assertTrue(a < b && b < c)
        assertTrue(c < 0.25f) // ilk prompt bitmeden %25'i geçmez
    }

    @Test fun fixRoundsInchForwardButNeverFinishTheStep() {
        val base = SerialProgress.stepFraction(SerialPhase.CI, 0)
        val r3 = SerialProgress.stepFraction(SerialPhase.CI, 3)
        val r40 = SerialProgress.stepFraction(SerialPhase.FIX, 40)
        assertTrue(r3 > base)
        assertTrue(r40 <= 0.95f)
    }

    @Test fun doneIsExactlyOneHundredPercent() {
        val p = SerialProgress.of(state(index = 5, total = 5, status = SerialStatus.DONE))
        assertEquals(1f, p.fraction, 0f)
        assertEquals(5, p.completed)
        assertNull(p.currentNumber)
        assertEquals(100, p.percent)
    }

    @Test fun failedOrWaitingNeverShowsFullBeforeFinishing() {
        val waiting = SerialProgress.of(state(index = 4, total = 5, phase = SerialPhase.CI, status = SerialStatus.WAITING_USER, fixRound = 12))
        assertTrue(waiting.fraction < 1f)
        assertEquals(4, waiting.completed)
    }

    @Test fun emptyPlanIsZero() {
        assertEquals(0f, SerialProgress.of(state(0, 0)).fraction, 0f)
    }
}
