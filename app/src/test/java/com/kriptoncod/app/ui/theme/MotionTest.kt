package com.kriptoncod.app.ui.theme

import com.kriptoncod.app.domain.RunStatus
import com.kriptoncod.app.ui.components.LogoMood
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

    @Test fun durationScalesWithSpeedAndSystemScale() {
        assertEquals(260, motionDurationMs(260, enabled = true, speed = 1f, systemScale = 1f))
        // Hız 2 = iki kat hızlı: süre yarıya; hız 0,5 = süre iki katına.
        assertEquals(130, motionDurationMs(260, true, 2f, 1f))
        assertEquals(520, motionDurationMs(260, true, 0.5f, 1f))
        // Sistem Animatör ölçeği çarpar.
        assertEquals(520, motionDurationMs(260, true, 1f, 2f))
        assertEquals(65, motionDurationMs(260, true, 2f, 0.5f))
    }

    @Test fun durationIsZeroWhenAnimationsAreOff() {
        assertEquals(0, motionDurationMs(260, enabled = false, speed = 1f, systemScale = 1f))
        assertEquals(0, motionDurationMs(260, true, 1f, 0f)) // sistem animasyonları kapalı
        assertEquals(0, motionDurationMs(0, true, 1f, 1f))
        assertEquals(0, motionDurationMs(-5, true, 1f, 1f))
    }

    @Test fun durationToleratesBadInput() {
        // Hız geçerli aralığa çekilir (0,5..2).
        assertEquals(130, motionDurationMs(260, true, 99f, 1f))
        assertEquals(520, motionDurationMs(260, true, 0.01f, 1f))
        assertEquals(260, motionDurationMs(260, true, Float.NaN, 1f))
        assertEquals(260, motionDurationMs(260, true, 1f, Float.NaN))
        // Açıkken en az 1 ms (0 "kapalı" demektir).
        assertTrue(motionDurationMs(1, true, 2f, 0.01f) >= 1)
        // Sistem ölçeği 10 ile sınırlı.
        assertEquals(2600, motionDurationMs(260, true, 1f, 1000f))
    }

    @Test fun motionScaleDelegatesToDuration() {
        assertEquals(Motion.MEDIUM / 2, MotionScale(enabled = true, speed = 2f).duration(Motion.MEDIUM))
        assertEquals(0, MotionScale(enabled = false).duration(Motion.MEDIUM))
        assertEquals(MotionScale(true, 1.5f, 1f), MotionScale(true, 1.5f, 1f))
        assertFalse(MotionScale(true, 1.5f, 1f) == MotionScale(true, 1f, 1f))
        // Tasarım süreleri küçük < orta < uzun.
        assertTrue(Motion.SHORT < Motion.MEDIUM && Motion.MEDIUM < Motion.LONG)
    }
}
