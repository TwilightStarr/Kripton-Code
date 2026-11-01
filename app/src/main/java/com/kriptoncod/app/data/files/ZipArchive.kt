package com.kriptoncod.app.data.files

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Arşivdeki bir zip'in kaydı. [kind]: REPO (GitHub'dan çekilen proje) ya da AI (bir adımda AI'ın değiştirdiği dosyalar). */
@Serializable
data class ZipRecord(
    val id: String,
    val createdAt: Long,
    val kind: String,
    val title: String,
    val detail: String = "",
    val sizeBytes: Long,
    val fileCount: Int,
    val sha: String = "",
)

/**
 * "Zip'ler" sekmesinin deposu: GitHub'dan çekilen proje zip'leri ve AI'ların yaptığı değişiklik zip'leri cihazda saklanır,
 * kullanıcı gezinebilir/indirebilir. Sınırsız büyümesin diye en yeni [MAX_ENTRIES] kayıt ve [MAX_BYTES] toplam boyut tutulur.
 * Anahtar/token İÇERMEZ. Bozuk dizin dosyası çökme yerine boş liste verir.
 */
class ZipArchive(root: File, private val now: () -> Long = System::currentTimeMillis) {
    private val dir = File(root, "zip-archive")
    private val indexFile = File(dir, "index.json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()
    private val _records = MutableStateFlow(load())
    val records: StateFlow<List<ZipRecord>> = _records

    private fun load(): List<ZipRecord> = try {
        if (!indexFile.isFile) {
            emptyList()
        } else {
            json.decodeFromString(ListSerializer(ZipRecord.serializer()), indexFile.readText())
                .filter { File(dir, "${it.id}.zip").isFile }
                .sortedByDescending { it.createdAt }
        }
    } catch (_: Exception) {
        emptyList()
    }

    private fun save(list: List<ZipRecord>) {
        AtomicFiles.write(indexFile, json.encodeToString(ListSerializer(ZipRecord.serializer()), list).toByteArray())
    }

    fun file(id: String): File = File(dir, "$id.zip").also { require(id.all { c -> c.isLetterOrDigit() || c == '-' } && id.isNotEmpty()) }

    /**
     * Zip'i arşive ekler. Aynı türde, aynı başlıkta ve aynı içerikte (SHA-256) en son kayıt varsa tekrar eklenmez.
     * Hata (disk dolu vb.) çalışmayı etkilemesin diye yutulur; null döner.
     */
    fun add(kind: String, title: String, detail: String, bytes: ByteArray, fileCount: Int = countEntries(bytes)): ZipRecord? = try {
        synchronized(lock) {
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val last = _records.value.firstOrNull { it.kind == kind && it.title == title }
            if (last != null && last.sha == sha) {
                last
            } else {
                val t = now()
                val id = "z$t-${sha.take(6)}"
                AtomicFiles.write(File(dir, "$id.zip"), bytes)
                val rec = ZipRecord(id, t, kind, title.take(120), detail.take(200), bytes.size.toLong(), fileCount, sha)
                val pruned = prune(listOf(rec) + _records.value)
                save(pruned)
                _records.value = pruned
                rec
            }
        }
    } catch (_: Exception) {
        null
    }

    fun delete(id: String) {
        synchronized(lock) {
            runCatching { File(dir, "$id.zip").delete() }
            val list = _records.value.filterNot { it.id == id }
            runCatching { save(list) }
            _records.value = list
        }
    }

    /** Arşivdeki tüm zip'leri siler (GitHub ve AI kayıtları; run sonuçlarına dokunmaz). */
    fun clear() {
        synchronized(lock) {
            _records.value.forEach { runCatching { File(dir, "${it.id}.zip").delete() } }
            runCatching { save(emptyList()) }
            _records.value = emptyList()
        }
    }

    /** Zip'in dosyalarını (yol, içerik) okar; bozuksa boş liste. */
    fun entries(id: String): List<Pair<String, ByteArray>> = try {
        readEntries(file(id).readBytes())
    } catch (_: Exception) {
        emptyList()
    }

    private fun prune(list: List<ZipRecord>): List<ZipRecord> {
        val sorted = list.sortedByDescending { it.createdAt }
        val keep = ArrayList<ZipRecord>()
        var total = 0L
        for (r in sorted) {
            if (keep.size < MAX_ENTRIES && (total + r.sizeBytes <= MAX_BYTES || keep.isEmpty())) {
                keep += r
                total += r.sizeBytes
            } else {
                runCatching { File(dir, "${r.id}.zip").delete() }
            }
        }
        return keep
    }

    companion object {
        const val KIND_REPO = "REPO"
        const val KIND_AI = "AI"
        const val MAX_ENTRIES = 60
        const val MAX_BYTES = 300L * 1024 * 1024

        fun countEntries(bytes: ByteArray): Int = try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                var n = 0
                while (true) {
                    val e = z.nextEntry ?: break
                    if (!e.isDirectory) n++
                }
                n
            }
        } catch (_: Exception) {
            0
        }

        fun readEntries(bytes: ByteArray): List<Pair<String, ByteArray>> {
            val out = ArrayList<Pair<String, ByteArray>>()
            ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    out += e.name to z.readBytes()
                }
            }
            return out
        }
    }
}
