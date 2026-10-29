package com.kriptoncod.app.data.files

import java.io.File
import java.util.zip.ZipInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RawReplyLogTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun log() = RawReplyLog(File(tmp.root, "raw"))

    private val reply = "Giriş yazısı\n\n```src/A.kt\nfun a() = 1\n```\n\nKapanış yazısı"

    @Test
    fun raw_reply_is_written_in_full_before_anything_else() {
        val l = log()
        val f = l.save("t", reply)
        assertNotNull(f)
        assertEquals(reply, f!!.readText())
        assertEquals(1, l.pending("t"))
    }

    @Test
    fun blank_reply_is_not_stored() {
        assertNull(log().save("t", "   \n"))
    }

    @Test
    fun compact_keeps_only_code_and_removes_the_raw_file() {
        val l = log()
        l.save("t", reply)
        assertEquals(1, l.compact("t"))
        assertEquals(0, l.pending("t"))
        val files = l.files("t")
        assertEquals(1, files.size)
        val text = files[0].readText()
        assertTrue(text.contains("fun a() = 1"))
        assertTrue(!text.contains("Giriş yazısı"))
    }

    @Test
    fun reply_without_code_is_dropped_on_compact() {
        val l = log()
        l.save("t", "Sadece sohbet, kod yok.")
        l.compact("t")
        assertTrue(l.files("t").isEmpty())
    }

    @Test
    fun prose_is_kept_when_the_text_itself_is_the_output() {
        val l = log()
        l.save("t", "Bu bir plan metnidir.")
        l.compact("t", keepProse = true)
        assertEquals("Bu bir plan metnidir.", l.files("t").single().readText())
    }

    @Test
    fun bundle_contains_original_files_and_extracted_code() {
        val l = log()
        l.save("t", reply)
        l.saveFile("t", "out.zip", byteArrayOf(1, 2, 3))
        l.compact("t")
        val names = ArrayList<String>()
        ZipInputStream(l.bundle("t")!!.inputStream()).use { z ->
            while (true) names += (z.nextEntry ?: break).name
        }
        assertTrue(names.any { it == "files/out.zip" })
        assertTrue(names.any { it.endsWith(".code.md") })
    }

    @Test
    fun delete_removes_everything_for_the_tag() {
        val l = log()
        l.save("t", reply)
        l.delete("t")
        assertTrue(l.files("t").isEmpty())
    }
}
