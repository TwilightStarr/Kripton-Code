package com.kriptoncod.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputMarkdownTest {

    @Test fun pathFenceBecomesHeaderPlusLanguageFence() {
        val seg = OutputMarkdown.segments("```lib/main.dart\nvoid main() {}\n```")
        assertEquals(1, seg.size)
        assertTrue(seg[0].startsWith("**`lib/main.dart`**\n\n```dart\n"))
        assertTrue(seg[0].contains("void main() {}"))
        assertTrue(seg[0].endsWith("```"))
    }

    @Test fun proseAndCodeAreSeparateSegments() {
        val seg = OutputMarkdown.segments("# Başlık\nmetin\n\n```kotlin\nval a = 1\n```\nson söz")
        assertEquals(3, seg.size)
        assertEquals("# Başlık\nmetin", seg[0])
        assertTrue(seg[1].startsWith("```kotlin"))
        assertEquals("son söz", seg[2])
    }

    @Test fun infoFormatsAreResolved() {
        assertEquals("a/b.kt" to "kotlin", OutputMarkdown.resolveInfo("kotlin a/b.kt"))
        assertEquals("a/b.kt" to "kotlin", OutputMarkdown.resolveInfo("kotlin:a/b.kt"))
        assertEquals("a/b.kt" to "kotlin", OutputMarkdown.resolveInfo("kotlin title=\"a/b.kt\""))
        assertEquals(null to "kotlin", OutputMarkdown.resolveInfo("kotlin"))
        assertEquals(null to null, OutputMarkdown.resolveInfo(""))
    }

    @Test fun unclosedFenceStillRendersEverything() {
        val seg = OutputMarkdown.segments("```a/b.kt\nfun f() {\n  1")
        assertEquals(1, seg.size)
        assertTrue(seg[0].contains("  1"))
    }

    @Test fun longBlocksAreChunkedWithoutLosingLines() {
        val body = (1..400).joinToString("\n") { "line$it" }
        val seg = OutputMarkdown.segments("```x/y.kt\n$body\n```")
        assertTrue(seg.size > 1)
        val joined = seg.joinToString("\n")
        for (n in listOf(1, 150, 151, 400)) assertTrue(joined.contains("line$n"))
    }

    @Test fun veryLongBlocksAreNotHighlighted() {
        val body = (1..(OutputMarkdown.MAX_HIGHLIGHT_LINES + 10)).joinToString("\n") { "x$it" }
        val seg = OutputMarkdown.segments("```x/y.kt\n$body\n```")
        assertTrue(seg.all { !it.contains("```kotlin") })
    }

    @Test fun innerFencesGetLongerOuterFence() {
        val seg = OutputMarkdown.segments("````docs/R.md\n```kotlin\nval a = 1\n```\n````")
        assertEquals(1, seg.size)
        assertTrue(seg[0].contains("````"))
    }
}
