package com.kriptoncod.app.ui.workspace

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kriptoncod.app.data.web.WebProvider
import com.kriptoncod.app.ui.screens.tint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/** AI sohbet simgeleri (avatar, odaklı pencere çerçevesi) tema gri olsa da renkli kalır. */
class ProviderTintTest {
    private fun saturation(c: Color) = max(c.red, max(c.green, c.blue)) - min(c.red, min(c.green, c.blue))

    @Test fun everyProviderTintIsColourful() {
        WebProvider.entries.forEach {
            assertTrue("${it.name} tint should not be grey", saturation(it.tint()) >= 0.25f)
        }
    }

    @Test fun tintsAreDistinctPerProvider() {
        val tints = WebProvider.entries.map { it.tint() }
        assertEquals(tints.size, tints.distinct().size)
    }

    @Test fun whiteInitialIsReadableOnEveryTint() {
        WebProvider.entries.forEach {
            val contrast = 1.05f / (it.tint().luminance() + 0.05f)
            assertTrue("${it.name} initial contrast $contrast", contrast >= 3f)
        }
    }

    @Test fun tintsAreOpaque() {
        WebProvider.entries.forEach { assertEquals(1f, it.tint().alpha, 0f) }
    }
}
