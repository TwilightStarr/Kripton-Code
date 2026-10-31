package com.kriptoncod.app.domain

import com.kriptoncod.app.data.run.AccountSelector
import com.kriptoncod.app.data.run.PromptDispatcher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeProfileTest {

    @After fun resetRuntime() = ModeRuntime.reset()

    @Test fun balancedEqualsLegacyConstants() {
        val b = ModeProfile.BALANCED
        assertEquals(PromptDispatcher.MAX_PARALLEL, b.maxParallel)
        assertEquals(PromptDispatcher.MAX_ATTEMPTS, b.maxAttempts)
        assertEquals(PromptDispatcher.RETRY_BASE_MS, b.retryBaseMs)
        assertEquals(PromptDispatcher.MAX_RETRY_DELAY_MS, b.maxRetryDelayMs)
        assertEquals(PromptDispatcher.NO_ACCOUNT_WAIT_MS, b.noAccountWaitMs)
        assertEquals(AccountSelector.BREAKER_STREAK, b.breakerStreak)
        assertEquals(AccountSelector.BREAKER_COOLDOWN_MS, b.breakerCooldownMs)
        assertEquals(AccountSelector.HEALTH_WINDOW_MS, b.healthWindowMs)
        assertEquals(AccountSelector.UNHEALTHY_BELOW, b.unhealthyBelow, 0.0001)
        assertEquals(12, b.serialMaxFixRounds)
        assertEquals(3, b.serialMaxWarnRounds)
        assertEquals(60, b.serialMaxContinues)
    }

    @Test fun everyBuiltInIsAlreadyWithinLimits() {
        ModeProfile.BUILT_INS.forEach { m ->
            assertTrue(m.name, m.builtIn)
            assertEquals(m.name, m, m.coerced())
        }
        assertEquals(ModeProfile.BUILT_INS.size, ModeProfile.BUILT_INS.map { it.id }.toSet().size)
    }

    @Test fun coercedClampsEveryField() {
        val wild = ModeProfile(
            id = "", name = "  ", maxParallel = 99, maxAttempts = -5, retryBaseSec = 0, maxRetryDelayMin = 9999,
            noAccountWaitMin = 0, breakerStreak = 1, breakerCooldownMin = 0, unhealthyBelowPct = 100,
            healthWindowMin = 0, serialMaxFixRounds = 0, serialMaxWarnRounds = 50, serialMaxContinues = 1,
            serialMaxUnmarkedAsks = 9, serialMaxGitFailures = 1, serialMaxUnauthorized = 0, serialMaxDomFailures = 99,
        ).coerced()
        assertTrue(wild.id.startsWith("u-"))
        assertEquals("Mod", wild.name)
        assertEquals(6, wild.maxParallel)
        assertEquals(1, wild.maxAttempts)
        assertEquals(5, wild.retryBaseSec)
        assertEquals(120, wild.maxRetryDelayMin)
        assertEquals(2, wild.breakerStreak)
        assertEquals(90, wild.unhealthyBelowPct)
        assertEquals(1, wild.serialMaxFixRounds)
        assertEquals(10, wild.serialMaxWarnRounds)
        assertEquals(5, wild.serialMaxContinues)
        assertEquals(5, wild.serialMaxUnmarkedAsks)
        assertEquals(3, wild.serialMaxGitFailures)
        assertEquals(1, wild.serialMaxUnauthorized)
        assertEquals(10, wild.serialMaxDomFailures)
    }

    @Test fun sameSettingsIgnoresNameAndId() {
        val copy = ModeProfile.FAST.copy(id = "x", name = "Benim", description = "d", builtIn = false)
        assertTrue(copy.sameSettingsAs(ModeProfile.FAST))
        assertFalse(copy.copy(maxParallel = 2).sameSettingsAs(ModeProfile.FAST))
    }

    @Test fun listRoundTripDropsBuiltInIdsAndClamps() {
        val mine = ModeProfile(id = "u-1", name = "A", maxParallel = 3)
        val clash = mine.copy(id = ModeProfile.ID_FAST)
        val text = ModeCodec.encodeList(listOf(mine, clash))
        val back = ModeCodec.decodeList(text)
        assertEquals(listOf("u-1"), back.map { it.id })
        assertEquals(3, back.single().maxParallel)
        assertFalse(back.single().builtIn)
    }

    @Test fun corruptStoredTextNeverThrows() {
        assertTrue(ModeCodec.decodeList(null).isEmpty())
        assertTrue(ModeCodec.decodeList("").isEmpty())
        assertTrue(ModeCodec.decodeList("{not json").isEmpty())
        assertTrue(ModeCodec.decodeList("[{\"id\":5}]").isEmpty())
    }

    @Test fun exportThenImportGivesFreshIdsAndKeepsSettings() {
        val src = ModeProfile.PATIENT
        val result = ModeCodec.import(ModeCodec.export(listOf(src)))
        val ok = result as ModeCodec.ImportResult.Ok
        val m = ok.modes.single()
        assertNotEquals(src.id, m.id)
        assertFalse(m.builtIn)
        assertTrue(m.sameSettingsAs(src))
        assertEquals(src.name, m.name)
    }

    @Test fun importAcceptsSingleObjectAndArray() {
        val one = """{"id":"zzz","name":"Tek","maxParallel":2}"""
        assertEquals(2, (ModeCodec.import(one) as ModeCodec.ImportResult.Ok).modes.single().maxParallel)
        val arr = """[{"id":"a","name":"A"},{"id":"b","name":"B"}]"""
        assertEquals(2, (ModeCodec.import(arr) as ModeCodec.ImportResult.Ok).modes.size)
    }

    @Test fun importRejectsGarbage() {
        assertTrue(ModeCodec.import("") is ModeCodec.ImportResult.Invalid)
        assertTrue(ModeCodec.import("merhaba dünya") is ModeCodec.ImportResult.Invalid)
        assertTrue(ModeCodec.import("[]") is ModeCodec.ImportResult.Invalid)
    }

    @Test fun importClampsOutOfRangeValues() {
        val ok = ModeCodec.import("""{"id":"x","name":"Y","maxParallel":500,"maxAttempts":0}""") as ModeCodec.ImportResult.Ok
        assertEquals(6, ok.modes.single().maxParallel)
        assertEquals(1, ok.modes.single().maxAttempts)
    }

    @Test fun runtimeProfileChangesBreakerBehaviour() {
        val t = 1_000_000_000L
        val twoFailures = AccountHealth(
            1,
            emptyList(),
            listOf(HealthEvent(t - 1_000, UsageOutcome.TRANSIENT), HealthEvent(t - 2_000, UsageOutcome.TRANSIENT)),
        )
        // Varsayılan: 3 art arda hata gerekir -> 2 hata devreyi açmaz.
        assertNull(AccountSelector.breakerReopenAt(twoFailures, t))
        // Kota tasarrufu: 2 art arda hata yeter ve dinlendirme 20 dk.
        ModeRuntime.set(ModeProfile.SAVER)
        val reopen = AccountSelector.breakerReopenAt(twoFailures, t)
        assertNotNull(reopen)
        assertEquals(t - 1_000 + 20 * 60_000L, reopen)
    }

    @Test fun runtimeProfileChangesRetryDelay() {
        assertEquals(15_000L, PromptDispatcher.retryDelayMs(1))
        ModeRuntime.set(ModeProfile.FAST)
        assertEquals(8_000L, PromptDispatcher.retryDelayMs(1))
        assertEquals(3 * 60_000L, PromptDispatcher.retryDelayMs(1_000)) // üst sınır 3 dk
    }
}
