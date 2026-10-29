package com.kriptoncod.app.data.files

import com.kriptoncod.app.data.run.OutputTriage
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * AI yanıtlarının kalıcı, ilk durağı. `<root>/<etiket>/` altında:
 *  - `NNNN-<zaman>.raw.txt`: yanıt gelir gelmez, hiçbir işlem yapılmadan yazılan TAM ham metin,
 *  - `NNNN-<zaman>.code.md`: [compact] sonrası yalnız kod blokları (birebir),
 *  - `NNNN-<zaman>.reply.md`: dosya beklenmeyen (düz metin) yanıtların kendisi,
 *  - `files/<ad>`: AI'ın verdiği özgün dosyalar (zip dahil) — bunlar kullanıcıya doğrudan sunulur, hiç sıkıştırılmaz/silinmez.
 *
 * Güvence: [compact] ham dosyayı YALNIZ kod bloğu sayısı aynen korunmuşsa siler; kod içeren hiçbir yanıt kaybolmaz.
 */
class RawReplyLog(
    private val root: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val seq = AtomicLong(0)

    private fun dir(tag: String) = File(root, safe(tag))

    /** Ham yanıtı hemen (atomik) yazar; yazılan dosyayı döndürür. Boş metin yazılmaz (null). */
    fun save(tag: String, text: String): File? {
        if (text.isBlank()) return null
        val f = File(dir(tag), "%04d-%d.raw.txt".format(next(tag), now()))
        AtomicFiles.write(f, text.toByteArray(Charsets.UTF_8))
        return f
    }

    /** AI'ın verdiği özgün dosyayı (ör. zip) olduğu gibi saklar; ad çakışırsa numaralanır. */
    fun saveFile(tag: String, name: String, bytes: ByteArray): File {
        val base = safe(name.substringAfterLast('/').ifBlank { "download" })
        val folder = File(dir(tag), "files").apply { mkdirs() }
        var target = File(folder, base)
        var n = 1
        while (target.exists()) target = File(folder, "${n++}_$base")
        AtomicFiles.write(target, bytes)
        return target
    }

    /**
     * [tag] altındaki bekleyen ham yanıtları sıkıştırır. [keepProse] true ise (düz metin çıktısı) yanıt olduğu gibi `.reply.md` olur;
     * değilse yalnız kod blokları `.code.md` olarak kalır, yazı ve gürültü silinir. Kod bloğu içermeyen yanıt silinir.
     * Döndürülen sayı: sıkıştırılan ham dosya adedi.
     */
    fun compact(tag: String, keepProse: Boolean = false): Int {
        val pending = dir(tag).listFiles { f -> f.isFile && f.name.endsWith(RAW) }.orEmpty().sortedBy { it.name }
        var n = 0
        for (raw in pending) {
            if (compactOne(raw, keepProse)) n++
        }
        return n
    }

    private fun compactOne(raw: File, keepProse: Boolean): Boolean {
        val text = runCatching { raw.readText(Charsets.UTF_8) }.getOrNull() ?: return false
        val stem = raw.name.removeSuffix(RAW)
        if (keepProse) {
            AtomicFiles.write(File(raw.parentFile, "$stem.reply.md"), text.toByteArray(Charsets.UTF_8))
            return raw.delete()
        }
        val blocks = OutputTriage.blockCount(text)
        if (blocks == 0) return raw.delete() // kod yok: ham metin yalnız gürültüdür
        val code = OutputTriage.codeOnly(text)
        val out = File(raw.parentFile, "$stem.code.md")
        AtomicFiles.write(out, code.toByteArray(Charsets.UTF_8))
        // Doğrulama: yazılan dosyadaki blok sayısı ham yanıttakiyle aynıysa ham dosya silinir; değilse KORUNUR.
        val kept = runCatching { OutputTriage.blockCount(out.readText(Charsets.UTF_8)) }.getOrDefault(-1)
        return if (kept == blocks) raw.delete() else false
    }

    /** [tag] için saklananlar (ham, kod, yanıt ve özgün dosyalar), ada göre sıralı. */
    fun files(tag: String): List<File> = dir(tag).walkTopDown().filter { it.isFile }.sortedBy { it.path }.toList()

    /** [tag] altındaki her şeyi tek zip olarak verir; hiçbir şey yoksa null. */
    fun bundle(tag: String): ByteArray? {
        val base = dir(tag)
        val list = files(tag)
        if (list.isEmpty()) return null
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for (f in list) {
                zos.putNextEntry(ZipEntry(f.relativeTo(base).path.replace(File.separatorChar, '/')))
                zos.write(f.readBytes())
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /** [tag] altında henüz sıkıştırılmamış (ham) yanıt sayısı. */
    fun pending(tag: String): Int = dir(tag).listFiles { f -> f.isFile && f.name.endsWith(RAW) }.orEmpty().size

    fun delete(tag: String) {
        dir(tag).deleteRecursively()
    }

    /** Başarısız işlerden artakalan ham dosyaları, [maxAgeMs]'den eskiyse sıkıştırır (kod korunur). Açılışta çağrılır. */
    fun sweep(maxAgeMs: Long): Int {
        val limit = now() - maxAgeMs
        var n = 0
        root.listFiles { f -> f.isDirectory }.orEmpty().forEach { tagDir ->
            tagDir.listFiles { f -> f.isFile && f.name.endsWith(RAW) && f.lastModified() < limit }.orEmpty().forEach { if (compactOne(it, keepProse = false)) n++ }
        }
        return n
    }

    /** [prefix] ile başlayan etiketlerin, [maxAgeMs]'den eski klasörlerini siler (başarısız web üretimlerinin artığı). */
    fun purge(prefix: String, maxAgeMs: Long): Int {
        val limit = now() - maxAgeMs
        var n = 0
        root.listFiles { f -> f.isDirectory && f.name.startsWith(prefix) }.orEmpty().forEach { d ->
            val newest = d.walkTopDown().filter { it.isFile }.maxOfOrNull { it.lastModified() } ?: 0L
            if (newest < limit && d.deleteRecursively()) n++
        }
        return n
    }

    private fun next(tag: String): Long {
        val existing = dir(tag).listFiles().orEmpty().mapNotNull { it.name.substringBefore('-').toLongOrNull() }.maxOrNull() ?: 0L
        return maxOf(existing, seq.get()).also { seq.set(it + 1) } + 1
    }

    private fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_.-]"), "_").trim('.', '_').ifEmpty { "x" }

    private companion object {
        const val RAW = ".raw.txt"
    }
}
