package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeBlockParserTest {
    @Test fun parsesPathFencedBlocksInOrder() {
        val text = """
            Açıklama
            ```app/Main.kt
            fun main() {
                println("a")
            }
            ```
            ara metin
            ```README.md
            # Başlık
            ```
        """.trimIndent()
        val blocks = CodeBlockParser.parse(text)
        assertEquals(listOf("app/Main.kt", "README.md"), blocks.map { it.path })
        assertEquals("fun main() {\n    println(\"a\")\n}", blocks[0].content)
        assertEquals("# Başlık", blocks[1].content)
    }

    @Test fun noFenceMeansNoBlocks() {
        assertTrue(CodeBlockParser.parse("sadece düz metin").isEmpty())
    }

    @Test fun emptyBlockIsDropped() {
        assertTrue(CodeBlockParser.parse("```a.txt\n```").isEmpty())
    }

    // ---- sağlayıcıya özgü çit biçimleri

    private fun one(text: String): CodeBlockParser.ParsedBlock = CodeBlockParser.analyze(text).blocks.single()

    @Test fun languagePlusPathForms() {
        assertEquals("app/Main.kt", one("```kotlin app/Main.kt\nfun a()\n```").path)
        assertEquals("kotlin", one("```kotlin app/Main.kt\nfun a()\n```").language)
        assertEquals("app/Main.kt", one("```kotlin:app/Main.kt\nfun a()\n```").path)
        assertEquals("app/Main.kt", one("```kotlin title=\"app/Main.kt\"\nfun a()\n```").path)
        assertEquals("app/Main.kt", one("```kotlin {file=app/Main.kt}\nfun a()\n```").path)
    }

    @Test fun languageOnlyBlockIsNotAFile() {
        val text = "Aşağıdaki kod:\n```kotlin\nfun a()\n```"
        assertTrue(CodeBlockParser.parse(text).isEmpty())
        val b = one(text)
        assertNull(b.path)
        assertEquals("kotlin", b.language)
    }

    @Test fun firstLineCommentLabelsTheFileAndIsRemoved() {
        val b = one("```kotlin\n// File: app/Main.kt\nfun a()\n```")
        assertEquals("app/Main.kt", b.path)
        assertEquals("fun a()", b.content)
        assertEquals("app/Main.kt", one("```kotlin\n// app/Main.kt\nfun a()\n```").path)
    }

    @Test fun ambiguousFirstLineCommentOrShebangIsNotAPath() {
        val py = one("```python\n# main.py\nprint(1)\n```")
        assertNull(py.path)
        assertEquals("# main.py\nprint(1)", py.content)
        val sh = one("```bash\n#!/bin/sh\necho 1\n```")
        assertNull(sh.path)
        assertEquals("#!/bin/sh\necho 1", sh.content)
    }

    @Test fun precedingLineLabelsTheFile() {
        assertEquals("app/Main.kt", one("**File: `app/Main.kt`**\n```kotlin\nfun a()\n```").path)
        assertEquals("app/Main.kt", one("### app/Main.kt\n```kotlin\nfun a()\n```").path)
        assertEquals("1", CodeBlockParser.parse("Dosya: x.txt\n```text\n1\n```").single().content)
        assertNull(one("Şu dosyayı oluştur:\n```kotlin\nfun a()\n```").path)
    }

    @Test fun longerFenceKeepsInnerFencesAsContent() {
        val text = "````README.md\n# Başlık\n```bash\n./gradlew build\n```\n````"
        assertEquals("# Başlık\n```bash\n./gradlew build\n```", CodeBlockParser.parse(text).single().content)
        val mismatched = "````a.txt\nx\n```\ny\n````"
        assertEquals("x\n```\ny", CodeBlockParser.parse(mismatched).single().content)
    }

    @Test fun markdownBlocksBalanceNestedFences() {
        val text = "```README.md\n# Başlık\n```bash\n./gradlew build\n```\nSon\n```\n```app/A.kt\nfun a()\n```"
        val blocks = CodeBlockParser.parse(text)
        assertEquals(listOf("README.md", "app/A.kt"), blocks.map { it.path })
        assertEquals("# Başlık\n```bash\n./gradlew build\n```\nSon", blocks[0].content)
    }

    @Test fun tildeIndentedAndCrlfFencesWork() {
        assertEquals("a.txt", CodeBlockParser.parse("~~~a.txt\nx\n~~~").single().path)
        assertEquals("a.txt", CodeBlockParser.parse("  ```a.txt\n  x\n  ```").single().path)
        assertEquals("x\ny", CodeBlockParser.parse("```a.txt\r\nx\r\ny\r\n```").single().content)
        assertTrue(CodeBlockParser.parse("```a```").isEmpty())
    }

    @Test fun unclosedFenceIsReported() {
        val open = CodeBlockParser.analyze("```a.txt\nyarım")
        assertTrue(open.unclosedFence)
        assertEquals("yarım", CodeBlockParser.parse("```a.txt\nyarım").single().content)
        assertFalse(CodeBlockParser.analyze("```a.txt\ntam\n```").unclosedFence)
        assertFalse(CodeBlockParser.analyze("düz metin").unclosedFence)
    }
}
