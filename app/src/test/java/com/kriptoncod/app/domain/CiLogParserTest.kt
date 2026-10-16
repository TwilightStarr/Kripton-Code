package com.kriptoncod.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CiLogParserTest {
    @Test fun parsesKotlinJavaAndActionsLines() {
        val log = """
            e: Main.kt: (10, 5) Unresolved reference: foo
            src/App.java:12:3: error: cannot find symbol
            ##[error]Process completed with exit code 1.
        """.trimIndent()
        val errors = CiLogParser.parse(log)
        assertEquals(3, errors.size)
        assertEquals(CiLogParser.CiError("Main.kt", 10, "Unresolved reference: foo"), errors[0])
        assertEquals(CiLogParser.CiError("src/App.java", 12, "cannot find symbol"), errors[1])
        assertEquals(CiLogParser.CiError("", -1, "Process completed with exit code 1."), errors[2])
    }

    @Test fun genericErrorLinesAreKeptAndDeduplicated() {
        val log = "Build FAILED with an exception\nBuild FAILED with an exception\nhepsi yolunda"
        assertEquals(1, CiLogParser.parse(log).size)
        assertTrue(CiLogParser.parse("temiz log").isEmpty())
    }

    @Test fun promptSummaryListsLocations() {
        val text = CiLogParser.toPromptSummary(listOf(CiLogParser.CiError("A.kt", 3, "x"), CiLogParser.CiError("", -1, "y")))
        assertTrue(text.contains("1. [A.kt:3] x"))
        assertTrue(text.contains("2. [genel] y"))
    }
}
