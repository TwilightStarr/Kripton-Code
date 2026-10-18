package com.kriptoncod.app.data.run

/**
 * AI'ın "geri kalanı aynı", `// ...` gibi yer tutucularla kısalttığı dosyaları yakalar.
 * Yalnız kod bloklarının içine bakar; düz yazı (.md/.txt) dosyaları taranmaz.
 * Bilerek muhafazakârdır: yalnız yorum satırı olan veya tek başına duran işaretler sayılır.
 */
object PlaceholderDetector {

    data class Finding(val path: String, val line: Int, val text: String)

    private val SKIP_EXT = setOf("md", "markdown", "mdx", "txt", "csv", "log")

    private val ELLIPSIS_ONLY = Regex("""^\s*(?:(?://+|#+|--+|;+|/\*+|\*|<!--)\s*)?(?:\.{3}|…)\s*(?:\*/|-->)?\s*$""")

    private val COMMENT_LINE = Regex("""^\s*(?://+|#+|--+|;+|/\*+|\*|<!--)\s*(.*?)\s*(?:\*/|-->)?\s*$""")

    private val PHRASES = Regex(
        """(?i)(rest of (the )?(code|file|implementation|class|function|method|content)|""" +
            """remaining (code|implementation|files?|content)|same as (before|above|previous)|""" +
            """(code|implementation) (goes )?here|your code here|existing code|""" +
            """\.{3} ?(existing|rest|more|other)|unchanged\b|""" +
            """geri kalan(ı)?|önceki(yle| ile)? aynı|diğer (kısımlar|kodlar)|buraya (kod|ekle)|değişmedi|aynen devam)""",
    )

    private val STUB = Regex("""(?i)\bTODO\(\s*"?\s*(implement|not implemented|uygula)""")

    fun find(blocks: List<CodeBlockParser.Block>): List<Finding> {
        val out = mutableListOf<Finding>()
        for (b in blocks) {
            val ext = PathSafety.extensionOf(b.path).lowercase()
            if (ext in SKIP_EXT) continue
            b.content.lineSequence().forEachIndexed { i, raw ->
                val line = raw.trimEnd()
                if (line.length > 140) return@forEachIndexed
                val hit = when {
                    ELLIPSIS_ONLY.matches(line) && !(ext == "py" && line.trim() == "...") -> true

                    STUB.containsMatchIn(line) -> true

                    else -> {
                        val c = COMMENT_LINE.matchEntire(line)?.groupValues?.get(1)
                        c != null && c.length <= 100 && PHRASES.containsMatchIn(c)
                    }
                }
                if (hit) out += Finding(b.path, i + 1, line.trim())
            }
        }
        return out
    }

    fun findInText(text: String): List<Finding> = find(CodeBlockParser.parse(text))
}
