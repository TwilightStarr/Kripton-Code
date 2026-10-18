package com.kriptoncod.app.data.run

/**
 * AI çıktısındaki kod bloklarını çözer. Kanonik biçim ```tam/dosya/yolu, ancak sağlayıcılar yolu
 * farklı yerlere koyabildiği için şu biçimler de kabul edilir (hepsi aynı [Block]'a çözülür):
 *
 *  - ```yol/dosya.kt                        (kanonik)
 *  - ```kotlin yol/dosya.kt   ```kotlin:yol/dosya.kt   ```kotlin title="yol/dosya.kt"
 *  - ```kotlin + ilk satırda `// File: yol/dosya.kt` (ya da `# yol/dosya.kt`) yorumu
 *  - ```kotlin + hemen önceki satırda `**File: yol/dosya.kt**`, `### yol/dosya.kt`, `` `yol/dosya.kt` ``
 *
 * Yolu belirlenemeyen (yalnız dil etiketli) bloklar [parse] sonucuna girmez; ham çıktı olarak kalır.
 * Çit uzunluğu önemlidir (4 ters tırnak içindeki 3'lüler içeriktir); `.md` bloklarında iç içe
 * çitler dengelenir. Kapanmamış son blok kesik çıktı belirtisidir ([Analysis.unclosedFence]).
 */
object CodeBlockParser {

    data class Block(val path: String, val content: String)

    /** [path] null ise blok yolsuzdur (yalnız dil etiketi var). */
    data class ParsedBlock(val path: String?, val language: String?, val content: String)

    data class Analysis(val blocks: List<ParsedBlock>, val unclosedFence: Boolean)

    /**
     * [rawOutput] içindeki yollu kod blokları sırayla. Yol PathSafety ile doğrulanmaz; çağıran doğrular.
     * Kapanmamış son blok da (geriye dönük uyumluluk için) dahildir; kesiklik için [analyze] kullan.
     */
    fun parse(rawOutput: String): List<Block> = analyze(rawOutput).blocks.mapNotNull { b -> b.path?.let { Block(it, b.content) } }

    fun analyze(rawOutput: String): Analysis {
        val lines = rawOutput.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<ParsedBlock>()
        val body = ArrayList<String>()
        var open: Open? = null
        var nested = 0
        var prevNonBlank: String? = null

        fun emit(o: Open) {
            var content0: List<String> = body.dropWhile { it.isEmpty() }
            var path = o.path
            if (path == null) {
                path = o.label?.let { labelPath(it) }
                if (path == null && content0.isNotEmpty()) {
                    val fromComment = commentPath(content0.first())
                    if (fromComment != null) {
                        path = fromComment
                        content0 = content0.drop(1)
                    }
                }
            }
            val content = content0.joinToString("\n").trimEnd()
            if (content.isNotEmpty()) blocks += ParsedBlock(path, o.language, content)
            body.clear()
        }

        for (line in lines) {
            val o = open
            val fence = parseFence(line)
            if (o == null) {
                if (fence != null) {
                    val hint = parseInfo(fence.info)
                    // Etiket (önceki satır) açılış anında saklanır; yol yoksa kapanışta değerlendirilir.
                    open = Open(fence.char, fence.length, hint.path, hint.language, hint.path.isMarkdownPath(), prevNonBlank)
                    body.clear()
                    nested = 0
                } else if (line.isNotBlank()) {
                    prevNonBlank = line
                }
                continue
            }
            if (fence != null && fence.char == o.char && fence.length >= o.length) {
                if (fence.info.isEmpty()) {
                    if (o.markdown && nested > 0) {
                        nested--
                        body += line
                        continue
                    }
                    emit(o)
                    open = null
                    prevNonBlank = null
                    continue
                } else if (o.markdown) {
                    nested++
                    body += line
                    continue
                }
            }
            body += line
        }
        val last = open
        if (last != null) emit(last)
        return Analysis(blocks, unclosedFence = last != null)
    }

    // --- iç ayrıştırma ------------------------------------------------------------------------

    private class Open(
        val char: Char,
        val length: Int,
        val path: String?,
        val language: String?,
        val markdown: Boolean,
        /** Açılıştan hemen önceki boş olmayan satır (dosya etiketi olabilir). */
        val label: String?,
    )

    private data class Fence(val char: Char, val length: Int, val info: String)

    internal data class Hint(val path: String?, val language: String?)

    private val FENCE = Regex("""^\s*(`{3,}|~{3,})(.*)$""")

    private fun parseFence(line: String): Fence? {
        val m = FENCE.matchEntire(line) ?: return null
        val marks = m.groupValues[1]
        val info = m.groupValues[2].trim()
        if (marks[0] == '`' && '`' in info) return null // satır içi ```kod``` açılış değildir
        return Fence(marks[0], marks.length, info)
    }

