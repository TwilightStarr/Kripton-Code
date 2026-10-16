package com.kriptoncod.app.data.github

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CiLogSummarizerTest {

    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun extractsErrorLinesWithFileNameAndDropsNoise() {
        val z = zip(
            "verify/1_Set up job.txt" to "2026-10-15T10:00:00.0000000Z Current runner version\n2026-10-15T10:00:01.0000000Z ok",
            "verify/2_Verify.txt" to buildString {
                appendLine("2026-10-15T10:00:02.0000000Z running tests")
                appendLine("2026-10-15T10:00:03.0000000Z \u001B[31mError: cannot find symbol\u001B[0m")
                appendLine("2026-10-15T10:00:04.0000000Z ##[error]Process completed with exit code 1.")
            },
        )
        val s = CiLogSummarizer.summarize(z)
        assertTrue(s.contains("[verify/2_Verify] ##[error]Process completed with exit code 1."))
        assertTrue(s.contains("Error: cannot find symbol"))
        assertFalse(s.contains("Current runner version"))
        assertFalse(s.contains("2026-10-15T"))
        assertFalse(s.contains("\u001B"))
        // Kesin eşleşme zayıf eşleşmeden önce gelir.
        assertTrue(s.indexOf("##[error]") < s.indexOf("cannot find symbol"))
    }

    @Test fun redactsSecretsInSummary() {
        val pat = "ghp_" + "c".repeat(36)
        val z = zip("a.txt" to "##[error]auth failed for $pat and Bearer abcdefghijklmnopqrstuvwxyz012345")
        val s = CiLogSummarizer.summarize(z)
        assertFalse(s.contains(pat))
        assertFalse(s.contains("abcdefghijklmnopqrstuvwxyz012345"))
        assertTrue(s.contains("[REDACTED]"))
    }

    @Test fun fallsBackToTailWhenNoErrorLines() {
        val lines = (1..100).joinToString("\n") { "line $it" }
        val s = CiLogSummarizer.summarize(zip("a.txt" to lines))
        assertTrue(s.contains("line 100"))
        assertTrue(s.contains("line 1\n"))
        assertEquals(100, s.lines().size)
    }

    @Test fun truncatesToMaxChars() {
        val z = zip("a.txt" to (1..500).joinToString("\n") { "##[error]problem number $it" })
        val s = CiLogSummarizer.summarize(z, maxChars = 200)
        assertTrue(s.length <= 202)
        assertTrue(s.endsWith("…"))
    }

    @Test fun garbageInputGivesEmptySummary() {
        assertEquals("", CiLogSummarizer.summarize(byteArrayOf(1, 2, 3, 4)))
        assertEquals("", CiLogSummarizer.summarize(ByteArray(0)))
    }
}
