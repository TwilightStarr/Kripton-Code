package com.kriptoncod.app.data.github

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * GitHub Actions log ZIP'inden, AI'a düzeltme promptu olarak gönderilecek kısa hata özeti çıkarır.
 * Çıktıdaki olası gizli anahtarlar maskelenir; özet yalnız hata satırlarını içerir.
 */
object CiLogSummarizer {

    private val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val TIMESTAMP = Regex("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\\s?")
    private val STRONG = Regex("##\\[error\\]|^e: |\\bFAILED\\b|Process completed with exit code [1-9]")
    private val WEAK = Regex("\\berror\\b|\\bException\\b|\\bFAILURE\\b|\\bfatal\\b", RegexOption.IGNORE_CASE)

    private val SECRETS = listOf(
        Regex("gh[pousr]_[A-Za-z0-9]{20,}"),
        Regex("github_pat_[A-Za-z0-9_]{20,}"),
        Regex("sk-[A-Za-z0-9_-]{16,}"),
        Regex("AIza[0-9A-Za-z_-]{20,}"),
        Regex("(?i)bearer\\s+[A-Za-z0-9._~+/=-]{16,}"),
    )

    /** Bilinen gizli anahtar kalıplarını `[REDACTED]` ile değiştirir. */
    fun redact(text: String): String = SECRETS.fold(text) { acc, re -> re.replace(acc, "[REDACTED]") }

    /**
     * Önce `##[error]` gibi kesin satırlar, sonra zayıf eşleşmeler; tekrarlar atılır, varsayılan olarak kesilmez; [maxChars] verilirse o kadarla sınırlanır.
     * Eşleşme yoksa son logun tüm satırları verilir. Okunabilir log yoksa boş string döner.
     */
    fun summarize(zip: ByteArray, maxChars: Int = Int.MAX_VALUE): String {
        val strong = LinkedHashSet<String>()
        val weak = LinkedHashSet<String>()
        var tail: List<String> = emptyList()

        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val bytes = zis.readBytes()
                    val name = entry.name.removeSuffix(".txt")
                    val lines = bytes.toString(Charsets.UTF_8).lineSequence()
                        .map { clean(it) }
                        .filter { it.isNotBlank() }
                        .toList()
                    for (line in lines) {
                        val tagged = "[$name] $line"
                        when {
                            STRONG.containsMatchIn(line) -> strong += tagged
                            WEAK.containsMatchIn(line) -> weak += tagged
                        }
                    }
                    if (lines.isNotEmpty()) tail = lines
                }
            }
        } catch (e: IOException) {
            // Kesik/bozuk ZIP: o ana kadar okunanla devam edilir.
        }

        val picked = (strong + weak).ifEmpty { tail }
        if (picked.isEmpty()) return ""
        val text = redact(picked.joinToString("\n"))
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "\n…"
    }

    private fun clean(line: String): String = TIMESTAMP.replace(ANSI.replace(line, ""), "").trimEnd()
}
