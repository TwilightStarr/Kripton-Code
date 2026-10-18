package com.kriptoncod.app.data.files

import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val store get() = HistoryStore(tmp.root)

    private fun entry(id: Long, createdAt: Long = id) = HistoryFile(
        runId = id,
        createdAt = createdAt,
        status = "DONE",
        repo = "o/r",
        ciAttempts = 2,
        zipPath = "zips/$id.zip",
        jobs = listOf(HistoryJob(1, 2, 3, "DONE", 1, "outputs/$id/1.txt")),
    )

    @Test fun writeReadRoundTripWithSchema() = runBlocking {
        store.write(entry(1))
        val text = File(tmp.root, "history/1.json").readText()
        assertTrue(text.contains("\"schema\":$HISTORY_SCHEMA"))
        assertEquals(entry(1), store.read(1))
    }

    @Test fun corruptFileIsQuarantinedAndReadsNull() = runBlocking {
        File(tmp.root, "history").mkdirs()
        File(tmp.root, "history/2.json").writeText("{\"schema\":1,\"runId\":")
        assertNull(store.read(2))
        assertFalse(File(tmp.root, "history/2.json").exists())
        assertTrue(File(tmp.root, "history/2.json.corrupt").exists())
    }

    @Test fun newerSchemaIsIgnoredButKept() = runBlocking {
        File(tmp.root, "history").mkdirs()
        val f = File(tmp.root, "history/3.json")
        f.writeText("""{"schema":99,"runId":3,"createdAt":1,"status":"DONE","gelecekAlan":true}""")
        assertNull(store.read(3))
        assertTrue(f.exists())
    }

    @Test fun listSkipsBrokenAndSortsNewestFirst() = runBlocking {
        store.write(entry(1, createdAt = 10))
        store.write(entry(2, createdAt = 30))
        store.write(entry(3, createdAt = 20))
        File(tmp.root, "history/9.json").writeText("bozuk")
        assertEquals(listOf(2L, 3L, 1L), store.list().map { it.runId })
    }

    @Test fun recoverSweepsTempFilesAndDeleteWorks() = runBlocking {
        store.write(entry(1))
        File(tmp.root, "history/1.json.77.tmp").writeText("yarım")
        assertEquals(1, store.recover())
        store.delete(1)
        assertNull(store.read(1))
    }
}
