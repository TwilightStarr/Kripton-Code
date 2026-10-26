package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.run.InterfaceSummarizer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * GitHub `zipball` arşivini işler: tek üst klasörü (`owner-repo-sha/`) soyar, sohbete verilecek zip'i yeniden kurar ve
 * zip eklenemediğinde projeyi METİN olarak dökümler.
 */
object RepoZip {

    class Project(val files: LinkedHashMap<String, ByteArray>)

    /** Zip'i (yol → bayt) okur; tüm girdiler tek bir üst klasör altındaysa o klasör atılır. Güvensiz yollar atlanır. */
    fun read(zip: ByteArray): Project {
        val raw = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.isDirectory) continue
                raw[e.name.replace('\\', '/')] = zis.readBytes()
            }
        }
        val tops = raw.keys.map { it.substringBefore('/', "") }.toSet()
        val strip = tops.size == 1 && tops.first().isNotEmpty() && raw.keys.all { '/' in it }
        val out = LinkedHashMap<String, ByteArray>()
        for ((name, bytes) in raw) {
            val path = (if (strip) name.substringAfter('/') else name).trimStart('/')
            if (path.isEmpty() || path.split('/').any { it == ".." || it == "." || it.isEmpty() }) continue
            out[path] = bytes
        }
        return Project(out)
    }

    /** Sohbete eklenecek zip: yalnız [files]; yollar olduğu gibi. */
    fun toZip(files: Map<String, ByteArray>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for ((path, bytes) in files) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    /**
     * Zip eklenemediğinde projenin metin dökümü: metin dosyaları ```yol bloklarıyla [maxChars] sınırına kadar, kalanlar için
     * arayüz özeti. İkili dosyalar yalnız adıyla anılır.
     */
    fun dump(files: Map<String, ByteArray>, maxChars: Int = 140_000, summaryChars: Int = 20_000): String {
        val texts = ArrayList<Pair<String, String>>()
        val binary = ArrayList<String>()
        for ((path, bytes) in files.entries.sortedBy { it.key }) {
            val t = decodeText(bytes)
            if (t == null) binary += path else texts += path to t
        }
        val sb = StringBuilder()
        var used = 0
        val rest = ArrayList<Pair<String, String>>()
        for ((path, text) in texts) {
            if (used + text.length > maxChars) {
                rest += path to text
                continue
            }
            sb.append(com.kriptoncod.app.data.web.ZipBlocks.block(path, text)).append("\n\n")
            used += text.length
        }
        if (rest.isNotEmpty()) {
            sb.append("The remaining files are only summarised (bodies omitted):\n")
            sb.append(InterfaceSummarizer.summarizeFiles(rest, summaryChars)).append("\n\n")
        }
        if (binary.isNotEmpty()) {
            sb.append("Binary files present (not shown): ").append(binary.take(60).joinToString(", ")).append("\n")
        }
        return sb.toString().trim()
    }

    private fun decodeText(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            null
        }
    }
}
