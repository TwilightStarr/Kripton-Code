package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.web.WebOutputCheck
import java.time.Instant
import java.time.ZoneId

/**
 * Prompt şablonu: `{{...}}` yer tutucularını çözer. Saf mantık (Android'e bağlı değil).
 *
 *  - `{{ad}}` / `{{ad|varsayılan}}`   değişken ([FlowDirectives.variables]) ya da yerleşik değer
 *  - `{{çıktı:Başlık}}`               önceki bir düğümün tam çıktısı (varsayılan en çok [DEFAULT_CAP] karakter;
 *                                     `{{çıktı:Başlık|4000}}` ile sınır verilir)
 *  - `{{özet:Başlık}}`                aynı çıktının arayüz özeti ([InterfaceSummarizer]: yollar + bildirim satırları)
 *  - `{{dosya:Başlık#yol/a.kt}}`      çıktıdaki tek bir dosyanın içeriği
 *  - `{{yollar:Başlık}}`              çıktıdaki dosya yolları (satır satır)
 *  - `{{blok:Başlık}}`                çıktıdaki ilk kod bloğunun içeriği (yollu ya da yolsuz)
 *  - `\{{...}}`                       çözülmeden `{{...}}` olarak bırakılır (kaçış)
 *
 * Tanınmayan `{{...}}` metinleri OLDUĞU GİBİ bırakılır: kod içindeki şablonlar (Vue, Handlebars, Jinja…) bozulmaz.
 * Yalnız çıktı başvuruları (`çıktı:` vb.) sıkıdır: kaynak yoksa ya da başarısızsa `[kaynak çıktı yok: X]` yazılır ve
 * [Rendered.missing]'e eklenir.
 *
 * Değişken değerleri başka değişkenlere/yerleşiklere başvurabilir (en çok [MAX_DEPTH] düzey, döngü korumalı); değer
 * içinde çıktı başvurusu desteklenmez (bağımlılık sırası belirsizleşirdi) ve olduğu gibi kalır.
 */
object PromptTemplate {

    const val DEFAULT_CAP = 12_000
    private const val MAX_DEPTH = 8

    enum class Kind { VAR, OUTPUT, SUMMARY, FILE, PATHS, BLOCK }

    /** Metindeki bir başvuru. [title]: çıktı başvurularında kaynak düğüm başlığı; [name]: değişken adı. */
    data class Ref(
        val kind: Kind,
        val name: String,
        val title: String = "",
        val path: String? = null,
        val cap: Int? = null,
        val default: String? = null,
    )

    private val TOKEN = Regex("""(\\)?\{\{([^\{\}\n]+?)\}\}""")
    private val NAME = Regex("""^[\p{L}_][\p{L}\p{N}_.-]*$""")

    private val PREFIXES: Map<String, Kind> = mapOf(
        "çıktı" to Kind.OUTPUT, "cikti" to Kind.OUTPUT, "output" to Kind.OUTPUT,
        "özet" to Kind.SUMMARY, "ozet" to Kind.SUMMARY, "summary" to Kind.SUMMARY,
        "dosya" to Kind.FILE, "file" to Kind.FILE,
        "yollar" to Kind.PATHS, "paths" to Kind.PATHS,
        "blok" to Kind.BLOCK, "block" to Kind.BLOCK,
    )

    fun normName(name: String): String = name.trim().lowercase()

