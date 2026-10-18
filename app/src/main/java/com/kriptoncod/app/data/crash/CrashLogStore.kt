package com.kriptoncod.app.data.crash

import android.content.Context
import java.io.File

/** Diskteki tek bir çökme kaydı. */
data class CrashLog(val file: File, val createdAt: Long)

/**
 * Yerel çökme günlükleri (`filesDir/crash/`). Kayıtlar yalnız cihazda kalır; ağa hiçbir şey gönderilmez.
 * En yeni [keep] kayıt tutulur, eskileri silinir. ACRA'nın ayrı süreçte (`:acra`) çalışan gönderim servisi de
 * buraya yazar; bu yüzden her işlem dosya düzeyinde ve atomiktir (geçici dosya + yeniden adlandırma).
 */
class CrashLogStore(private val dir: File, private val keep: Int = DEFAULT_KEEP) {

    init {
        require(keep >= 1) { "keep en az 1 olmalı" }
    }

    /** @param id rapor kimliği (dosya adına girmeden önce yalnız harf/rakam/tireye indirgenir). */
    fun write(id: String, json: String, now: Long = System.currentTimeMillis()): File {
        check(dir.isDirectory || dir.mkdirs()) { "Çökme klasörü oluşturulamadı" }
        val safe = id.filter { it.isLetterOrDigit() || it == '-' }.take(MAX_ID_LEN).ifEmpty { "report" }
        val target = File(dir, "$PREFIX$now-$safe$SUFFIX")
        val tmp = File(dir, "${target.name}.tmp")
        tmp.writeText(json, Charsets.UTF_8)
        check(tmp.renameTo(target)) { "Çökme kaydı yazılamadı" }
        prune()
        return target
    }

    /** En yeniden eskiye. */
    fun list(): List<CrashLog> = dir.listFiles { f -> f.isFile && f.name.startsWith(PREFIX) && f.name.endsWith(SUFFIX) }
        .orEmpty()
        .map { CrashLog(it, createdAtOf(it)) }
        .sortedWith(compareByDescending<CrashLog> { it.createdAt }.thenByDescending { it.file.name })

    fun read(log: CrashLog): String = log.file.readText(Charsets.UTF_8)

    fun latestText(): String? = list().firstOrNull()?.let { runCatching { read(it) }.getOrNull() }

    fun clear() {
        dir.listFiles().orEmpty().filter { it.isFile }.forEach { it.delete() }
    }

    private fun prune() {
        list().drop(keep).forEach { it.file.delete() }
    }

    /** Zaman damgası dosya adından okunur (dosya sistemi saatine güvenilmez); okunamazsa değişiklik zamanı. */
    private fun createdAtOf(f: File): Long =
        f.name.removePrefix(PREFIX).substringBefore('-').toLongOrNull() ?: f.lastModified()

    companion object {
        const val DEFAULT_KEEP = 20
        private const val MAX_ID_LEN = 40
        private const val PREFIX = "crash-"
        private const val SUFFIX = ".json"

        fun default(context: Context): CrashLogStore = CrashLogStore(File(context.applicationContext.filesDir, "crash"))
    }
}
