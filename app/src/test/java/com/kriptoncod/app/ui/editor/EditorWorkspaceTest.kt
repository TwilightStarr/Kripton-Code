package com.kriptoncod.app.ui.editor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EditorWorkspaceTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun validNameRejectsSeparatorsAndDots() {
        assertTrue(EditorWorkspace.validName("main.kt"))
        assertTrue(EditorWorkspace.validName("  notlar.md  "))
        assertFalse(EditorWorkspace.validName(""))
        assertFalse(EditorWorkspace.validName("   "))
        assertFalse(EditorWorkspace.validName("."))
        assertFalse(EditorWorkspace.validName(".."))
        assertFalse(EditorWorkspace.validName("a/b.kt"))
        assertFalse(EditorWorkspace.validName("a\\b.kt"))
        assertFalse(EditorWorkspace.validName("x".repeat(121)))
    }

    @Test fun uniqueFileNeverOverwrites() {
        val dir = tmp.newFolder("w")
        assertEquals("a.txt", EditorWorkspace.uniqueFile(dir, "a.txt").name)
        File(dir, "a.txt").writeText("1")
        assertEquals("a (1).txt", EditorWorkspace.uniqueFile(dir, "a.txt").name)
        File(dir, "a (1).txt").writeText("2")
        assertEquals("a (2).txt", EditorWorkspace.uniqueFile(dir, "a.txt").name)
        File(dir, "Makefile").writeText("x")
        assertEquals("Makefile (1)", EditorWorkspace.uniqueFile(dir, "Makefile").name)
    }

    @Test fun listPutsFoldersFirstAndHidesTempFiles() {
        val dir = tmp.newFolder("w")
        File(dir, "b.kt").writeText("")
        File(dir, "A.kt").writeText("")
        File(dir, "zeta").mkdir()
        File(dir, "alfa").mkdir()
        File(dir, "x.kt.123.tmp").writeText("")
        assertEquals(listOf("alfa", "zeta", "A.kt", "b.kt"), EditorWorkspace.list(dir).map { it.name })
    }

    @Test fun insideDetectsEscape() {
        val root = tmp.newFolder("root")
        val sub = File(root, "sub").also { it.mkdir() }
        val outside = tmp.newFolder("outside")
        assertTrue(EditorWorkspace.isInside(root, root))
        assertTrue(EditorWorkspace.isInside(root, sub))
        assertFalse(EditorWorkspace.isInside(root, outside))
        assertFalse(EditorWorkspace.isInside(root, File(root, "../outside")))
    }

    @Test fun displayShowsRootedPath() {
        val root = tmp.newFolder("root")
        val sub = File(File(root, "a"), "b")
        assertEquals("/", EditorWorkspace.display(root, root))
        assertEquals("/a/b", EditorWorkspace.display(root, sub))
    }

    @Test fun draftsRoundTrip() {
        assertFalse(EditorDrafts.has("/x/y.kt"))
        EditorDrafts.put("/x/y.kt", EditorDrafts.Draft("metin", true, 42L))
        assertTrue(EditorDrafts.has("/x/y.kt"))
        assertEquals("metin", EditorDrafts.get("/x/y.kt")?.text)
        assertEquals(42L, EditorDrafts.get("/x/y.kt")?.base)
        EditorDrafts.remove("/x/y.kt")
        assertFalse(EditorDrafts.has("/x/y.kt"))
    }
}
