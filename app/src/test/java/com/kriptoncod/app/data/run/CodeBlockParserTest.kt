package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
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
}
