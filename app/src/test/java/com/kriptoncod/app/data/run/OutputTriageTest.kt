package com.kriptoncod.app.data.run

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputTriageTest {

    private val noisy = """
        Tamam, işte dosyalar. Önce bir giriş yazısı, sonra uzun açıklamalar...
        ${"gürültü ".repeat(200)}

        ```src/A.kt
        fun a() = 1
        ```

        Şimdi B dosyası:

        ```src/B.kt
        fun b() = 2
        ```

        Sonuç: hepsi bu kadar.
    """.trimIndent()

    @Test
    fun noise_is_dropped_but_every_file_block_survives() {
        val t = OutputTriage.triage(noisy)
        assertEquals(2, t.rawBlocks)
        assertEquals(2, t.keptBlocks)
        assertEquals(0, t.droppedBlocks)
        assertTrue(t.useful.contains("fun a() = 1"))
        assertTrue(t.useful.contains("fun b() = 2"))
        assertFalse(t.useful.contains("gürültü"))
        assertTrue(t.usefulChars < t.rawChars / 2)
    }

    @Test
    fun superseded_versions_leave_the_working_text_but_stay_in_the_archive() {
        val raw = """
            ```src/A.kt
            fun a() = 1 // eski sürüm
            ```

            ```src/A.kt
            fun a() = 1 // yeni ve daha uzun sürüm
            fun extra() = 2
            ```
        """.trimIndent()
        val t = OutputTriage.triage(raw)
        assertEquals(1, t.keptBlocks)
        assertEquals(1, t.droppedBlocks)
        assertTrue(t.useful.contains("yeni ve daha uzun"))
        // Elenen sürüm de arşivde: kod hiçbir koşulda silinmez.
        assertTrue(t.archive.contains("eski sürüm"))
        assertTrue(t.archive.contains("yeni ve daha uzun"))
    }

    @Test
    fun reply_without_blocks_is_returned_as_is() {
        val t = OutputTriage.triage("Sadece düz bir plan metni.")
        assertEquals("Sadece düz bir plan metni.", t.useful)
        assertEquals("", t.archive)
    }

    @Test
    fun code_only_keeps_content_verbatim() {
        val code = OutputTriage.codeOnly(noisy)
        assertTrue(code.contains("fun a() = 1"))
        assertTrue(code.contains("fun b() = 2"))
        assertFalse(code.contains("gürültü"))
        assertEquals(2, OutputTriage.blockCount(code))
    }
}
