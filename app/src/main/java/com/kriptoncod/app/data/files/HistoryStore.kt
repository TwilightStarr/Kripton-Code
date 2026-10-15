package com.kriptoncod.app.data.files

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val HISTORY_SCHEMA = 1

@Serializable
data class HistoryJob(
    val jobId: Long,
    val promptId: Long,
    val accountId: Long? = null,
    val state: String,
    val attempts: Int = 0,
    val outputPath: String? = null,
)

/** history/<runId>.json. Anahtar/token İÇERMEZ. */
@Serializable
data class HistoryFile(
    val schema: Int = HISTORY_SCHEMA,
    val runId: Long,
    val createdAt: Long,
    val status: String,
    val repo: String? = null,
    val ciAttempts: Int = 0,
    val zipPath: String? = null,
    val jobs: List<HistoryJob> = emptyList(),
)

class HistoryStore(
    root: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val dir = File(root, "history")
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun file(runId: Long) = File(dir, "$runId.json")

    suspend fun write(entry: HistoryFile) {
        mutex.withLock {
            withContext(dispatcher) {
                AtomicFiles.write(file(entry.runId), json.encodeToString(HistoryFile.serializer(), entry).toByteArray())
            }
        }
    }

    /**
     * Okunamayan/bozuk dosya ".corrupt" adıyla kenara alınır ve null döner.
     * Daha yeni şema sürümü (başka uygulama sürümü yazmış) null döner, dosyaya dokunulmaz.
     */
    suspend fun read(runId: Long): HistoryFile? = mutex.withLock { withContext(dispatcher) { readLocked(file(runId)) } }

    suspend fun list(): List<HistoryFile> = mutex.withLock {
        withContext(dispatcher) {
            dir.listFiles { f -> f.isFile && f.name.endsWith(".json") }.orEmpty()
                .mapNotNull { readLocked(it) }
                .sortedWith(compareByDescending<HistoryFile> { it.createdAt }.thenByDescending { it.runId })
        }
    }

    suspend fun delete(runId: Long) {
        mutex.withLock { withContext(dispatcher) { file(runId).delete() } }
    }

    /** Açılışta: yarım .tmp dosyalarını süpürür. */
    suspend fun recover(): Int = mutex.withLock { withContext(dispatcher) { AtomicFiles.sweepTemp(dir) } }

    private fun readLocked(f: File): HistoryFile? {
        if (!f.isFile) return null
        val parsed = try {
            json.decodeFromString(HistoryFile.serializer(), f.readText(Charsets.UTF_8))
        } catch (_: Exception) {
            quarantine(f)
            return null
        }
        return if (parsed.schema in 1..HISTORY_SCHEMA) parsed else null
    }

    private fun quarantine(f: File) {
        val target = File(f.parentFile, f.name + ".corrupt")
        runCatching { AtomicFiles.move(f, target) }.onFailure { f.delete() }
    }
}
