package com.kriptoncod.app.data.run

import android.util.Xml
import com.kriptoncod.app.data.web.WebOutputCheck
import java.io.StringReader
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserException

/**
 * Üretilen dosyalar için hafif sözdizimi denetimi (push/CI'dan ÖNCE yakalamak için).
 *
 * Tam ayrıştırıcı değildir; kesik/bozuk çıktının tipik belirtilerini yakalar:
 *  - JSON: kesin ayrıştırma (kotlinx.serialization),
 *  - XML: Android'in XmlPullParser'ı (etiket eşleşmesi, kaçışsız `&` vb.),
 *  - Kotlin/Java/Dart/JS/TS/C-benzeri/Python: parantez-süslü-köşeli dengesi, kapanmamış metin ve yorum
 *    (metin, yorum ve `${...}` şablonları atlanarak),
 *  - YAML: girintide TAB karakteri.
 * Bulgular UYARIdır (regex değişmezi gibi nadir durumlarda yanlış alarm olabilir); push'u engellemez.
 * Dosya uzantısı tanınmıyorsa denetlenmez.
 */
object SyntaxCheck {

    data class Issue(val path: String, val line: Int, val message: String)

    /** Bundan büyük içerik denetlenmez (UI/CPU koruması). */
    const val MAX_CHARS = 400_000

    private const val MAX_ISSUES = 20

    fun checkOutput(rawOutput: String): List<Issue> =
        checkBlocks(CodeBlockParser.parse(WebOutputCheck.canonical(rawOutput)))

    fun checkBlocks(blocks: List<CodeBlockParser.Block>): List<Issue> {
        val out = ArrayList<Issue>()
        for (b in blocks) {
            out += check(b.path, b.content)
            if (out.size >= MAX_ISSUES) break
        }
        return out.take(MAX_ISSUES)
    }

    fun check(path: String, content: String): List<Issue> {
        if (content.isBlank() || content.length > MAX_CHARS) return emptyList()
        val name = path.substringAfterLast('/').lowercase()
        val ext = name.substringAfterLast('.', "")
        return when {
            ext == "json" -> if (isJsonWithComments(name)) emptyList() else checkJson(path, content)
            ext == "xml" || ext == "svg" -> checkXml(path, content)
            ext == "yml" || ext == "yaml" -> checkYaml(path, content)
            else -> configFor(ext)?.let { scanBrackets(path, content, it) } ?: emptyList()
        }
    }

    // --- JSON ---------------------------------------------------------------------------------

    private fun isJsonWithComments(name: String) =
        name.startsWith("tsconfig") || name.startsWith("jsconfig") || name.contains("devcontainer") ||
            name.startsWith(".eslintrc") || name.startsWith("launch") || name.startsWith("settings")

    private fun checkJson(path: String, content: String): List<Issue> = try {
        Json.parseToJsonElement(content)
        emptyList()
    } catch (e: SerializationException) {
        val offset = Regex("offset (\\d+)").find(e.message.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        val line = offset?.let { lineAt(content, it) } ?: 1
        listOf(Issue(path, line, "Geçersiz JSON (kesilmiş ya da virgül/tırnak hatası olabilir)"))
    } catch (e: IllegalArgumentException) {
        listOf(Issue(path, 1, "Geçersiz JSON"))
    }

    private fun lineAt(text: String, offset: Int): Int {
        var line = 1
        for (i in 0 until offset.coerceAtMost(text.length)) if (text[i] == '\n') line++
        return line
    }

    // --- XML ----------------------------------------------------------------------------------

    private fun checkXml(path: String, content: String): List<Issue> = try {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(content))
        while (parser.next() != XmlPullParser.END_DOCUMENT) { /* yalnız ayrıştır */ }
        emptyList()
    } catch (e: XmlPullParserException) {
        listOf(Issue(path, e.lineNumber.coerceAtLeast(1), "Geçersiz XML (etiket eşleşmiyor ya da kesilmiş olabilir)"))
    } catch (e: java.io.IOException) {
        listOf(Issue(path, 1, "XML okunamadı"))
    }

    // --- YAML ---------------------------------------------------------------------------------