    /** `{{...}}` içeriğini çözer; tanınmıyorsa (kod şablonu vb.) null. */
    internal fun parseRef(inner: String): Ref? {
        val colon = inner.indexOf(':')
        if (colon > 0) {
            val kind = PREFIXES[inner.substring(0, colon).trim().lowercase()]
            if (kind != null) {
                var rest = inner.substring(colon + 1)
                var cap: Int? = null
                val bar = rest.lastIndexOf('|')
                if (bar >= 0) {
                    val n = rest.substring(bar + 1).trim().toIntOrNull()
                    if (n != null && n > 0) {
                        cap = n
                        rest = rest.substring(0, bar)
                    }
                }
                var path: String? = null
                if (kind == Kind.FILE) {
                    val hash = rest.indexOf('#')
                    if (hash >= 0) {
                        path = rest.substring(hash + 1).trim().ifEmpty { null }
                        rest = rest.substring(0, hash)
                    }
                }
                val title = rest.trim()
                if (title.isEmpty()) return null
                return Ref(kind, name = "", title = title, path = path, cap = cap)
            }
        }
        val bar = inner.indexOf('|')
        val name = (if (bar >= 0) inner.substring(0, bar) else inner).trim()
        if (!NAME.matches(name)) return null
        return Ref(Kind.VAR, name = name, default = if (bar >= 0) inner.substring(bar + 1).trim() else null)
    }

    /** [text] içindeki tüm (kaçışsız) başvurular, görünüş sırasıyla. */
    fun references(text: String): List<Ref> = TOKEN.findAll(text).filter { it.groupValues[1].isEmpty() }.mapNotNull { parseRef(it.groupValues[2]) }.toList()

    /** Çıktısına başvurulan düğüm başlıkları (tekrarsız, ilk yazılışla). */
    fun referencedTitles(text: String): List<String> {
        val seen = HashSet<String>()
        val out = ArrayList<String>()
        for (r in references(text)) {
            if (r.kind == Kind.VAR) continue
            if (seen.add(FlowDirectivesParser.normalize(r.title))) out += r.title
        }
        return out
    }

    /** Bir kaynak düğümün çıktısı. [text] null: kaynak yok, bitmedi ya da başarısız. */
    data class SourceOutput(val title: String, val text: String?)

    class Context(
        /** Kullanıcı değişkenleri: [normName] → ham değer (değer başka değişkenlere başvurabilir). */
        val vars: Map<String, String> = emptyMap(),
        /** Yerleşikler: [normName] → değer. Kullanıcı değişkeni aynı adla yerleşiği ezer. */
        val builtins: Map<String, String> = emptyMap(),
        /** Başlıktan kaynak çıktı; bilinmiyorsa null. */
        val output: (title: String) -> SourceOutput? = { null },
    )

    data class Rendered(val text: String, val missing: List<String>)

    fun render(text: String, ctx: Context): Rendered {
        val missing = LinkedHashSet<String>()
        val out = renderInner(text, ctx, depth = 0, active = emptySet(), allowOutputs = true, missing = missing)
        return Rendered(out, missing.toList())
    }

    private fun renderInner(
        text: String,
        ctx: Context,
        depth: Int,
        active: Set<String>,
        allowOutputs: Boolean,
        missing: MutableSet<String>,
    ): String = TOKEN.replace(text) { m ->
        val escaped = m.groupValues[1].isNotEmpty()
        val whole = m.value.removePrefix("\\")
        if (escaped) return@replace whole
        val ref = parseRef(m.groupValues[2]) ?: return@replace whole
        val replacement: String = when (ref.kind) {
            Kind.VAR -> {
                val key = normName(ref.name)
                val raw = ctx.vars[key] ?: ctx.builtins[key]
                when {
                    raw == null -> ref.default ?: whole

                    key in active || depth >= MAX_DEPTH -> whole

                    // döngü ya da aşırı derinlik: dokunma
                    else -> renderInner(raw, ctx, depth + 1, active + key, allowOutputs = false, missing = missing)
                }
            }

            else -> if (!allowOutputs) whole else renderOutput(ref, ctx, missing)
        }
        replacement
    }

