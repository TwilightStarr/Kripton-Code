package com.kriptoncod.app.data.run

/**
 * AI çıktısındaki ```yol/dosya biçimli kod bloklarını çözer.
 * Format: ```tam/dosya/yolu  →  satırın geri kalanı yol, blok içeriği kod.
 */
object CodeBlockParser {

    data class Block(val path: String, val content: String)

    private val FENCE_OPEN = Regex("""^```([^\s`]+)\s*$""")
    private val FENCE_CLOSE = Regex("""^```\s*$""")

    /**
     * [rawOutput] içindeki tüm kod bloklarını sırayla döndürür.
     * Yol satırı PathSafety ile doğrulanmaz; çağıran doğrular.
     */
    fun parse(rawOutput: String): List<Block> {
        val blocks = mutableListOf<Block>()
        var currentPath: String? = null
        val buffer = StringBuilder()

        fun flush() {
            val path = currentPath
            if (path != null && buffer.isNotEmpty()) {
                blocks += Block(path, buffer.toString().trimEnd())
            }
            buffer.clear()
        }

        for (line in rawOutput.lineSequence()) {
            val open = FENCE_OPEN.find(line)
            if (open != null && currentPath == null) {
                currentPath = open.groupValues[1].trim()
                buffer.clear()
                continue
            }
            if (FENCE_CLOSE.matches(line) && currentPath != null) {
                flush()
                currentPath = null
                continue
            }
            if (currentPath != null) {
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(line)
            }
        }
        flush()
        return blocks
    }
}
