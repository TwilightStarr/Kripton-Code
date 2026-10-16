package com.kriptoncod.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test fun knownKeysRoundTrip() {
        ThemePreference.entries.forEach { assertEquals(it, ThemePreference.fromKey(it.key)) }
    }

    @Test fun unknownOrNullFallsBackToBlack() {
        assertEquals(ThemePreference.BLACK, ThemePreference.fromKey(null))
        assertEquals(ThemePreference.BLACK, ThemePreference.fromKey("neon"))
    }

    @Test fun legacyKeysFallBackToBlack() {
        listOf("system", "light", "dark").forEach { assertEquals(ThemePreference.BLACK, ThemePreference.fromKey(it)) }
    }
}
