package com.kriptoncod.app.ui.components

/**
 * Düğüm çıktısını markdown görünümüne hazırlar (saf Kotlin; Compose'a bağlı değil, birim testlenebilir).
 *
 * Girdi: AI çıktısı. Kod blokları ```tam/dosya/yolu biçiminde gelir; renklendirici ise dil etiketi bekler.
 * Burada her yollu çit şuna çevrilir: önce **`yol`** başlığı, sonra dosya uzantısından türetilen dil etiketli çit.
 * Sonuç, tembel listede (LazyColumn) ayrı ayrı çizilebilsin diye bölümlere (düz yazı / tek kod bloğu) ayrılır;
 * çok uzun bloklar parçalanır, çok çok uzunlar renklendirilmez (UI takılmasın).
 */
object OutputMarkdown {

    /** Bir kod bloğu bu satırı aşarsa parçalara bölünür. */
    const val CHUNK_LINES = 150

    /** Bir kod bloğu bu satırı aşarsa renklendirme kapatılır. */
    const val MAX_HIGHLIGHT_LINES = 1500

    private val FENCE_OPEN = Regex("^( {0,3})(`{3,}|~{3,})(.*)$")

    private val LANG_BY_EXT = mapOf(
        "kt" to "kotlin", "kts" to "kotlin", "java" to "java", "dart" to "dart",
        "js" to "javascript", "mjs" to "javascript", "cjs" to "javascript", "jsx" to "javascript",
        "ts" to "typescript", "tsx" to "typescript", "py" to "python",
        "c" to "c", "h" to "c", "cc" to "cpp", "cpp" to "cpp", "hpp" to "cpp", "cs" to "csharp",
        "go" to "go", "rs" to "rust", "swift" to "swift", "rb" to "ruby", "php" to "php",
        "sh" to "shell", "bash" to "shell", "json" to "json", "xml" to "xml",
        "yml" to "yaml", "yaml" to "yaml", "md" to "markdown", "gradle" to "groovy",
    )

    /** Çıktıyı çizilecek markdown bölümlerine ayırır; hiçbir karakter içerik olarak kaybolmaz. */
    fun segments(raw: String): List<String> {
        val lines = raw.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val out = ArrayList<String>()
        val prose = StringBuilder()

        fun flushProse() {
            val t = prose.toString().trim('\n')
            if (t.isNotBlank()) out += t
            prose.setLength(0)
        }

        var i = 0
        while (i < lines.size) {
            val m = FENCE_OPEN.matchEntire(lines[i])
            if (m == null) {
                prose.append(lines[i]).append('\n')
                i++
                continue
            }
            val indent = m.groupValues[1]
            val fence = m.groupValues[2]
            val info = m.groupValues[3].trim()
            // Eşleşen kapanış: aynı karakter, en az aynı uzunluk, bilgi dizesi yok.
            var close = -1
            for (j in i + 1 until lines.size) {
                val t = lines[j].trim()
                if (t.length >= fence.length && t.all { it == fence[0] } && t.startsWith(fence)) {
                    close = j
                    break
                }
            }
            val bodyEnd = if (close >= 0) close else lines.size
            val body = lines.subList(i + 1, bodyEnd)

            val (path, lang) = resolveInfo(info)
            flushProse()
            val highlight = body.size <= MAX_HIGHLIGHT_LINES
            val langTag = if (highlight) lang.orEmpty() else ""
            val chunks = if (body.size <= CHUNK_LINES) listOf(body) else body.chunked(CHUNK_LINES)
            chunks.forEachIndexed { idx, part ->
                val sb = StringBuilder()
                if (idx == 0 && path != null) sb.append("**`").append(path).append("`**\n\n")
                // Gövdede çit karakteri varsa dışarıdaki çit daha uzun olmalı.
                val longest = longestRun(part, fence[0])
                val outer = fence[0].toString().repeat(maxOf(3, longest + 1, fence.length))
                sb.append(indent).append(outer).append(langTag).append('\n')
                part.forEach { sb.append(it).append('\n') }
                sb.append(indent).append(outer)
                out += sb.toString()
            }
            i = if (close >= 0) close + 1 else lines.size
        }
        flushProse()
        return out
    }

    /** Çit bilgi dizesinden (yol, dil) çözer. Biçimler: `yol/dosya.kt`, `kotlin yol/dosya.kt`, `kotlin:yol`, `kotlin title="yol"`. */
    internal fun resolveInfo(info: String): Pair<String?, String?> {
        if (info.isEmpty()) return null to null
        Regex("title\\s*=\\s*\"([^\"]+)\"").find(info)?.let { m ->
            val first = info.substringBefore(' ')
            val lang = first.takeIf { '=' !in it && it.isNotEmpty() }
            return m.groupValues[1] to (lang ?: langOf(m.groupValues[1]))
        }
        val tokens = info.split(Regex("\\s+"))
        val first = tokens[0]
        val colon = first.indexOf(':')
        if (colon > 0 && first.substring(0, colon).all { it.isLetter() } && looksLikePath(first.substring(colon + 1))) {
            return first.substring(colon + 1) to first.substring(0, colon).lowercase()
        }
        if (looksLikePath(first)) return first to langOf(first)
        if (tokens.size > 1 && looksLikePath(tokens[1])) return tokens[1] to first.lowercase()
        return null to first.lowercase()
    }

    private fun looksLikePath(t: String): Boolean {
        if (t.isEmpty() || t.endsWith('.')) return false
        if ('/' in t) return true
        val ext = t.substringAfterLast('.', "")
        return t.contains('.') && ext.length in 1..5 && ext.all { it.isLetterOrDigit() }
    }

    private fun langOf(path: String): String? = LANG_BY_EXT[path.substringAfterLast('.', "").lowercase()]

    private fun longestRun(lines: List<String>, ch: Char): Int {
        var best = 0
        for (l in lines) {
            val t = l.trim()
            if (t.isNotEmpty() && t.all { it == ch }) best = maxOf(best, t.length)
        }
        return best
    }
}