    private fun checkYaml(path: String, content: String): List<Issue> {
        content.lineSequence().forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            val indent = line.takeWhile { it == ' ' || it == '\t' }
            if ('\t' in indent) return listOf(Issue(path, i + 1, "YAML girintisinde TAB karakteri var (boşluk kullanılmalı)"))
        }
        return emptyList()
    }

    // --- Parantez dengesi ---------------------------------------------------------------------

    private class Cfg(
        val lineComment: Boolean = true,
        val blockComment: Boolean = true,
        val hashComment: Boolean = false,
        val singleQuote: Boolean = true,
        val triple: Boolean = false,
        val backtick: Boolean = false,
        /** `${...}` metin içinde (Kotlin/Dart/Groovy; Dart tek tırnaklı metinde de şablon kullanır). */
        val dollarTemplate: Boolean = false,
        /** Ters tırnaklı metin içinde `${...}` (JS/TS); false ise ham metindir (Go). */
        val backtickTemplate: Boolean = false,
    )

    private val KOTLIN_LIKE = Cfg(triple = true, dollarTemplate = true)
    private val JAVA_LIKE = Cfg(triple = true)
    private val C_LIKE = Cfg()
    private val JS_LIKE = Cfg(backtick = true, backtickTemplate = true)
    private val GO_LIKE = Cfg(backtick = true)
    private val CSS_LIKE = Cfg(lineComment = false)
    private val PYTHON = Cfg(lineComment = false, blockComment = false, hashComment = true, triple = true)

    private fun configFor(ext: String): Cfg? = when (ext) {
        "kt", "kts", "gradle", "dart" -> KOTLIN_LIKE
        "java" -> JAVA_LIKE
        "c", "h", "cc", "cpp", "hpp", "cs" -> C_LIKE
        "js", "mjs", "cjs", "jsx", "ts", "tsx" -> JS_LIKE
        "go" -> GO_LIKE
        "css", "scss" -> CSS_LIKE
        "py" -> PYTHON
        else -> null
    }

    private class Open(val ch: Char, val line: Int)

    private fun closerOf(open: Char) = when (open) { '(' -> ')'; '[' -> ']'; else -> '}' }

    /**
     * Tek geçişli tarayıcı. `modes` yığını: null = kod, aksi halde açık metnin sınırlayıcısı.
     * Metin içindeki `${` bir '$' işaretçisi iterek yeniden kod moduna geçer; eşleşen `}` metne döndürür.
     * İlk yapısal hatada durur (art arda gelen hatalar zincirleme olduğundan).
     */
    private fun scanBrackets(path: String, s: String, c: Cfg): List<Issue> {
        val brackets = ArrayList<Open>()
        val modes = ArrayList<String?>().apply { add(null) }
        val modeLines = ArrayList<Int>().apply { add(1) }
        var i = 0
        var line = 1
        val n = s.length

        fun issue(l: Int, msg: String) = listOf(Issue(path, l, msg))

        while (i < n) {
            val ch = s[i]
            val next = if (i + 1 < n) s[i + 1] else '\u0000'
            if (ch == '\n') line++
            val mode = modes.last()

            if (mode == null) {
                when {
                    c.lineComment && ch == '/' && next == '/' -> {
                        while (i < n && s[i] != '\n') i++
                        continue
                    }
                    c.hashComment && ch == '#' -> {
                        while (i < n && s[i] != '\n') i++
                        continue
                    }
                    c.blockComment && ch == '/' && next == '*' -> {
                        val end = s.indexOf("*/", i + 2)
                        val stop = if (end < 0) n else end + 2
                        val startLine = line
                        for (k in i until stop) if (s[k] == '\n') line++
                        if (end < 0) return issue(startLine, "Kapanmamış /* yorum (çıktı kesilmiş olabilir)")
                        i = stop
                        continue
                    }
                    c.triple && s.startsWith("\"\"\"", i) -> { modes += "\"\"\""; modeLines += line; i += 3; continue }
                    c.triple && c.hashComment && s.startsWith("'''", i) -> { modes += "'''"; modeLines += line; i += 3; continue }
                    ch == '"' -> { modes += "\""; modeLines += line }
                    ch == '\'' && c.singleQuote -> { modes += "'"; modeLines += line }
                    ch == '`' && c.backtick -> { modes += "`"; modeLines += line }
                    ch == '(' || ch == '[' || ch == '{' -> brackets += Open(ch, line)
                    ch == ')' || ch == ']' || ch == '}' -> {
                        val top = brackets.lastOrNull()
                        if (ch == '}' && top?.ch == '$') {
                            brackets.removeAt(brackets.lastIndex)
                            modes.removeAt(modes.lastIndex)
                            modeLines.removeAt(modeLines.lastIndex)
                        } else if (top == null || top.ch == '$') {
                            return issue(line, "Fazladan '$ch' (açılışı yok)")
                        } else if (closerOf(top.ch) != ch) {
                            return issue(line, "'${top.ch}' (satır ${top.line}) ile '$ch' eşleşmiyor")
                        } else {
                            brackets.removeAt(brackets.lastIndex)
                        }
                    }
                }
                i++
                continue
            }

            // Metin içi
            val rawMode = mode == "\"\"\"" || mode == "'''" || (mode == "`" && !c.backtickTemplate)
            when {
                ch == '\\' && !rawMode -> {
                    if (next == '\n') line++
                    i += 2
                    continue
                }
                s.startsWith(mode, i) -> {
                    modes.removeAt(modes.lastIndex)
                    modeLines.removeAt(modeLines.lastIndex)
                    i += mode.length
                    continue
                }
                ch == '$' && next == '{' &&
                    ((c.dollarTemplate && (mode == "\"" || mode == "\"\"\"" || mode == "'")) || (c.backtickTemplate && mode == "`")) -> {
                    brackets += Open('$', line)
                    modes += null
                    modeLines += line
                    i += 2
                    continue
                }
                ch == '\n' && (mode == "\"" || mode == "'") -> {
                    return issue(modeLines.last(), "Kapanmamış metin değişmezi (olası; JSX metni/regex ise yanlış alarm olabilir)")
                }
            }
            i++
        }

        if (modes.size > 1) {
            return issue(modeLines.last(), "Kapanmamış metin/şablon (çıktı kesilmiş olabilir)")
        }
        val unclosed = brackets.filter { it.ch != '$' }
        if (unclosed.isNotEmpty()) {
            val first = unclosed.first()
            return issue(first.line, "${unclosed.size} kapanmamış '${first.ch}' (ilki satır ${first.line}); çıktı kesilmiş olabilir")
        }
        return emptyList()
    }
}