    private fun String?.isMarkdownPath(): Boolean {
        val p = this?.lowercase() ?: return false
        return p.endsWith(".md") || p.endsWith(".markdown") || p.endsWith(".mdx")
    }

    private val ATTR = Regex("""(?i)\b(?:title|file|filename|filepath|path|dosya)\s*[=:]\s*["']?([^\s"'}]+)["']?""")
    private val WS = Regex("\\s+")
    private val PATH_CHARS = Regex("""^[A-Za-z0-9._@+~\-/\\]+$""")
    private val FILE_EXT = Regex("""\.[A-Za-z0-9]{1,8}$""")
    private val KNOWN_NAMES = setOf(
        "dockerfile", "makefile", "license", "gemfile", "procfile", "jenkinsfile", "rakefile", "vagrantfile", "cname",
    )

    /** Dil etiketi olarak kabul edilen (dosya yolu olmayan) tek sözcükler. */
    private val LANGS = setOf(
        "kotlin", "kt", "kts", "java", "python", "py", "javascript", "js", "typescript", "ts", "tsx", "jsx",
        "json", "jsonc", "xml", "html", "css", "scss", "bash", "sh", "shell", "zsh", "powershell", "ps1", "bat",
        "yaml", "yml", "toml", "ini", "properties", "sql", "c", "cpp", "csharp", "cs", "go", "golang", "rust", "rs",
        "swift", "php", "ruby", "rb", "dart", "gradle", "groovy", "markdown", "md", "text", "txt", "plaintext",
        "diff", "patch", "console", "terminal", "log", "proto", "graphql", "vue", "svelte", "lua", "scala", "r",
        "objc", "objectivec", "kotlinscript", "dockerfile-lang", "csv", "env",
    )

    private fun isLang(t: String) = t.lowercase().trim('{', '}') in LANGS

    internal fun looksLikePath(t: String): Boolean {
        if (t.isEmpty() || t.length > 260 || "://" in t || !PATH_CHARS.matches(t)) return false
        return '/' in t || FILE_EXT.containsMatchIn(t) || t.lowercase() in KNOWN_NAMES || t.startsWith('.')
    }

    internal fun parseInfo(info: String): Hint {
        if (info.isEmpty()) return Hint(null, null)
        val tokens = info.split(WS).filter { it.isNotEmpty() }
        val lang = tokens.firstOrNull()?.takeIf { isLang(it) }

        ATTR.find(info)?.groupValues?.get(1)?.takeIf { looksLikePath(it) }?.let { return Hint(it, lang) }

        if (tokens.size == 1) {
            val t = tokens[0]
            val colon = t.indexOf(':')
            if (colon > 0) {
                val pre = t.substring(0, colon)
                val post = t.substring(colon + 1)
                if (isLang(pre) && looksLikePath(post)) return Hint(post, pre)
            }
            if (isLang(t) && '/' !in t) return Hint(null, t)
            return Hint(t, null) // eski davranış: tek sözcük yol sayılır (ör. "Makefile")
        }
        if (lang != null) {
            tokens.drop(1).firstOrNull { looksLikePath(it) }?.let { return Hint(it, lang) }
            return Hint(null, lang)
        }
        tokens.firstOrNull { looksLikePath(it) }?.let { return Hint(it, null) }
        return Hint(null, tokens.first())
    }

    private val LABEL_PREFIX = Regex("""(?i)^(?:file(?:name|path)?|path|dosya(?:\s*adı)?)\s*[:=]\s*""")
    private val LIST_MARK = Regex("""^(?:[#>*\-\s]+|\d+[.)]\s+)+""")
    private val COMMENT = Regex("""^\s*(?://+|#+|--+|;+|/\*+|<!--)\s*(.*?)\s*(?:\*/|-->)?\s*$""")

    /** Bloktan hemen önceki satır bir dosya etiketiyse yolu; değilse null. */
    internal fun labelPath(line: String): String? {
        var s = line.trim().replace(LIST_MARK, "").replace("*", "").replace("`", "").trim().trimEnd(':').trim()
        s = s.replace(LABEL_PREFIX, "").trim()
        return s.takeIf { looksLikePath(it) }
    }

    /** Bloğun ilk satırı `// File: yol` benzeri bir yorumsa yolu; değilse null. Etiketsiz yorum için yol '/' içermeli. */
    internal fun commentPath(first: String): String? {
        val m = COMMENT.matchEntire(first) ?: return null
        var s = m.groupValues[1].trim()
        val prefixed = LABEL_PREFIX.containsMatchIn(s)
        s = s.replace(LABEL_PREFIX, "").trim()
        if (!looksLikePath(s)) return null
        return s.takeIf { prefixed || '/' in s }
    }
}
