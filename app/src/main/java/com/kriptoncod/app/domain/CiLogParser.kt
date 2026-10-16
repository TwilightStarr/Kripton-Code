package com.kriptoncod.app.domain

/**
 * CI log özetinden hata satırlarını yapılandırılmış biçime çevirir.
 * CiLoopWorker, düzeltme promptu üretmek için kullanır.
 */
object CiLogParser {

    data class CiError(
        /** Hatanın geçtiği dosya yolu (log satırından çıkarılır; yoksa ""). */
        val file: String,
        /** Satır numarası (yoksa -1). */
        val line: Int,
        /** Hata mesajı. */
        val message: String,
    )

    // e: file.kt: (10, 5) error message  — Kotlin
    // file.kt:10:5: error: message      — Java/Gradle
    // ##[error] message                  — GitHub Actions
    private val KOTLIN = Regex("""^e:\s+(\S+?):\s*\((\d+),\s*\d+\)\s+(.*)$""")
    private val JAVA = Regex("""^(\S+?):(\d+):\d+:\s*(?:error|warning):\s*(.*)$""")
    private val ACTIONS = Regex("""^##\[error\]\s*(.*)$""")
    private val GENERIC = Regex("""\b(error|FAILED|Exception|fatal)\b""", RegexOption.IGNORE_CASE)

    /**
     * [logText] içinden hata satırlarını çıkarır.
     * Boş veya hata içermeyen log için boş liste döner.
     */
    fun parse(logText: String): List<CiError> {
        val errors = mutableListOf<CiError>()
        for (raw in logText.lineSequence()) {
            val line = raw.trim()
            when {
                KOTLIN.matches(line) -> {
                    val m = KOTLIN.find(line)!!
                    errors += CiError(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
                }
                JAVA.matches(line) -> {
                    val m = JAVA.find(line)!!
                    errors += CiError(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3])
                }
                ACTIONS.matches(line) -> {
                    val m = ACTIONS.find(line)!!
                    errors += CiError("", -1, m.groupValues[1])
                }
                GENERIC.containsMatchIn(line) && line.length > 10 -> {
                    errors += CiError("", -1, line.take(300))
                }
            }
        }
        return errors.distinctBy { "${it.file}:${it.line}:${it.message}" }
    }

    /** Hata listesini AI düzeltme promptu için özet metne çevirir. */
    fun toPromptSummary(errors: List<CiError>): String = buildString {
        appendLine("CI hataları (${errors.size} adet):")
        errors.forEachIndexed { i, e ->
            val loc = if (e.file.isNotEmpty()) "${e.file}${if (e.line > 0) ":${e.line}" else ""}" else "genel"
            appendLine("${i + 1}. [$loc] ${e.message}")
        }
    }
}
