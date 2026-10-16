package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.run.PathSafety
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * Sağlayıcının verdiği dosyayı (zip veya tek dosya) projenin standart biçimine, yani
 * ```yol/dosya kod bloklarına çevirir. Böylece [com.kriptoncod.app.data.run.RunFinalizer] hiç
 * değişmeden çalışır. Güvenli olmayan yollar, ikili dosyalar ve aşırı büyük girdiler atlanır.
 */
object ZipBlocks {

    data class Converted(val text: String, val skipped: List<String>)

    const val MAX_FILES = 500
    const val MAX_FILE_BYTES = 2 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 20 * 1024 * 1024

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 3 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            (bytes[2] == 3.toByte() || bytes[2] == 5.toByte())

    fun convert(file: WebFile): Converted =
        if (isZip(file.bytes)) fromZip(file.bytes) else fromSingle(file.name, file.bytes)

    fun fromSingle(name: String, bytes: ByteArray): Converted {
        val safe = safePath(name) ?: return Converted("", listOf(name))
        val text = decodeText(bytes) ?: return Converted("", listOf(name))
        return Converted(block(safe, text), emptyList())
    }

    fun fromZip(bytes: ByteArray): Converted {
        val out = StringBuilder()
        val skipped = mutableListOf<String>()
        var files = 0
        var total = 0L
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                if (files >= MAX_FILES || total >= MAX_TOTAL_BYTES) {
                    skipped += name
                    continue
                }
                val data = readLimited(zis)
                if (data == null) {
                    skipped += name
                    continue
                }
                val safe = safePath(name)
                val text = decodeText(data)
                if (safe == null || text == null) {
                    skipped += name
                    continue
                }
                files++
                total += data.size
                out.append(block(safe, text)).append('\n')
            }
        }
        return Converted(out.toString().trimEnd(), skipped)
    }

    private fun readLimited(zis: ZipInputStream): ByteArray? {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var n = zis.read(chunk)
        var tooBig = false
        while (n >= 0) {
            if (!tooBig) {
                buf.write(chunk, 0, n)
                if (buf.size() > MAX_FILE_BYTES) tooBig = true
            }
            n = zis.read(chunk)
        }
        return if (tooBig) null else buf.toByteArray()
    }

    private fun safePath(raw: String): String? {
        val p = raw.replace('\\', '/').trimStart('/')
        if (p.startsWith("__MACOSX/") || p.substringAfterLast('/') == ".DS_Store") return null
        return (PathSafety.validate(p) as? PathSafety.Result.Safe)?.normalized
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

    /** İçerikteki en uzun ters tırnak dizisinden daha uzun bir çitle sarar. */
    fun block(path: String, content: String): String {
        val longest = Regex("`+").findAll(content).maxOfOrNull { it.value.length } ?: 0
        val fence = "`".repeat(maxOf(3, longest + 1))
        return "$fence$path\n${content.trimEnd('\n')}\n$fence"
    }
}
