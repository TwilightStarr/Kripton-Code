package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderDetectorTest {
    private fun hits(path: String, content: String) = PlaceholderDetector.find(listOf(CodeBlockParser.Block(path, content)))

    @Test fun flagsEllipsisCommentsAndPhrases() {
        assertEquals(1, hits("A.kt", "fun a() {\n    // ...\n}").size)
        assertEquals(1, hits("a.py", "def a():\n    # rest of the code\n").size)
        assertEquals(1, hits("A.kt", "class A {\n    // geri kalanı aynı\n}").size)
        assertEquals(1, hits("A.kt", "fun f() = TODO(\"implement\")").size)
    }

    @Test fun ordinaryCodeIsNotFlagged() {
        assertTrue(hits("A.kt", "fun a(vararg x: Int) { println(x.size) }\n// returns the sum").isEmpty())
        assertTrue(hits("a.py", "def stub():\n    ...\n").isEmpty())
        assertTrue(hits("README.md", "# Başlık\n...\n").isEmpty())
    }
}

class FlowDirectivesTest {
    @Test fun noDirectivesKeepsTextUntouched() {
        val d = FlowDirectivesParser.parse("Ana sayfayı yaz")
        assertTrue(d.isDefault)
        assertEquals("Ana sayfayı yaz", d.body)
    }

    @Test fun parsesInputsAndShare() {
        val d = FlowDirectivesParser.parse("[[girdi: Ana sayfa, Menü]]\n[[ilet: hayır]]\n\nGörev metni")
        assertEquals(listOf("Ana sayfa", "Menü"), d.inputs)
        assertFalse(d.share)
        assertEquals("Görev metni", d.body)
    }

    @Test fun englishAliasesAndMidTextIsIgnored() {
        val d = FlowDirectivesParser.parse("[[input: A]]\n[[share: no]]\nbody\n[[girdi: X]]")
        assertEquals(listOf("A"), d.inputs)
        assertFalse(d.share)
        assertTrue(d.body.contains("[[girdi: X]]"))
    }
}
