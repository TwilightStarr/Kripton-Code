package com.kriptoncod.app.ui.fx

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FxControllerTest {
    private val palette = listOf(Color.White, Color.Gray)

    @Test fun startsIdle() {
        assertTrue(FxController().idle)
    }

    @Test fun confettiAddsParticlesAndTheyExpire() {
        val fx = FxController()
        fx.burst(Offset(10f, 10f), FxKind.Confetti, palette)
        assertEquals(26, fx.particleCount)
        assertFalse(fx.idle)
        repeat(200) { fx.step(0.05f) }
        assertTrue(fx.idle)
    }

    @Test fun supernovaAddsThreeWavesAndSparks() {
        val fx = FxController()
        fx.burst(Offset.Zero, FxKind.Supernova, palette)
        assertEquals(3, fx.waveCount)
        assertEquals(62, fx.particleCount)
        repeat(200) { fx.step(0.05f) }
        assertTrue(fx.idle)
    }

    @Test fun fountainAndSparkleSpawn() {
        val fx = FxController()
        fx.burst(Offset.Zero, FxKind.Fountain, palette)
        assertEquals(36, fx.particleCount)
        fx.burst(Offset.Zero, FxKind.Sparkle, palette)
        assertEquals(44, fx.particleCount)
    }

    @Test fun particleCountIsCapped() {
        val fx = FxController()
        repeat(50) { fx.burst(Offset.Zero, FxKind.Supernova, palette) }
        assertTrue(fx.particleCount <= 320)
        assertTrue(fx.waveCount <= 24)
    }

    @Test fun rippleAndPulseAreSingleWaves() {
        val fx = FxController()
        fx.ripple(Offset(5f, 5f), Color.White)
        fx.pulse(Offset(5f, 5f), Color.Red)
        assertEquals(2, fx.waveCount)
        assertEquals(0, fx.particleCount)
    }

    @Test fun emptyPaletteFallsBackToWhite() {
        val fx = FxController()
        fx.burst(Offset.Zero, FxKind.Confetti, emptyList())
        assertEquals(26, fx.particleCount)
    }

    @Test fun everyBurstRestartsTheFrameLoop() {
        val fx = FxController()
        val before = fx.epoch
        fx.burst(Offset.Zero, FxKind.Sparkle, palette)
        assertTrue(fx.epoch > before)
    }

    @Test fun toLocalSubtractsHostOffset() {
        val fx = FxController()
        fx.hostOffset = Offset(10f, 20f)
        assertEquals(Offset(5f, 5f), fx.toLocal(Offset(15f, 25f)))
    }

    @Test fun stepMovesParticles() {
        val fx = FxController()
        fx.burst(Offset(100f, 100f), FxKind.Fountain, palette)
        val before = fx.particles.map { it.y }
        fx.step(0.1f)
        val after = fx.particles.map { it.y }
        assertEquals(before.size, after.size)
        assertTrue(before.zip(after).any { (b, a) -> a != b })
    }
}
