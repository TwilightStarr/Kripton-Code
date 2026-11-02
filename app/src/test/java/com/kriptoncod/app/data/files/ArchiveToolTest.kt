package com.kriptoncod.app.data.files

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ArchiveToolTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun tree(): File {
        val root = tmp.newFolder("src")
        File(root, "a.txt").writeText("alfa")
        File(root, "sub").mkdirs()
        File(root, "sub/b.txt").writeText("beta")
        File(root, "sub/" + "x".repeat(120) + ".txt").writeText("uzun")
        return root
    }

    @Test fun zipRoundTrip() {
        val out = File(tmp.root, "t.zip")
        val z = ArchiveTool.compress(listOf(tree()), out, ArchiveFormat.ZIP).getOrThrow()
        val names = ArchiveTool.list(z).map { it.path }
        assertTrue("src/sub/b.txt" in names)
        val dest = tmp.newFolder("out")
        assertTrue(ArchiveTool.extract(z, dest).isSuccess)
        assertEquals("beta", File(dest, "src/sub/b.txt").readText())
    }

    @Test fun tarGzRoundTripWithLongName() {
        val z = ArchiveTool.compress(listOf(tree()), File(tmp.root, "t.tar.gz"), ArchiveFormat.TAR_GZ).getOrThrow()
        val all = ArchiveTool.list(z)
        assertTrue(all.any { it.name.length > 100 })
        val dest = tmp.newFolder("out2")
        assertEquals(3, ArchiveTool.extract(z, dest).getOrThrow())
        assertEquals("alfa", File(dest, "src/a.txt").readText())
    }

    @Test fun zipSlipBlocked() {
        val evil = File(tmp.root, "evil.zip")
        ZipOutputStream(evil.outputStream()).use {
            it.putNextEntry(ZipEntry("../../escape.txt"))
            it.write(1)
            it.closeEntry()
            it.putNextEntry(ZipEntry("ok.txt"))
            it.write(2)
            it.closeEntry()
        }
        val dest = tmp.newFolder("safe")
        ArchiveTool.extract(evil, dest).getOrThrow()
        assertFalse(File(dest.parentFile.parentFile, "escape.txt").exists())
        assertTrue(File(dest, "ok.txt").exists())
        assertNull(ArchiveTool.cleanPath("../x"))
        assertNull(ArchiveTool.safeTarget(dest, "a/../../x"))
    }

    @Test fun neverOverwritesAndBrowses() {
        val z = ArchiveTool.compress(listOf(tree()), File(tmp.root, "t.zip")).getOrThrow()
        val dest = tmp.newFolder("o3")
        ArchiveTool.extract(z, dest)
        ArchiveTool.extract(z, dest)
        assertTrue(File(dest, "src/a (1).txt").exists())
        val kids = ArchiveTool.children(ArchiveTool.list(z), "src")
        assertEquals(listOf("sub", "a.txt"), kids.map { it.name })
    }

    @Test fun formats() {
        assertEquals(ArchiveFormat.TAR_GZ, ArchiveTool.formatOf("x.TGZ"))
        assertEquals(ArchiveFormat.UNSUPPORTED, ArchiveTool.formatOf("x.7z"))
        assertEquals("proje", ArchiveTool.baseName("proje.tar.gz"))
    }
}
