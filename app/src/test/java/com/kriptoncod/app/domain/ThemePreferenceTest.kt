package com.kriptoncod.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test fun onlySystemDarkAndLightRemain() {
        assertEquals(listOf("system", "dark", "light"), ThemePreference.entries.map { it.key })
    }

    @Test fun knownKeysRoundTrip() {
        ThemePreference.entries.forEach { assertEquals(it, ThemePreference.fromKey(it.key)) }
    }

    @Test fun unknownOrNullFollowsSystem() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromKey("neon"))
    }

    @Test fun removedDarkThemesMigrateToDark() {
        listOf("black", "graphite", "sage").forEach { assertEquals(it, ThemePreference.DARK, ThemePreference.fromKey(it)) }
    }
}
