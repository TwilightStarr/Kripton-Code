package com.kriptoncod.app.ui.editor

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Düzenleyicinin saf (Android'siz) mantığı: dosya türü → dil, okuma/çözme, yazma kodlaması. Birim testle doğrulanır. */
object EditorSupport {
    /** Düzenlemeye açılan en büyük dosya (bayt); fazlası salt okunur önizleme olur. */
    const val MAX_EDIT_BYTES = 5 * 1024 * 1024

    private val SCOPE_BY_EXT = mapOf(
        "java" to "source.java",
        "kt" to "source.kotlin", "kts" to "source.kotlin",
        "py" to "source.python", "pyw" to "source.python",
        "js" to "source.js", "mjs" to "source.js", "cjs" to "source.js", "jsx" to "source.js",
        "ts" to "source.js", "tsx" to "source.js", "gradle" to "source.js",
        "json" to "source.json", "jsonc" to "source.json", "json5" to "source.json",
        "yml" to "source.yaml", "yaml" to "source.yaml",
        "sh" to "source.shell", "bash" to "source.shell", "zsh" to "source.shell", "gitignore" to "source.shell",
        "toml" to "source.ini", "ini" to "source.ini", "cfg" to "source.ini", "conf" to "source.ini",
        "properties" to "source.ini", "pro" to "source.ini", "env" to "source.ini", "editorconfig" to "source.ini",
        "css" to "source.css", "scss" to "source.css", "less" to "source.css",
        "sql" to "source.sql",
        "xml" to "text.xml", "svg" to "text.xml", "plist" to "text.xml", "xsd" to "text.xml", "xsl" to "text.xml",
        "html" to "text.html.basic", "htm" to "text.html.basic",
        "md" to "text.html.markdown", "markdown" to "text.html.markdown",
    )

    private val LABEL_BY_SCOPE = mapOf(
        "source.java" to "Java", "source.kotlin" to "Kotlin", "source.python" to "Python", "source.js" to "JavaScript",
        "source.json" to "JSON", "source.yaml" to "YAML", "source.shell" to "Shell", "source.ini" to "Config",
        "source.css" to "CSS", "source.sql" to "SQL", "text.xml" to "XML", "text.html.basic" to "HTML",
        "text.html.markdown" to "Markdown",
    )

    /** Uzantıdan (ya da ".gitignore" gibi nokta dosyası adından) TextMate kapsam adı; bilinmiyorsa null (düz metin). */
    fun scopeFor(fileName: String): String? {
        val lower = fileName.lowercase(Locale.ROOT)
        val dot = lower.lastIndexOf('.')
        val ext = when {
            dot < 0 -> ""
            dot == 0 -> lower.substring(1)
            else -> lower.substring(dot + 1)
        }
        return SCOPE_BY_EXT[ext]
    }

    fun languageLabel(scope: String?): String = scope?.let { LABEL_BY_SCOPE[it] } ?: "Text"

    /** İlk 8000 baytta NUL varsa ikili dosyadır. */
    fun looksBinary(bytes: ByteArray, length: Int = bytes.size): Boolean {
        val n = minOf(length, 8000)
        for (i in 0 until n) if (bytes[i].toInt() == 0) return true
        return false
    }

    class Decoded(val text: String, val bom: Boolean, val lossy: Boolean)

    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** UTF-8 çözer (BOM'u ayırır). Geçersiz bayt varsa [Decoded.lossy] true olur (kaydedince bozulacağı için salt okunur). */
    fun decode(bytes: ByteArray): Decoded {
        val hasBom = bytes.size >= 3 && bytes[0] == BOM[0] && bytes[1] == BOM[1] && bytes[2] == BOM[2]
        val body = if (hasBom) ByteBuffer.wrap(bytes, 3, bytes.size - 3) else ByteBuffer.wrap(bytes)
        return try {
            val text = Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(body).toString()
            Decoded(text, hasBom, false)
        } catch (_: CharacterCodingException) {
            val start = if (hasBom) 3 else 0
            Decoded(String(bytes, start, bytes.size - start, Charsets.UTF_8), hasBom, true)
        }
    }

    fun encode(text: String, bom: Boolean): ByteArray {
        val body = text.toByteArray(Charsets.UTF_8)
        return if (bom) BOM + body else body
    }

    class Loaded(
        val text: String,
        val bom: Boolean,
        val truncated: Boolean,
        val lossy: Boolean,
        val binary: Boolean,
        val lastModified: Long,
    ) {
        /** Güvenle geri yazılabilir mi (kesilmemiş, bozulmadan çözülmüş, metin)? */
        val safeToWrite: Boolean get() = !truncated && !lossy && !binary
    }

    fun read(file: File, max: Int = MAX_EDIT_BYTES): Loaded {
        val out = ByteArrayOutputStream()
        var truncated = false
        file.inputStream().use { s ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = s.read(buf)
                if (n < 0) break
                if (out.size() + n > max) {
                    out.write(buf, 0, max - out.size())
                    truncated = true
                    break
                }
                out.write(buf, 0, n)
            }
        }
        val bytes = out.toByteArray()
        if (looksBinary(bytes)) return Loaded("", false, truncated, false, true, file.lastModified())
        val d = decode(bytes)
        return Loaded(d.text, d.bom, truncated, d.lossy, false, file.lastModified())
    }
}
