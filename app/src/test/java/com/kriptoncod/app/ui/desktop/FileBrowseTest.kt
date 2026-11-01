package com.kriptoncod.app.ui.desktop

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowseTest {
    private fun tempTree(): File {
        val root = java.nio.file.Files.createTempDirectory("kripton-fb").toFile()
        File(root, "b-dir").mkdir()
        File(root, "A-dir").mkdir()
        File(root, "z.txt").writeText("z")
        File(root, "a.md").writeText("# a")
        File(root, ".gizli").writeText("x")
        File(root, "A-dir/ic.txt").writeText("ic")
        return root
    }

    @Test fun listsFoldersFirstThenFilesCaseInsensitive() {
        val root = tempTree()
        assertEquals(listOf("A-dir", "b-dir", "a.md", "z.txt"), FileBrowse.list(root, showHidden = false).map { it.name })
        assertTrue(FileBrowse.list(root, showHidden = true).any { it.name == ".gizli" })
        assertEquals(emptyList<FileEntry>(), FileBrowse.list(File(root, "yok"), showHidden = false))
    }

    @Test fun parentNeverEscapesRoot() {
        val root = tempTree()
        val sub = File(root, "A-dir")
        assertEquals(root.canonicalFile, FileBrowse.parentWithin(sub, root))
        assertNull(FileBrowse.parentWithin(root, root))
        assertTrue(FileBrowse.isInside(sub, root))
        assertFalse(FileBrowse.isInside(root.parentFile!!, root))
    }

    @Test fun breadcrumbsFromRoot() {
        val root = tempTree()
        val crumbs = FileBrowse.breadcrumbs(File(root, "A-dir"), root, "Cihaz")
        assertEquals(listOf("Cihaz", "A-dir"), crumbs.map { it.first })
        assertEquals(listOf("Cihaz"), FileBrowse.breadcrumbs(root, root, "Cihaz").map { it.first })
    }

    @Test fun humanSizes() {
        assertEquals("0 B", FileBrowse.humanSize(0))
        assertEquals("1023 B", FileBrowse.humanSize(1023))
        assertEquals("1 KB", FileBrowse.humanSize(1024))
        assertEquals("1,5 KB", FileBrowse.humanSize(1536))
        assertEquals("2 MB", FileBrowse.humanSize(2L * 1024 * 1024))
        assertEquals("3,2 GB", FileBrowse.humanSize((3.2 * 1024 * 1024 * 1024).toLong()))
    }

    @Test fun mimeGuess() {
        assertEquals("application/pdf", FileBrowse.mimeFor("Rapor.PDF"))
        assertEquals("text/plain", FileBrowse.mimeFor("Main.kt"))
        assertEquals("application/octet-stream", FileBrowse.mimeFor("dosya"))
        assertEquals("application/octet-stream", FileBrowse.mimeFor("x.bilinmeyen"))
    }

    @Test fun readForUploadRespectsLimits() {
        val root = tempTree()
        val ok = FileBrowse.readForUpload(File(root, "z.txt"))
        assertNotNull(ok)
        assertEquals("z.txt", ok!!.name)
        assertEquals("text/plain", ok.mime)
        assertEquals(1, ok.bytes.size)
        assertNull(FileBrowse.readForUpload(File(root, "A-dir")))
        assertNull(FileBrowse.readForUpload(File(root, "yok.txt")))
        assertNull(FileBrowse.readForUpload(File(root, "a.md"), maxBytes = 2))
        File(root, "bos.txt").writeText("")
        assertNull(FileBrowse.readForUpload(File(root, "bos.txt")))
    }

    @Test fun readStreamLimitsAndDefaultsMime() {
        val data = ByteArray(10) { 1 }
        val f = FileBrowse.readStream("a.png", null, { ByteArrayInputStream(data) })
        assertEquals("image/png", f!!.mime)
        assertEquals(10, f.bytes.size)
        assertNull(FileBrowse.readStream("a.png", "image/png", { ByteArrayInputStream(data) }, maxBytes = 5))
        assertNull(FileBrowse.readStream("a.png", "image/png", { null }))
        assertEquals("text/x", FileBrowse.readStream("a", "text/x", { ByteArrayInputStream(data) })!!.mime)
    }
}
