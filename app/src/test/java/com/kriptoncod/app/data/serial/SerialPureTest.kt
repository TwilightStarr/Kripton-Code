package com.kriptoncod.app.data.serial

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SerialPureTest {

    @Test
    fun markers_complete_only_when_end_marker_is_at_the_tail() {
        val n = "ABC234"
        assertTrue(SerialMarkers.isComplete("x\n" + SerialMarkers.end(n), n))
        assertFalse(SerialMarkers.isComplete("x\n" + SerialMarkers.begin(n) + "\nyarım", n))
        assertTrue(SerialMarkers.belongs("a " + SerialMarkers.begin(n), n))
        assertFalse(SerialMarkers.belongs("başka bir yanıt", n))
    }

    @Test
    fun markers_strip_removes_marker_lines_even_when_formatted() {
        val n = "ABC234"
        val text = "**" + SerialMarkers.begin(n) + "**\nkod\n`" + SerialMarkers.end(n) + "`"
        assertEquals("kod", SerialMarkers.strip(text, n))
    }

    @Test
    fun nonce_has_six_safe_characters() {
        val n = SerialMarkers.newNonce { 7 }
        assertEquals(6, n.length)
        assertTrue(n.all { it.isLetterOrDigit() })
    }

    @Test
    fun directives_new_chat_and_no_files_are_removed_from_body() {
        val p = SerialDirectives.parse("[[yeni-sohbet]]\n[[dosyasız]]\n\nMerhaba")
        assertTrue(p.newChat)
        assertFalse(p.expectFiles)
        assertEquals("Merhaba", p.body)
    }

    @Test
    fun directives_default_keeps_same_chat_and_expects_files() {
        val p = SerialDirectives.parse("Sadece bir prompt")
        assertFalse(p.newChat)
        assertTrue(p.expectFiles)
        assertEquals("Sadece bir prompt", p.body)
    }

    @Test
    fun directives_can_be_switched_off() {
        val p = SerialDirectives.parse("[[yeni-sohbet: hayır]]\nMerhaba")
        assertFalse(p.newChat)
    }

    @Test
    fun ci_scan_separates_errors_from_warnings_and_skips_noise() {
        val log = """
            e: Main.kt: (10, 5) Unresolved reference: foo
            w: Util.kt: (3, 4) 'bar' is deprecated
            Warning: Node.js 16 actions are deprecated
            ##[warning]Cache not found for input keys
            BUILD SUCCESSFUL
        """.trimIndent()
        val r = CiLogScan.scanText(log)
        assertEquals(1, r.errors.size)
        assertEquals(1, r.warnings.size)
        assertTrue(r.errors[0].contains("Unresolved reference"))
        assertTrue(r.warnings[0].contains("deprecated"))
    }

    @Test
    fun ci_scan_clean_log_is_clean() {
        assertTrue(CiLogScan.scanText("Run flutter build apk\nBuilt build/app.apk").clean)
    }

    @Test
    fun repo_zip_strips_single_top_folder_and_round_trips() {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z ->
            z.putNextEntry(ZipEntry("owner-repo-abc/a.txt"))
            z.write("A".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("owner-repo-abc/src/b.kt"))
            z.write("B".toByteArray())
            z.closeEntry()
        }
        val p = RepoZip.read(bos.toByteArray())
        assertEquals(setOf("a.txt", "src/b.kt"), p.files.keys)
        val again = RepoZip.read(RepoZip.toZip(p.files))
        assertEquals(setOf("a.txt", "src/b.kt"), again.files.keys)
    }

    @Test
    fun planner_turns_zip_catalogs_into_ordered_steps_with_directives() {
        val cats = listOf(
            com.kriptoncod.app.domain.Catalog(
                "A",
                listOf(
                    com.kriptoncod.app.domain.CatalogPrompt("bir", "Birinci"),
                    com.kriptoncod.app.domain.CatalogPrompt("iki", "[[yeni-sohbet]]\n[[dosyasız]]\nİkinci"),
                ),
            ),
            com.kriptoncod.app.domain.Catalog("B", listOf(com.kriptoncod.app.domain.CatalogPrompt("üç", "Üçüncü"))),
        )
        val plan = SerialPlanner.planCatalogs(cats, nowMs = 0L)
        assertEquals(3, plan.size)
        assertEquals("A · iki", plan[1].title)
        assertTrue(plan[1].newChat)
        assertFalse(plan[1].expectFiles)
        assertEquals("İkinci", plan[1].body)
        assertFalse(plan[2].newChat)
    }
}
