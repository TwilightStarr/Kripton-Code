package com.kriptoncod.app.data.run

import com.kriptoncod.app.domain.CiLogParser.CiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CiFixPromptTest {

    @Test fun resolvesAbsoluteLogPathToProjectPathByLongestSuffix() {
        val paths = listOf("Main.kt", "app/src/Main.kt", "lib/Util.kt")
        assertEquals("app/src/Main.kt", CiFixPrompt.resolvePath("/home/runner/work/r/r/app/src/Main.kt", paths))
        assertEquals("Main.kt", CiFixPrompt.resolvePath("/home/runner/work/r/r/Main.kt", paths))
        assertEquals("lib/Util.kt", CiFixPrompt.resolvePath("lib/Util.kt", paths))
        assertNull(CiFixPrompt.resolvePath("/x/Other.kt", paths))
        assertNull(CiFixPrompt.resolvePath("", paths))
    }

    @Test fun promptContainsFailingFileContentAndSummaryOfTheRest() {
        val project = mapOf(
            "src/Calc.kt" to "package x\nclass Calc {\n    fun add(a: Int, b: Int) = a - b\n}\n",
            "src/Other.kt" to "class Other {\n    fun secretBody() {\n        hidden()\n    }\n}\n",
        )
        val errors = listOf(CiError("/home/runner/work/r/r/src/Calc.kt", 3, "wrong result"))
        val p = CiFixPrompt.build(errors, project)
        assertTrue(p.contains("HATALI DOSYANIN GÜNCEL İÇERİĞİ: src/Calc.kt"))
        assertTrue("hatalı dosya tam içerikle", p.contains("fun add(a: Int, b: Int) = a - b"))
        assertTrue(p.contains("wrong result"))
        assertTrue("diğer dosya yalnız özet", p.contains("class Other {") && !p.contains("hidden()"))
    }

    @Test fun errorsWithoutFileStillGetProjectSummary() {
        val p = CiFixPrompt.build(listOf(CiError("", -1, "TEST YOK")), mapOf("a.py" to "def f():\n    return 1\n"))
        assertTrue(p.contains("TEST YOK") && p.contains("def f():"))
        assertFalse(p.contains("HATALI DOSYANIN"))
    }

    @Test fun hugeFileIsWindowedAroundTheErrorLine() {
        val body = (1..5000).joinToString("\n") { "line$it" }
        val out = CiFixPrompt.excerpt(body, 2500, 3000)
        assertTrue(out.length < 3300)
        assertTrue(out.contains("line2500"))
        assertFalse(out.contains("line1\n"))
        assertEquals("kısa", CiFixPrompt.excerpt("kısa", 1, 100))
    }
}
