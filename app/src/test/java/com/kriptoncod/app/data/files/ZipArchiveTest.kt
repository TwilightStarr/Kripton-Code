package com.kriptoncod.app.data.files

import com.kriptoncod.app.data.serial.RepoZip
import java.io.File
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ZipArchiveTest {
    private val root: File = Files.createTempDirectory("ziparch").toFile()
    private var clock = 1_000L

    @After fun cleanup() { root.deleteRecursively() }

    private fun archive() = ZipArchive(root) { clock++ }
    private fun zip(vararg f: Pair<String, String>) = RepoZip.toZip(f.associate { it.first to it.second.toByteArray() })

    @Test fun addListBrowseAndSurviveRestart() {
        val a = archive()
        val rec = a.add(ZipArchive.KIND_AI, "1/3: Giriş", "o/r@main", zip("a.kt" to "x", "b/c.kt" to "y"))
        assertNotNull(rec)
        assertEquals(2, rec!!.fileCount)
        assertEquals(listOf("a.kt", "b/c.kt"), a.entries(rec.id).map { it.first })
        // Uygulama yeniden başlayınca kayıtlar diskten gelir.
        assertEquals(1, archive().records.value.size)
    }

    @Test fun identicalContentIsNotArchivedTwice() {
        val a = archive()
        val z = zip("a.kt" to "x")
        a.add(ZipArchive.KIND_REPO, "o/r@main", "GitHub", z)
        a.add(ZipArchive.KIND_REPO, "o/r@main", "GitHub", z)
        assertEquals(1, a.records.value.size)
        a.add(ZipArchive.KIND_REPO, "o/r@main", "GitHub", zip("a.kt" to "değişti"))
        assertEquals(2, a.records.value.size)
    }

    @Test fun newestFirstAndDeleteRemovesFile() {
        val a = archive()
        val r1 = a.add(ZipArchive.KIND_AI, "bir", "", zip("a" to "1"))!!
        val r2 = a.add(ZipArchive.KIND_AI, "iki", "", zip("a" to "2"))!!
        assertEquals(listOf(r2.id, r1.id), a.records.value.map { it.id })
        a.delete(r1.id)
        assertTrue(!a.file(r1.id).exists())
        assertEquals(listOf(r2.id), a.records.value.map { it.id })
    }

    @Test fun oldEntriesArePrunedBeyondLimit() {
        val a = archive()
        repeat(ZipArchive.MAX_ENTRIES + 5) { a.add(ZipArchive.KIND_AI, "t$it", "", zip("a" to "$it")) }
        assertEquals(ZipArchive.MAX_ENTRIES, a.records.value.size)
        assertEquals("t${ZipArchive.MAX_ENTRIES + 4}", a.records.value.first().title)
    }

    @Test fun corruptIndexGivesEmptyListNotCrash() {
        File(root, "zip-archive").mkdirs()
        File(root, "zip-archive/index.json").writeText("{bozuk")
        assertTrue(archive().records.value.isEmpty())
    }
}
