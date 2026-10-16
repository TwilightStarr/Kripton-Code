package com.kriptoncod.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test fun knownKeysRoundTrip() {
        ThemePreference.entries.forEach { assertEquals(it, ThemePreference.fromKey(it.key)) }
    }

    @Test fun unknownOrNullFallsBackToGraphite() {
        assertEquals(ThemePreference.GRAPHITE, ThemePreference.fromKey(null))
        assertEquals(ThemePreference.GRAPHITE, ThemePreference.fromKey("neon"))
    }

    @Test fun legacyKeysFallBackToGraphite() {
        listOf("system", "light", "dark").forEach { assertEquals(ThemePreference.GRAPHITE, ThemePreference.fromKey(it)) }
    }
}
