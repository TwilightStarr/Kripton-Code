package com.kriptoncod.app.data.files

import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OutputStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = OutputStore(tmp.root)
    private fun file(rel: String) = File(tmp.root, rel)

    @Test fun writeIsAtomicAndLeavesNoPartial() = runBlocking {
        val path = store().write(3, 7, "merhaba dünya")
        assertEquals("outputs/3/7.txt", path)
        assertEquals("merhaba dünya", file(path).readText())
        assertFalse(file("outputs/3/7.partial").exists())
        assertEquals("merhaba dünya", store().read(path))
    }

    @Test fun streamingPartialThenCommit() = runBlocking {
        val s = store()
        s.startPartial(1, 1)
        s.appendPartial(1, 1, "bir ")
        s.appendPartial(1, 1, "iki")
        assertTrue(file("outputs/1/1.partial").exists())
        assertFalse(file("outputs/1/1.txt").exists()) // commit'ten önce .txt görünmez
        val path = s.commitPartial(1, 1)
        assertEquals("bir iki", s.read(path))
        assertFalse(file("outputs/1/1.partial").exists())
    }

    @Test fun startPartialResetsOldContent() = runBlocking {
        val s = store()
        s.startPartial(1, 1); s.appendPartial(1, 1, "eski")
        s.startPartial(1, 1); s.appendPartial(1, 1, "yeni")
        assertEquals("yeni", s.read(s.commitPartial(1, 1)))
    }

    @Test fun overwriteKeepsOldContentUntilReplaced() = runBlocking {
        val s = store()
        s.write(1, 1, "v1")
        s.write(1, 1, "v2")
        assertEquals("v2", s.read("outputs/1/1.txt"))
    }

    @Test fun emptyOrMissingFileReadsAsNull() = runBlocking {
        val s = store()
        assertNull(s.read("outputs/9/9.txt"))
        file("outputs/9").mkdirs()
        file("outputs/9/9.txt").writeText("")
        assertNull(s.read("outputs/9/9.txt"))
        assertFalse(s.isValid("outputs/9/9.txt"))
    }

    @Test fun pathTraversalIsRejected() = runBlocking {
        val s = store()
        try {
            s.resolve("../etc/passwd")
            fail()
        } catch (_: IllegalArgumentException) {
        }
        assertNull(s.read("../outside.txt"))
        assertFalse(s.isValid("outputs/../../x"))
    }

    @Test fun recoverDeletesIncompletePartialsAndReportsThem() = runBlocking {
        file("outputs/2").mkdirs()
        file("outputs/2/5.partial").writeText("yarım")          // .txt yok -> yarım üretim
        file("outputs/2/6.partial").writeText("eski")           // .txt var -> eski artık
        file("outputs/2/6.txt").writeText("tam")
        file("outputs/2/8.txt.123.tmp").writeText("yarım tmp")  // yarım atomik yazma
        file("zips").mkdirs()
        file("zips/2.zip.456.tmp").writeText("yarım zip")

        val r = store().recover()

        assertEquals(listOf(JobKey(2, 5)), r.discardedPartials)
        assertEquals(1, r.stalePartials)
        assertEquals(2, r.removedTemp)
        assertFalse(file("outputs/2/5.partial").exists())
        assertFalse(file("outputs/2/6.partial").exists())
        assertEquals("tam", file("outputs/2/6.txt").readText()) // tamamlanmış dosyaya dokunulmaz
        assertFalse(file("zips/2.zip.456.tmp").exists())
    }

    @Test fun recoverOnEmptyRootIsNoOp() = runBlocking {
        assertEquals(StoreRecovery(), store().recover())
    }

    @Test fun concurrentWritesToSameJobNeverTear() = runBlocking {
        val s = store()
        val contents = (1..30).map { i -> "içerik-$i-" + "x".repeat(20_000) }
        contents.map { c -> async(Dispatchers.IO) { s.write(1, 1, c) } }.awaitAll()
        assertTrue(s.read("outputs/1/1.txt") in contents)
        assertFalse(file("outputs/1/1.partial").exists())
    }

    @Test fun concurrentWritesToDifferentJobsAllLand() = runBlocking {
        val s = store()
        (1L..40L).map { j -> async(Dispatchers.IO) { s.write(1, j, "çıktı-$j") } }.awaitAll()
        (1L..40L).forEach { assertEquals("çıktı-$it", s.read("outputs/1/$it.txt")) }
    }

    @Test fun zipWriteIsAtomicAndFailureLeavesNothing() = runBlocking {
        val s = store()
        val path = s.writeZip(4) { it.write(byteArrayOf(1, 2, 3)) }
        assertEquals("zips/4.zip", path)
        assertEquals(listOf<Byte>(1, 2, 3), file(path).readBytes().toList())

        try {
            s.writeZip(4) { it.write(9); error("yazma sırasında çöktü") }
            fail()
        } catch (_: IllegalStateException) {
        }
        assertEquals(listOf<Byte>(1, 2, 3), file(path).readBytes().toList()) // eski zip bozulmadı
        assertTrue(file("zips").listFiles()!!.none { it.name.endsWith(".tmp") })
    }

    @Test fun deleteRunRemovesOutputsAndZip() = runBlocking {
        val s = store()
        s.write(5, 1, "a"); s.writeZip(5) { it.write(1) }
        s.deleteRun(5)
        assertFalse(file("outputs/5").exists())
        assertFalse(file("zips/5.zip").exists())
    }
}
