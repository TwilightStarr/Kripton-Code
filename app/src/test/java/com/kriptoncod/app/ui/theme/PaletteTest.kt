package com.kriptoncod.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/** Tek renkli (gri-beyaz) tema ve AMOLED siyah zeminin sözleşmesi: renk, kontrast, kademe sırası. */
class PaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = max(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Kanallar birbirine çok yakınsa (en çok ~%3 fark) renk gri sayılır. */
    private fun isGrey(c: Color) = abs(c.red - c.green) <= 0.03f && abs(c.green - c.blue) <= 0.03f && abs(c.red - c.blue) <= 0.03f

    private val both = listOf("dark" to Palette.Dark, "light" to Palette.Light)

    @Test fun darkBackgroundIsPureBlackForAmoled() {
        assertEquals(Color.Black, Palette.Dark.background)
        assertTrue(Palette.Dark.isDark)
    }

    @Test fun everyNonErrorColorIsGrey() {
        for ((name, p) in both) {
            val greys = mapOf(
                "background" to p.background,
                "surface" to p.surface,
                "raised" to p.raised,
                "border" to p.border,
                "text" to p.text,
                "muted" to p.muted,
                "accent" to p.accent,
                "onAccent" to p.onAccent,
                "success" to p.success,
                "successFill" to p.successFill,
            )
            greys.forEach { (k, c) -> assertTrue("$name.$k must be grey: $c", isGrey(c)) }
        }
    }

    @Test fun errorIsTheOnlyColourAndStaysRedDominant() {
        for ((name, p) in both) {
            assertTrue("$name danger is red-dominant", p.danger.red > p.danger.green + 0.2f && p.danger.red > p.danger.blue + 0.2f)
        }
    }

    @Test fun noGreenAccentLeft() {
        for ((name, p) in both) {
            listOf(p.accent, p.success, p.successFill).forEach {
                assertTrue("$name has green tint: $it", it.green <= it.red + 0.03f)
            }
        }
    }

    @Test fun darkSurfacesGetLighterStepByStepButStayNearBlack() {
        val d = Palette.Dark
        val l = listOf(d.background, d.surface, d.raised, d.border).map { it.luminance() }
        assertEquals(l.sorted(), l)
        assertEquals(l.distinct().size, l.size)
        assertTrue("surfaces must stay dark", d.raised.luminance() < 0.02f)
    }

    @Test fun textIsReadableOnBackground() {
        for ((name, p) in both) {
            assertTrue("$name text", contrast(p.text, p.background) >= 12f)
            assertTrue("$name text on surface", contrast(p.text, p.surface) >= 12f)
            assertTrue("$name muted", contrast(p.muted, p.background) >= 4.5f)
            assertTrue("$name muted on raised", contrast(p.muted, p.raised) >= 4f)
        }
    }

    @Test fun accentAndSuccessStandOutAndOnAccentIsReadable() {
        for ((name, p) in both) {
            assertTrue("$name accent vs bg", contrast(p.accent, p.background) >= 7f)
            assertTrue("$name success vs bg", contrast(p.success, p.background) >= 7f)
            assertTrue("$name onAccent vs accent", contrast(p.onAccent, p.accent) >= 7f)
            assertTrue("$name danger vs bg", contrast(p.danger, p.background) >= 3f)
        }
    }

    @Test fun borderIsVisibleAgainstRaisedFill() {
        for ((name, p) in both) {
            assertTrue("$name border vs raised", contrast(p.border, p.raised) >= 1.2f)
        }
    }

    @Test fun themeSpecFollowsPreferenceAndSystem() {
        assertEquals(Palette.Dark, com.kriptoncod.app.domain.ThemePreference.DARK.spec(systemDark = false))
        assertEquals(Palette.Light, com.kriptoncod.app.domain.ThemePreference.LIGHT.spec(systemDark = true))
        assertEquals(Palette.Dark, com.kriptoncod.app.domain.ThemePreference.SYSTEM.spec(systemDark = true))
        assertEquals(Palette.Light, com.kriptoncod.app.domain.ThemePreference.SYSTEM.spec(systemDark = false))
    }

    @Test fun kriptonColorsMapSemanticRoles() {
        val k = Palette.Dark.toKriptonColors()
        assertEquals(Palette.Dark.raised, k.nodeFill)
        assertEquals(Palette.Dark.border, k.nodeBorder)
        assertEquals(Palette.Dark.success, k.arrow)
        assertEquals(Palette.Dark.successFill, k.arrowFill)
        assertEquals(Palette.Dark.danger, k.danger)
        assertEquals(Palette.Dark.muted, k.muted)
    }
}
