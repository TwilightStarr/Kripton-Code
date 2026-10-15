package com.kriptoncod.app.data.kimi

import com.kriptoncod.app.data.run.CodeBlockParser
import com.kriptoncod.app.data.run.PathSafety
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class KimiCodeRepository(private val root: File) {

    @Serializable
    data class StoredBlock(val path: String, val content: String)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun runDir(runId: Long) = File(root, "kimi/$runId")
    private fun indexFile(runId: Long) = File(runDir(runId), "index.json")

    @Synchronized
    fun collect(runId: Long, rawOutput: String): Int {
        val blocks = CodeBlockParser.parse(rawOutput).mapNotNull { b ->
            when (val r = PathSafety.validate(b.path)) {
                is PathSafety.Result.Safe -> StoredBlock(r.normalized, b.content)
                is PathSafety.Result.Rejected -> null
            }
        }
        if (blocks.isEmpty()) return 0
        val merged = (listBlocks(runId) + blocks)
            .groupBy { it.path }
            .map { (_, v) -> v.last() }
        writeIndex(runId, merged)
        return blocks.size
    }

    @Synchronized
    fun listBlocks(runId: Long): List<StoredBlock> {
        val f = indexFile(runId)
        if (!f.exists()) return emptyList()
        return runCatching { json.decodeFromString<List<StoredBlock>>(f.readText()) }
            .getOrElse { emptyList() }
    }

    fun hasOutputs(runId: Long): Boolean = indexFile(runId).exists()

    @Synchronized
    fun clear(runId: Long) {
        runDir(runId).deleteRecursively()
    }

    private fun writeIndex(runId: Long, blocks: List<StoredBlock>) {
        val d = runDir(runId)
        d.mkdirs()
        val tmp = File(d, "index.json.tmp")
        tmp.writeText(json.encodeToString(blocks))
        check(tmp.renameTo(indexFile(runId))) { "Kimi kod indeksi yazılamadı: $runId" }
    }
}
