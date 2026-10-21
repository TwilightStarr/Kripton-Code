package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.web.WebOutputCheck

/**
 * Dosyaların GÖVDESİZ özeti: kod dosyaları için yalnız bildirim satırları (sınıf, fonksiyon, tür, dışa aktarım),
 * küçük yapılandırma/belge dosyaları için tamamı, büyüklerde ilk satırlar.
 *
 * `[[girdi: ...]]` ile bir prompta önceki çıktılar verilirken tam çıktı yerine bu özet konur: bağlam sınırı aşılmaz,
 * model yalnızca uyması gereken adları/imzaları/yolları görür. Çıktıda kod çiti (```) KULLANILMAZ; model özeti
 * yeniden üretilecek bir dosya bloğu sanmasın.
 */
object InterfaceSummarizer {

    const val DEFAULT_BUDGET = 8_000
    private const val SMALL_FILE_CHARS = 1_500
    private const val CONFIG_HEAD_LINES = 25
    private const val OTHER_HEAD_LINES = 10
    private const val MAX_LINE = 160
    private const val MAX_SKIPPED_LIST = 600

    private val CODE_EXT = setOf(
        "kt", "java", "ts", "tsx", "js", "jsx", "mjs", "cjs", "py", "go", "rs", "cs", "swift", "dart",
        "scala", "php", "rb", "c", "h", "cpp", "hpp", "cc", "vue", "svelte",
    )
    private val DATA_EXT = setOf(
        "json", "xml", "yaml", "yml", "toml", "gradle", "kts", "properties", "md", "txt", "html", "css",
        "scss", "sql", "sh", "ini", "cfg", "env", "csv", "mod", "lock",
    )

    private const val MODS =
        "(?:(?:public|private|protected|internal|open|abstract|final|static|override|suspend|data|sealed|inline|" +
            "export|default|async|const|readonly|declare|pub(?:\\([a-z]+\\))?|extern|unsafe|actual|expect|enum|" +
            "companion|operator|infix|tailrec|external|lateinit|vararg|value|fun|annotation|inner|partial|virtual|" +
            "sealed|record)\\s+)*"
    private val DECL = Regex("""^(\s*)$MODS(class|interface|object|struct|trait|impl|fun|func|fn|def|function|type|typealias|protocol|extension|record|enum|namespace|module|package)\b.*""")
    private val VALUE_DECL = Regex("""^(\s*)(?:(?:public|private|protected|internal|open|override|const|export|static|readonly|lateinit|pub|final)\s+)+(val|var|let|const)\b.*""")
    private val TOP_VALUE = Regex("""^(val|var|let|const)\b.*""")
    private val EXPORT = Regex("""^\s*(export\s*[{*]|export\s+default|module\.exports|exports\.\w+).*""")
    private val DECORATOR = Regex("""^\s*@[A-Za-z_]\w*(\(.*\))?\s*$""")

    /** Kod bloğu içeren bir AI çıktısının özeti; blok yoksa (düz metin) baştan [budget] karakter. */
    fun summarizeOutput(text: String, budget: Int = DEFAULT_BUDGET): String {
        val a = CodeBlockParser.analyze(WebOutputCheck.canonical(text))
        val files = a.blocks.mapNotNull { b -> b.path?.let { it to b.content } }
        if (files.isEmpty()) {
            val t = text.trim()
            return if (t.length <= budget) t else t.take(budget).trimEnd() + "\n… (metin ${t.length - budget} karakter kısaltıldı)"
        }
        return summarizeFiles(files, budget)
    }

    /** [files] (yol, içerik) için, toplamı yaklaşık [budget] karakteri aşmayan özet. */
    fun summarizeFiles(files: List<Pair<String, String>>, budget: Int = DEFAULT_BUDGET): String {
        val out = StringBuilder()
        val skipped = mutableListOf<String>()
        for ((path, content) in files) {
            val part = summarizeFile(path, content)
            if (out.length + part.length > budget && out.isNotEmpty()) {
                skipped += path
            } else {
                out.append(part)
            }
        }
        if (skipped.isNotEmpty()) {
            // Yol listesi de sınırlıdır: binlerce dosyada özet yine bağlamı patlatmasın.
            val list = StringBuilder()
            var shown = 0
            for (p in skipped) {
                if (list.length + p.length > MAX_SKIPPED_LIST) break
                if (shown > 0) list.append(", ")
                list.append(p)
                shown++
            }
            out.append("## (özet bütçesi doldu; yalnız yollar) ").append(list)
            if (shown < skipped.size) out.append(" … (+").append(skipped.size - shown).append(" dosya daha)")
            out.append('\n')
        }
        return out.toString().trimEnd()
    }

    fun summarizeFile(path: String, content: String): String {
        val lines = content.replace("\r\n", "\n").split('\n')
        val ext = path.substringAfterLast('.', "").lowercase()
        val body = when {
            ext in CODE_EXT -> declarations(lines)

            ext in DATA_EXT || path.substringAfterLast('/').lowercase() in NAMED_CONFIG ->
                if (content.length <= SMALL_FILE_CHARS) lines else head(lines, CONFIG_HEAD_LINES)

            else -> head(lines, OTHER_HEAD_LINES)
        }
        return buildString {
            append("## dosya: ").append(path).append(" (").append(lines.size).append(" satır)\n")
            body.forEach { append(it).append('\n') }
            append('\n')
        }
    }

    private val NAMED_CONFIG = setOf("dockerfile", "makefile", "gemfile", "procfile")

    private fun head(lines: List<String>, n: Int): List<String> = if (lines.size <= n) lines else lines.take(n).map { cap(it) } + "… (${lines.size - n} satır daha)"

    private fun cap(s: String) = if (s.length <= MAX_LINE) s else s.take(MAX_LINE) + "…"

    private fun declarations(lines: List<String>): List<String> {
        val kept = ArrayList<String>()
        var pendingDecorator: String? = null
        for (raw in lines) {
            val line = raw.trimEnd()
            if (line.isBlank()) continue
            val keep = DECL.matches(line) || VALUE_DECL.matches(line) || TOP_VALUE.matches(line) || EXPORT.matches(line)
            if (DECORATOR.matches(line)) {
                pendingDecorator = line
                continue
            }
            if (keep) {
                pendingDecorator?.let { kept += cap(it) }
                kept += cap(line)
            }
            pendingDecorator = null
        }
        return if (kept.isEmpty()) listOf("(bildirim satırı bulunamadı)") else kept
    }
}