    private fun renderOutput(ref: Ref, ctx: Context, missing: MutableSet<String>): String {
        val src = ctx.output(ref.title)
        val text = src?.text
        if (text == null) {
            missing += ref.title
            return "[kaynak çıktı yok: ${ref.title}]"
        }
        return when (ref.kind) {
            Kind.OUTPUT -> cap(text.trim(), ref.cap ?: DEFAULT_CAP)

            Kind.SUMMARY -> InterfaceSummarizer.summarizeOutput(text, ref.cap ?: InterfaceSummarizer.DEFAULT_BUDGET)

            Kind.PATHS -> {
                val paths = files(text).map { it.first }
                if (paths.isEmpty()) "(dosya yok)" else paths.joinToString("\n")
            }

            Kind.BLOCK -> {
                val first = CodeBlockParser.analyze(WebOutputCheck.canonical(text)).blocks.firstOrNull()
                cap((first?.content ?: text).trim(), ref.cap ?: DEFAULT_CAP)
            }

            Kind.FILE -> {
                val want = ref.path?.replace('\\', '/')?.trim('/')?.lowercase()
                val all = files(text)
                val hit = if (want == null) {
                    all.firstOrNull()
                } else {
                    all.firstOrNull { (p, _) ->
                        val n = p.replace('\\', '/').trim('/').lowercase()
                        n == want || n.endsWith("/$want")
                    }
                }
                if (hit == null) {
                    missing += "${ref.title}#${ref.path.orEmpty()}"
                    "[dosya yok: ${ref.title}${ref.path?.let { "#$it" }.orEmpty()}]"
                } else {
                    cap(hit.second, ref.cap ?: DEFAULT_CAP)
                }
            }

            Kind.VAR -> ""
        }
    }

    private fun files(text: String): List<Pair<String, String>> = CodeBlockParser.analyze(WebOutputCheck.canonical(text)).blocks.mapNotNull { b -> b.path?.let { it to b.content } }

    private fun cap(text: String, max: Int): String = if (text.length <= max) text else text.take(max).trimEnd() + "\n… (${text.length - max} karakter kısaltıldı)"

    /**
     * Yerleşik değişkenler (hem Türkçe hem İngilizce adlarla): çalıştırma kimliği, başlık, katalog, sıra, tarih, deneme.
     * [attempt]: 1'den başlar (doğrulama yeniden denemesinde artar).
     */
    fun builtins(
        runId: Long,
        title: String?,
        catalog: String?,
        index: Int,
        nowMs: Long,
        attempt: Int,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<String, String> {
        val date = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().toString()
        val m = LinkedHashMap<String, String>()
        fun put(value: String, vararg names: String) = names.forEach { m[normName(it)] = value }
        put(runId.toString(), "run_id", "çalıştırma")
        put(title.orEmpty(), "title", "başlık")
        put(catalog.orEmpty(), "catalog", "katalog")
        put(index.toString(), "index", "sıra")
        put(date, "date", "tarih")
        put(attempt.toString(), "attempt", "deneme")
        return m
    }

    // --- yazım denetimi (prompt düzenleyici) ----------------------------------------------------------------------

    /**
     * Bir prompt metnindeki bağlama sorunlarını bulur. [earlier]: bu promptTAN ÖNCE sıralanmış başlıklar (çıktıları
     * çözülebilir), [others]: setteki diğer tüm başlıklar (yalnız yanlış sıra uyarısı için).
     */
    fun lint(text: String, earlier: Collection<String>, others: Collection<String>): List<DirectiveIssue> {
        val d = FlowDirectivesParser.parse(text)
        val issues = ArrayList(d.issues)
        val before = earlier.map { FlowDirectivesParser.normalize(it) }.toSet()
        val elsewhere = others.map { FlowDirectivesParser.normalize(it) }.toSet()
        for (t in d.dependencies) {
            val n = FlowDirectivesParser.normalize(t)
            when {
                n in before -> Unit
                n in elsewhere -> issues += DirectiveIssue(IssueCode.FORWARD_SOURCE, t)
                else -> issues += DirectiveIssue(IssueCode.UNKNOWN_SOURCE, t)
            }
        }
        for (v in d.variables) {
            if (references(v.value).any { it.kind != Kind.VAR }) issues += DirectiveIssue(IssueCode.OUTPUT_IN_VARIABLE, v.name)
        }
        return issues.distinct()
    }
}
