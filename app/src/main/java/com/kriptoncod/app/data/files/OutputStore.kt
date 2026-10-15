package com.kriptoncod.app.data.files

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class JobKey(val runId: Long, val jobId: Long)

data class StoreRecovery(
    /** Silinen yarım geçici (.tmp) dosya sayısı. */
    val removedTemp: Int = 0,
    /** Tamamlanmış .txt'si zaten olduğu için silinen eski .partial sayısı. */
    val stalePartials: Int = 0,
    /** .txt'si olmayan, yani yarım kalmış üretimler (silindi; iş yeniden yapılmalı). */
    val discardedPartials: List<JobKey> = emptyList(),
)

/**
 * filesDir altında: outputs/<runId>/<jobId>.partial -> atomik rename -> <jobId>.txt,
 * zips/<runId>.zip. Veritabanına yazılan yollar filesDir'e göredir.
 */
class OutputStore(
    private val root: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val locks = ConcurrentHashMap<String, Mutex>()
    private fun lockFor(name: String) = locks.getOrPut(name) { Mutex() }

    private val outputsDir get() = File(root, OUTPUTS)
    private val zipsDir get() = File(root, ZIPS)

    fun outputRelativePath(runId: Long, jobId: Long) = "$OUTPUTS/$runId/$jobId.txt"
    fun zipRelativePath(runId: Long) = "$ZIPS/$runId.zip"

    private fun txt(runId: Long, jobId: Long) = File(outputsDir, "$runId/$jobId.txt")
    private fun partial(runId: Long, jobId: Long) = File(outputsDir, "$runId/$jobId.partial")

    /** Göreli yolu filesDir altında çözer; dışarı taşan yolu reddeder. */
    fun resolve(relativePath: String): File {
        val base = root.canonicalFile
        val f = File(base, relativePath).canonicalFile
        require(f.path.startsWith(base.path + File.separator)) { "Geçersiz yol" }
        return f
    }

    /** Tek seferde yaz: .partial -> fsync -> atomik .txt. Göreli yolu döndürür. */
    suspend fun write(runId: Long, jobId: Long, text: String): String {
        lockFor("o$runId/$jobId").withLock {
            withContext(dispatcher) {
                val part = partial(runId, jobId)
                part.parentFile!!.mkdirs()
                FileOutputStream(part, false).use { out ->
                    out.write(text.toByteArray(Charsets.UTF_8))
                    out.flush()
                    out.fd.sync()
                }
                AtomicFiles.move(part, txt(runId, jobId))
            }
        }
        return outputRelativePath(runId, jobId)
    }

    /** Akışlı üretim: önce başlat (varsa eski .partial'ı sıfırlar). */
    suspend fun startPartial(runId: Long, jobId: Long) {
        lockFor("o$runId/$jobId").withLock {
            withContext(dispatcher) {
                val part = partial(runId, jobId)
                part.parentFile!!.mkdirs()
                FileOutputStream(part, false).use { it.fd.sync() }
            }
        }
    }

    suspend fun appendPartial(runId: Long, jobId: Long, chunk: String) {
        lockFor("o$runId/$jobId").withLock {
            withContext(dispatcher) {
                val part = partial(runId, jobId)
                check(part.isFile) { "startPartial çağrılmadı" }
                FileOutputStream(part, true).use { it.write(chunk.toByteArray(Charsets.UTF_8)) }
            }
        }
    }

    /** .partial'ı fsync edip atomik olarak .txt yapar. */
    suspend fun commitPartial(runId: Long, jobId: Long): String {
        lockFor("o$runId/$jobId").withLock {
            withContext(dispatcher) {
                val part = partial(runId, jobId)
                check(part.isFile) { "Tamamlanacak .partial yok" }
                FileOutputStream(part, true).use { it.fd.sync() }
                AtomicFiles.move(part, txt(runId, jobId))
            }
        }
        return outputRelativePath(runId, jobId)
    }

    suspend fun discardPartial(runId: Long, jobId: Long) {
        lockFor("o$runId/$jobId").withLock { withContext(dispatcher) { partial(runId, jobId).delete() } }
    }

    /** Dosya yoksa veya boşsa (bozuk sayılır) null. */
    suspend fun read(relativePath: String): String? = withContext(dispatcher) {
        val f = safeResolve(relativePath)
        if (f != null && f.isFile && f.length() > 0) f.readText(Charsets.UTF_8) else null
    }

    /** Var ve boş değilse true. */
    suspend fun isValid(relativePath: String): Boolean = withContext(dispatcher) {
        val f = safeResolve(relativePath)
        f != null && f.isFile && f.length() > 0
    }

    /** Tamamlanmış çıktıyı siler (iş yeniden üretilecekse önce çağrılır). */
    suspend fun deleteOutput(runId: Long, jobId: Long) {
        lockFor("o$runId/$jobId").withLock {
            withContext(dispatcher) {
                txt(runId, jobId).delete()
                partial(runId, jobId).delete()
            }
        }
    }

    private fun safeResolve(relativePath: String): File? =
        try { resolve(relativePath) } catch (_: IllegalArgumentException) { null }

    /** zips/<runId>.zip'i atomik yazar; göreli yolu döndürür. */
    suspend fun writeZip(runId: Long, writer: (OutputStream) -> Unit): String {
        lockFor("z$runId").withLock {
            withContext(dispatcher) { AtomicFiles.write(File(zipsDir, "$runId.zip"), writer) }
        }
        return zipRelativePath(runId)
    }

    suspend fun deleteRun(runId: Long) {
        withContext(dispatcher) {
            File(outputsDir, runId.toString()).deleteRecursively()
            File(zipsDir, "$runId.zip").delete()
        }
    }

    /**
     * Açılışta çağrılır: yarım .tmp dosyalarını süpürür; .txt'si olan eski .partial'ları siler;
     * .txt'siz .partial'ları (yarım üretim) siler ve raporlar.
     */
    suspend fun recover(): StoreRecovery = withContext(dispatcher) {
        val removedTemp = AtomicFiles.sweepTemp(outputsDir) + AtomicFiles.sweepTemp(zipsDir)
        var stale = 0
        val discarded = mutableListOf<JobKey>()
        outputsDir.listFiles { f -> f.isDirectory }.orEmpty().forEach { runDir ->
            val runId = runDir.name.toLongOrNull() ?: return@forEach
            runDir.listFiles { f -> f.isFile && f.name.endsWith(PARTIAL_EXT) }.orEmpty().forEach { part ->
                val jobId = part.name.removeSuffix(PARTIAL_EXT).toLongOrNull()
                if (jobId != null && txt(runId, jobId).isFile) {
                    if (part.delete()) stale++
                } else {
                    if (part.delete() && jobId != null) discarded += JobKey(runId, jobId)
                }
            }
        }
        StoreRecovery(removedTemp, stale, discarded.sortedWith(compareBy({ it.runId }, { it.jobId })))
    }

    private companion object {
        const val OUTPUTS = "outputs"
        const val ZIPS = "zips"
        const val PARTIAL_EXT = ".partial"
    }
}
