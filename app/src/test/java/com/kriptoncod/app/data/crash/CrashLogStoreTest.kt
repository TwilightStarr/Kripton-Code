package com.kriptoncod.app.data.crash

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashLogStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store(keep: Int = 3) = CrashLogStore(File(tmp.root, "crash"), keep)

    @Test fun writesAndReadsLatestFirst() {
        val s = store()
        s.write("a", "{\"n\":1}", now = 100)
        s.write("b", "{\"n\":2}", now = 200)
        assertEquals(listOf(200L, 100L), s.list().map { it.createdAt })
        assertEquals("{\"n\":2}", s.latestText())
    }

    @Test fun keepsOnlyNewestN() {
        val s = store(keep = 2)
        (1..5).forEach { s.write("id$it", "{}", now = it * 10L) }
        assertEquals(listOf(50L, 40L), s.list().map { it.createdAt })
    }

    @Test fun idIsSanitizedForFileName() {
        val s = store()
        val f = s.write("../../etc/passwd", "{}", now = 1)
        assertEquals(s.list().single().file.canonicalPath, f.canonicalPath)
        assertTrue(f.parentFile!!.name == "crash")
        assertTrue('/' !in f.name && ".." !in f.name)
    }

    @Test fun emptyStoreAndClear() {
        val s = store()
        assertNull(s.latestText())
        s.write("x", "{}", now = 1)
        s.clear()
        assertTrue(s.list().isEmpty())
    }
}
