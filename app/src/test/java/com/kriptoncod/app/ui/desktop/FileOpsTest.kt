package com.kriptoncod.app.ui.desktop

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileOpsTest {
    private fun tmp(): File = java.nio.file.Files.createTempDirectory("kripton-ops").toFile()

    @Test fun kinds() {
        assertEquals(FileKind.MARKDOWN, FileOps.kindOf("a.MD"))
        assertEquals(FileKind.TEXT, FileOps.kindOf("x.json"))
        assertEquals(FileKind.PDF, FileOps.kindOf("r.pdf"))
        assertEquals(FileKind.IMAGE, FileOps.kindOf("p.webp"))
        assertEquals(FileKind.ARCHIVE, FileOps.kindOf("b.tar.gz"))
        assertEquals(FileKind.ARCHIVE, FileOps.kindOf("b.7z"))
        assertEquals(FileKind.OTHER, FileOps.kindOf("a.docx"))
    }

    @Test fun copyAvoidsOverwrite() {
        val d = tmp(); val a = File(d, "a.txt").apply { writeText("1") }
        val c1 = FileOps.copy(a, d).getOrThrow()
        assertEquals("a (1).txt", c1.name)
        assertEquals("a (2).txt", FileOps.copy(a, d).getOrThrow().name)
        assertEquals("1", a.readText())
    }

    @Test fun folderIntoItselfRejected() {
        val d = tmp(); val sub = File(d, "s").apply { mkdir() }
        assertTrue(FileOps.copy(d, sub).isFailure)
        assertTrue(FileOps.move(d, sub).isFailure)
    }

    @Test fun moveAndDelete() {
        val d = tmp(); val dst = File(d, "dst").apply { mkdir() }
        val a = File(d, "a.txt").apply { writeText("x") }
        val m = FileOps.move(a, dst).getOrThrow()
        assertFalse(a.exists()); assertTrue(m.exists())
        assertTrue(FileOps.delete(dst)); assertFalse(dst.exists())
    }

    @Test fun renameValidation() {
        val d = tmp(); val a = File(d, "a.txt").apply { writeText("x") }; File(d, "b.txt").writeText("y")
        assertTrue(FileOps.rename(a, "b.txt").isFailure)
        assertTrue(FileOps.rename(a, "../x").isFailure)
        assertTrue(FileOps.rename(a, " ").isFailure)
        assertEquals("c.txt", FileOps.rename(a, "c.txt").getOrThrow().name)
    }

    @Test fun checksumsAndText() {
        assertEquals("900150983cd24fb0d6963f7d28e17f72", FileOps.checksum("abc".byteInputStream(), "MD5"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FileOps.checksum("abc".byteInputStream(), "SHA-256"))
        val (t, cut) = FileOps.readText("abcdef".byteInputStream(), 3)
        assertEquals("abc" to true, t to cut)
    }

    @Test fun fenceAvoidsBackticks() {
        val md = FileOps.asFencedMarkdown("a ``` b", "x.json")
        assertTrue(md.startsWith("````json\n") && md.endsWith("\n````"))
    }
}
