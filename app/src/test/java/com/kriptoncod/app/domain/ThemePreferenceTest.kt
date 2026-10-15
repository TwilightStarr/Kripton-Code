package com.kriptoncod.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test fun knownKeysRoundTrip() {
        ThemePreference.entries.forEach { assertEquals(it, ThemePreference.fromKey(it.key)) }
    }

    @Test fun unknownOrNullFallsBackToSystem() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey("neon"))
    }
}
