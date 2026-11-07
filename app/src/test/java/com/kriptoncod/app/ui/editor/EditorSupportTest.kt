package com.kriptoncod.app.ui.editor

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorSupportTest {
    @Test fun scopeFollowsExtension() {
        assertEquals("source.kotlin", EditorSupport.scopeFor("Main.kt"))
        assertEquals("source.kotlin", EditorSupport.scopeFor("build.gradle.kts"))
        assertEquals("source.json", EditorSupport.scopeFor("DATA.JSON"))
        assertEquals("source.yaml", EditorSupport.scopeFor("ci.yml"))
        assertEquals("source.shell", EditorSupport.scopeFor(".gitignore"))
        assertEquals("text.xml", EditorSupport.scopeFor("AndroidManifest.xml"))
        assertNull(EditorSupport.scopeFor("notes.txt"))
        assertNull(EditorSupport.scopeFor("Makefile"))
    }

    @Test fun languageLabelFallsBackToText() {
        assertEquals("Kotlin", EditorSupport.languageLabel("source.kotlin"))
        assertEquals("Text", EditorSupport.languageLabel(null))
    }

    @Test fun binaryDetectionUsesNul() {
        assertTrue(EditorSupport.looksBinary(byteArrayOf(65, 0, 66)))
        assertFalse(EditorSupport.looksBinary("merhaba dünya".toByteArray()))
    }

    @Test fun decodeKeepsBomAndRoundTrips() {
        val original = "çğüş\r\nsatır2\n"
        val bytes = EditorSupport.encode(original, bom = true)
        val d = EditorSupport.decode(bytes)
        assertTrue(d.bom)
        assertFalse(d.lossy)
        assertEquals(original, d.text)
        assertArrayEquals(bytes, EditorSupport.encode(d.text, d.bom))
    }

    @Test fun invalidUtf8IsMarkedLossy() {
        val d = EditorSupport.decode(byteArrayOf(0x68, 0xFF.toByte(), 0x69))
        assertTrue(d.lossy)
    }

    @Test fun readTruncatesAndMarksUnsafe() {
        val f = File.createTempFile("edit", ".txt")
        try {
            f.writeText("a".repeat(100))
            val small = EditorSupport.read(f, max = 40)
            assertTrue(small.truncated)
            assertFalse(small.safeToWrite)
            assertEquals(40, small.text.length)
            val full = EditorSupport.read(f)
            assertFalse(full.truncated)
            assertTrue(full.safeToWrite)
        } finally {
            f.delete()
        }
    }

    @Test fun readFlagsBinaryFiles() {
        val f = File.createTempFile("edit", ".bin")
        try {
            f.writeBytes(byteArrayOf(1, 2, 0, 3))
            val l = EditorSupport.read(f)
            assertTrue(l.binary)
            assertFalse(l.safeToWrite)
        } finally {
            f.delete()
        }
    }

    @Test fun settingsAreClamped() {
        val n = EditorSettings(fontSizeSp = 100f, tabWidth = 0).normalized()
        assertEquals(EditorSettings.MAX_FONT, n.fontSizeSp)
        assertEquals(1, n.tabWidth)
        assertEquals(EditorSettings.MIN_FONT, EditorSettings(fontSizeSp = 1f).normalized().fontSizeSp)
    }
}
