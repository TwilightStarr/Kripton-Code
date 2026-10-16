package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathSafetyTest {
    private fun safe(raw: String) = (PathSafety.validate(raw) as PathSafety.Result.Safe).normalized
    private fun rejected(raw: String) = PathSafety.validate(raw) is PathSafety.Result.Rejected

    @Test fun normalizesLeadingSlashAndDot() {
        assertEquals("src/Main.kt", safe("/src/Main.kt"))
        assertEquals("src/Main.kt", safe("./src/Main.kt"))
        assertEquals("a-b_c.txt", safe(" a-b_c.txt "))
    }

    @Test fun rejectsTraversalAndOddCharacters() {
        assertTrue(rejected(""))
        assertTrue(rejected("../x"))
        assertTrue(rejected("a/../b"))
        assertTrue(rejected("a/.."))
        assertTrue(rejected("a\\b"))
        assertTrue(rejected("a:b"))
        assertTrue(rejected("a b.txt"))
        assertTrue(rejected("a//b"))
        assertTrue(rejected("a\u0000b"))
    }

    @Test fun extension() {
        assertEquals("kt", PathSafety.extensionOf("a/b.kt"))
        assertEquals("", PathSafety.extensionOf("Makefile"))
    }
}
