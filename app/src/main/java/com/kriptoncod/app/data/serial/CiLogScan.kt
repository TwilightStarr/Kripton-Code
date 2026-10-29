package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.github.CiLogSummarizer
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * GitHub Actions log ZIP'inden HATA ve UYARI satırlarını ayrı ayrı çıkarır. Seri mod bunları AI'a olduğu gibi (özetlemeden)
 * yapıştırır; bu yüzden satırlar bağlamıyla ve kaynak adımıyla birlikte verilir. Sır gibi görünen değerler maskelenir.
 */
object CiLogScan {

    data class Result(val errors: List<String>, val warnings: List<String>) {
        val clean: Boolean get() = errors.isEmpty() && warnings.isEmpty()
    }

    private val ANSI = Regex("\u001B\\[[0-9;]*[A-Za-z]")
    private val TIMESTAMP = Regex("^\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\\s?")

    private val ERROR = Regex(
        "##\\[error\\]|^e: |^error[:\\s]|\\bFAILED\\b|\\bFAILURE\\b|Process completed with exit code [1-9]|^\\s*error\\s+•|" +
            "\\bBUILD FAILED\\b|\\bUnhandled exception\\b|\\bFatal error\\b",
        RegexOption.IGNORE_CASE,
    )

    private val WARNING = Regex(
        "##\\[warning\\]|^w: |warning:|^\\s*warning\\s+•|^warning\\b|\\bwarning\\b.*\\b(deprecat|unused|unchecked|shadow)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * Gerçek proje uyarısı olmayan, her derlemede çıkan ve AI'ın düzeltemeyeceği gürültü. Bunlar yüzünden sonsuz düzeltme
     * döngüsüne girilmesin diye atlanır.
     */
    private val NOISE = listOf(
        "node.js", "actions/", "cache not found", "cache service", "no files were found", "post job cleanup",
        "hint:", "git config", "deprecated gradle features", "--warning-mode", "gradle.org/", "docs.gradle.org",
        "daemon will be stopped", "the --jvm-args", "enable-cache", "restore cache", "save cache", "ubuntu-latest",
        "runner image", "set-output", "save-state", "add-path", "set-env",
    )

    /** Sınır YOK: hata ve uyarıların tamamı AI'a verilir (yinelenen satırlar zaten tekilleştirilir). */
    const val MAX_ERRORS = Int.MAX_VALUE
    const val MAX_WARNINGS = Int.MAX_VALUE

    /** Gradle'ın hata gerekçesi bölümü (`* What went wrong:` ... `* Try:`); satırları birlikte anlam taşır. */
    private val WRONG_START = Regex("^\\s*\\* What went wrong:", RegexOption.IGNORE_CASE)
    private val WRONG_END = Regex("^\\s*\\* (Try|Get more help|Exception is):", RegexOption.IGNORE_CASE)

    /** [zip] bir Actions log arşividir (adım başına bir `.txt`). Okunamayan/kesik arşivde elde olanla devam edilir. */
    fun scan(zip: ByteArray): Result {
        val errors = LinkedHashSet<String>()
        val warnings = LinkedHashSet<String>()
        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.removeSuffix(".txt")
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    classify(name, text, errors, warnings)
                }
            }
        } catch (_: IOException) {
            // Kesik arşiv: o ana kadar okunanla devam.
        }
        return Result(errors.take(MAX_ERRORS), warnings.take(MAX_WARNINGS))
    }

    /** Düz log metni için aynı sınıflandırma (testler ve tek dosyalı loglar için). */
    fun scanText(text: String, source: String = "log"): Result {
        val errors = LinkedHashSet<String>()
        val warnings = LinkedHashSet<String>()
        classify(source, text, errors, warnings)
        return Result(errors.take(MAX_ERRORS), warnings.take(MAX_WARNINGS))
    }

    private fun classify(source: String, text: String, errors: MutableSet<String>, warnings: MutableSet<String>) {
        var inWrong = false
        for (raw in text.lineSequence()) {
            val line = TIMESTAMP.replace(ANSI.replace(raw, ""), "").trimEnd()
            if (line.isBlank()) continue
            if (inWrong && WRONG_END.containsMatchIn(line)) inWrong = false
            if (inWrong) {
                errors += "[$source] $line"
                continue
            }
            if (WRONG_START.containsMatchIn(line)) {
                inWrong = true
                errors += "[$source] $line"
                continue
            }
            val lower = line.lowercase()
            when {
                ERROR.containsMatchIn(line) -> errors += "[$source] $line"
                WARNING.containsMatchIn(line) && NOISE.none { it in lower } -> warnings += "[$source] $line"
            }
        }
    }

    /**
     * AI'a yapıştırılacak metin. Varsayılan olarak KESİLMEZ; yalnız sohbetin kaldıramayacağı dev boyutta ([MAX_RENDER_CHARS]) sondan
     * kesilir ve ne kadarının atıldığı açıkça yazılır (ilk satırlar en önemlisidir).
     */
    fun render(lines: List<String>, maxChars: Int = MAX_RENDER_CHARS): String {
        val text = CiLogSummarizer.redact(lines.joinToString("\n"))
        return if (text.length <= maxChars) text else text.take(maxChars).trimEnd() + "\n… (kısaltıldı: ${text.length - maxChars} karakter daha var)"
    }

    /** Bir sohbet mesajına sığabilecek en büyük günlük boyutu (karakter). */
    const val MAX_RENDER_CHARS = 400_000
}
