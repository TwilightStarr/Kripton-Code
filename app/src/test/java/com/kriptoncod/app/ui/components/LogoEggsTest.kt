package com.kriptoncod.app.ui.components

import com.kriptoncod.app.ui.fx.FxKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LogoEggsTest {
    @Test fun slowTapsCycleThroughTheCalmTrio() {
        val s = LogoEggState()
        var t = 0L
        val picks = (0 until 4).map {
            t += 2_000
            s.nextTapEgg(t)
        }
        assertEquals(listOf(LogoEgg.Boop, LogoEgg.Blink, LogoEgg.Hop, LogoEgg.Boop), picks)
    }

    @Test fun rapidTapsWalkTheWholeChainThenReset() {
        val s = LogoEggState()
        var t = 1_000L
        val picks = mutableListOf<LogoEgg>()
        repeat(7) {
            picks += s.nextTapEgg(t)
            t += 300
        }
        assertEquals(LogoEgg.Boop, picks.first())
        assertEquals(CHAIN_EGGS, picks.drop(1))
        assertEquals(LogoEgg.Supernova, picks.last())
        // Final sonrası zincir sıfırlanır: bir sonraki dokunuş yeniden sakin üçlüden başlar.
        assertEquals(LogoEgg.Blink, s.nextTapEgg(t))
    }

    @Test fun aPauseBreaksTheChain() {
        val s = LogoEggState()
        assertEquals(LogoEgg.Boop, s.nextTapEgg(1_000))
        assertEquals(LogoEgg.Spin, s.nextTapEgg(1_300))
        assertEquals(LogoEgg.Blink, s.nextTapEgg(1_300 + TAP_CHAIN_MS + 1))
    }

    @Test fun eggForTapClampsBeyondTheChain() {
        assertEquals(LogoEgg.Supernova, eggForTap(99, 0))
        assertEquals(LogoEgg.Spin, eggForTap(2, 2))
        assertEquals(LogoEgg.Hop, eggForTap(1, 2))
        assertEquals(LogoEgg.Boop, eggForTap(1, 3))
    }

    @Test fun everyEggHasADurationAndEffectsFireInsideIt() {
        LogoEgg.entries.forEach { egg ->
            assertTrue(egg.name, egg.durationMs > 0)
            if (egg.fxKind != null) assertTrue(egg.name, egg.fxDelayMs in 1 until egg.durationMs)
        }
        assertEquals(FxKind.Confetti, LogoEgg.Confetti.fxKind)
        assertEquals(FxKind.Supernova, LogoEgg.Supernova.fxKind)
        assertNull(LogoEgg.Boop.fxKind)
    }

    @Test fun peekIsClampedAndReleasesToZero() {
        val s = LogoEggState()
        s.peekTo(100f, 0f)
        assertTrue(s.lookX in 0f..11.001f)
        assertEquals(0f, s.lookY, 0.001f)
        s.peekTo(0f, 0f)
        assertEquals(0f, s.lookX, 0.001f)
        assertNull(s.current)
        assertNotNull(s.progress)
    }
}
