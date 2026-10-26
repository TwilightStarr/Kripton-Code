package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.files.AtomicFiles
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Seri çalışmaların diskteki kaydı: `<dir>/<id>.json` (durum), `<id>.step.json` (süren alışverişin alınanları) ve
 * `<id>.files.zip` (commit bekleyen dosyalar). Yazmalar atomiktir; bozuk bir dosya diğer çalışmaları etkilemez.
 *
 * Kimlikler [FIRST_ID]'den başlar: bildirim kimlikleri normal run kimlikleriyle çakışmasın.
 */
class SerialStore(
    private val dir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val lock = Any()
    private val all = MutableStateFlow(loadAll())

    /** Tüm çalışmalar, yeniden eskiye. */
    fun observeAll(): StateFlow<List<SerialState>> = all

    fun get(id: Long): SerialState? = all.value.firstOrNull { it.id == id }

    /** Yeni kayıt oluşturur; [make] verilen kimlikle durumu kurar. */
    fun create(make: (id: Long) -> SerialState): SerialState = synchronized(lock) {
        val id = (all.value.maxOfOrNull { it.id } ?: (FIRST_ID - 1)) + 1
        val state = make(id)
        write(state)
        publish(state)
        state
    }

    /** Durumu [transform] ile günceller ve yazar; kayıt yoksa null. [SerialState.updatedAt] otomatik yenilenir. */
    fun update(id: Long, transform: (SerialState) -> SerialState): SerialState? = synchronized(lock) {
        val old = get(id) ?: return null
        val next = transform(old).copy(id = old.id, updatedAt = clock())
        write(next)
        publish(next)
        next
    }

    /** Günlüğe zaman damgalı satır ekler (en fazla [MAX_LOG] satır tutulur). */
    fun log(id: Long, line: String) {
        val stamp = TIME.format(Instant.ofEpochMilli(clock()).atZone(ZoneId.systemDefault()))
        update(id) { it.copy(log = (it.log + "$stamp $line").takeLast(MAX_LOG)) }
    }

    fun delete(id: Long) {
        synchronized(lock) {
            listOf(stateFile(id), stepFile(id), filesFile(id)).forEach { it.delete() }
            all.value = all.value.filter { it.id != id }
        }
    }

    // --- süren alışveriş ---------------------------------------------------------------------------------

    fun loadStep(id: Long): SerialStep = runCatching {
        stepFile(id).takeIf { it.isFile }?.let { json.decodeFromString<SerialStep>(it.readText()) }
    }.getOrNull() ?: SerialStep()

    fun saveStep(id: Long, step: SerialStep) = AtomicFiles.write(stepFile(id), json.encodeToString(step).toByteArray(Charsets.UTF_8))

    fun clearStep(id: Long) {
        stepFile(id).delete()
    }

    // --- commit bekleyen dosyalar ------------------------------------------------------------------------

    /** Commit bekleyen dosyalar (yol → bayt), yazım sırasıyla. */
    fun loadFiles(id: Long): LinkedHashMap<String, ByteArray> {
        val out = LinkedHashMap<String, ByteArray>()
        val f = filesFile(id)
        if (!f.isFile) return out
        runCatching {
            ZipInputStream(ByteArrayInputStream(f.readBytes())).use { zis ->
                while (true) {
                    val e = zis.nextEntry ?: break
                    if (!e.isDirectory) out[e.name] = zis.readBytes()
                }
            }
        }
        return out
    }

    fun saveFiles(id: Long, files: Map<String, ByteArray>) {
        if (files.isEmpty()) {
            filesFile(id).delete()
            return
        }
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((path, bytes) in files) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        AtomicFiles.write(filesFile(id), bos.toByteArray())
    }

    fun clearFiles(id: Long) {
        filesFile(id).delete()
    }

    // --- iç ----------------------------------------------------------------------------------------------

    private fun stateFile(id: Long) = File(dir, "$id.json")
    private fun stepFile(id: Long) = File(dir, "$id.step.json")
    private fun filesFile(id: Long) = File(dir, "$id.files.zip")

    private fun write(state: SerialState) = AtomicFiles.write(stateFile(state.id), json.encodeToString(state).toByteArray(Charsets.UTF_8))

    private fun publish(state: SerialState) {
        all.value = (all.value.filter { it.id != state.id } + state).sortedByDescending { it.id }
    }

    private fun loadAll(): List<SerialState> {
        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".json") && !f.name.endsWith(".step.json") }.orEmpty()
        return files.mapNotNull { f -> runCatching { json.decodeFromString<SerialState>(f.readText()) }.getOrNull() }
            .sortedByDescending { it.id }
    }

    companion object {
        /** İlk seri çalışma kimliği; normal run kimliklerinden ayrık bildirim kimlikleri için. */
        const val FIRST_ID = 90_000L
        const val MAX_LOG = 300
        private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}
