package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.run.PathSafety
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * Sağlayıcının verdiği dosyayı (zip veya tek dosya) projenin standart biçimine, yani
 * ```yol/dosya kod bloklarına çevirir. Böylece [com.kriptoncod.app.data.run.RunFinalizer] hiç
 * değişmeden çalışır. Güvenli olmayan yollar ve ikili dosyalar atlanır; boyut sınırı yoktur.
 */
object ZipBlocks {

    data class Converted(val text: String, val skipped: List<String>)

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
        ZipInputStream(ByteArrayInputStream(bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name
                val data = zis.readBytes()
                val safe = safePath(name)
                val text = decodeText(data)
                if (safe == null || text == null) {
                    skipped += name
                    continue
                }
                out.append(block(safe, text)).append('\n')
            }
        }
        return Converted(out.toString().trimEnd(), skipped)
    }

    /** Zip içindeki metne çevrilemeyen (ikili) dosyalar: (güvenli yol, bayt). Boş ve güvensiz yollar atlanır. */
    fun binaries(file: WebFile): List<Pair<String, ByteArray>> {
        if (!isZip(file.bytes)) {
            val safe = safePath(file.name) ?: return emptyList()
            return if (decodeText(file.bytes) == null) listOf(safe to file.bytes) else emptyList()
        }
        val out = mutableListOf<Pair<String, ByteArray>>()
        ZipInputStream(ByteArrayInputStream(file.bytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.isDirectory) continue
                val data = zis.readBytes()
                val safe = safePath(entry.name) ?: continue
                if (data.isNotEmpty() && decodeText(data) == null) out += safe to data
            }
        }
        return out
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
