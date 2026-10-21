package com.kriptoncod.app.data.run

/**
 * Prompt metninin başına yazılabilen yönergeler (veritabanı değişikliği gerektirmez). Her yönerge kendi satırında,
 * `[[anahtar: değer]]` biçimindedir; ilk yönerge olmayan satırdan itibaren metin görevin kendisidir.
 *
 * Akış:
 *     [[girdi: Ana sayfa, Menü]]   bu prompt, başlığı verilen ÖNCEKİ promptların çıktısının ARAYÜZ ÖZETİNİ de görür
 *                                  (onlar bitene kadar bekler; yalnız daha önce sıralanmış promptlar seçilebilir)
 *     [[ilet: hayır]]              bu promptun çıktısı katalog/nihai birleştirmeye GİTMEZ
 *     [[kaynak: zorunlu]]          bağlı kaynaklardan biri başarısızsa bu düğüm AI çağrılmadan düşer
 *                                  (varsayılan: isteğe bağlı — eksik kaynak notla belirtilip devam edilir)
 *
 * Değişkenler (ayrıntı: [PromptTemplate]):
 *     [[değişken: uygulama = Kervan Çağı]]   sonraki promptlarda (ve bu promptta) `{{uygulama}}` olarak kullanılır
 *     Metin içinde: {{uygulama}}  {{dil|Kotlin}} (varsayılanlı)  {{çıktı:Başlık}}  {{özet:Başlık}}
 *                   {{dosya:Başlık#yol/dosya.kt}}  {{yollar:Başlık}}  {{blok:Başlık}}
 *
 * Doğrulama ve yeniden deneme (ayrıntı: [OutputRule], [RetryRules]):
 *     [[doğrula: min:200, içerir:"fun main", dosya, yer-tutucu-yok]]
 *     [[tekrar: 2]]                kural bozulursa en çok 2 kez yeniden üret
 *     [[tekrar-bekle: 10sn]]       denemeler arası bekleme (en çok 2 dk)
 *     [[tekrar-hesap: aynı]]       yeniden denemede aynı hesapta kal (varsayılan: değiştir)
 *     [[tekrar-ipucu: hayır]]      reddedilme nedenlerini isteme ekleme (varsayılan: evet)
 *     [[hatada: devam]]            denemeler bitince son çıktıyı yine de kabul et (varsayılan: dur → düğüm düşer)
 *     [[hata-tekrar: 5]]           sunucu/ağ hatasında bu düğüm için en çok deneme (varsayılan: genel sınır)
 *
 * İngilizce karşılıkları: `input`, `share`, `sources: required`, `var`, `validate`, `retry`, `retry-delay`,
 * `retry-account: same`, `retry-hint: no`, `on-fail: continue`, `error-retry`. Yönergeler AI'a gönderilmeden
 * önce metinden çıkarılır.
 */
data class FlowDirectives(
    val inputs: List<String>,
    val share: Boolean,
    val body: String,
    val variables: List<VariableDef> = emptyList(),
    val rules: List<OutputRule> = emptyList(),
    val retry: RetryRules = RetryRules(),
    val requireSources: Boolean = false,
    /** Ayrıştırılamayan yönergeler (düğüm yine çalışır; ilgili yönerge yok sayılır). */
    val issues: List<DirectiveIssue> = emptyList(),
) {
    val isDefault: Boolean
        get() = inputs.isEmpty() && share && variables.isEmpty() && rules.isEmpty() &&
            retry == RetryRules() && !requireSources && issues.isEmpty()

    /**
     * Bu promptun çıktısına bağımlı olduğu başlıklar: `[[girdi:]]` + gövdedeki `{{çıktı:..}}`, `{{özet:..}}`,
     * `{{dosya:..}}`, `{{yollar:..}}`, `{{blok:..}}` başvuruları. Sıra korunur, tekrarlar atılır.
     */
    val dependencies: List<String>
        get() {
            val seen = HashSet<String>()
            val out = ArrayList<String>()
            for (t in inputs + PromptTemplate.referencedTitles(body)) {
                if (seen.add(FlowDirectivesParser.normalize(t))) out += t
            }
            return out
        }

    /** Gövdesi boş, yalnız değişken tanımlayan düğüm: AI çağrılmaz. */
    val isDefinitionOnly: Boolean get() = body.isBlank() && variables.isNotEmpty()
}

data class VariableDef(val name: String, val value: String)

/** `[[tekrar...]]` ailesi: kalite (doğrulama) yeniden denemesi ve düğüm bazlı hata sınırı. */
data class RetryRules(
    /** Doğrulama kuralı bozulduğunda en çok kaç kez yeniden üretilir (0 = yeniden deneme yok). */
    val maxRetries: Int = 0,
    /** Yeniden denemeler arası bekleme. */
    val delayMs: Long = 0,
    /** true: yeniden denemede başka hesap tercih edilir; false: aynı hesap. */
    val switchAccount: Boolean = true,
    /** true: reddedilme nedenleri isteme eklenir. */
    val feedback: Boolean = true,
    /** Denemeler bitince kural hâlâ bozuksa: durup düğümü düşür ya da son çıktıyı kabul et. */
    val onExhausted: OnExhausted = OnExhausted.STOP,
    /** Sunucu/ağ hatasında bu düğüm için en çok deneme; null = genel sınır. */
    val maxErrorAttempts: Int? = null,
)

enum class OnExhausted { STOP, CONTINUE }

enum class IssueCode {
    BAD_VARIABLE,
    DUPLICATE_VARIABLE,
    BAD_RULE,
    BAD_REGEX,
    BAD_NUMBER,
    BAD_DURATION,
    BAD_CHOICE,
    UNKNOWN_SOURCE,
    FORWARD_SOURCE,
    OUTPUT_IN_VARIABLE,
}

/** [arg]: sorunlu yönergenin/başlığın kendisi (arayüzde gösterilir). */
data class DirectiveIssue(val code: IssueCode, val arg: String)

object FlowDirectivesParser {

    private val LINE = Regex("""^\s*\[\[\s*([\p{L}-]+)\s*:\s*(.*?)\s*]]\s*$""")
    private val NO = setOf("hayır", "hayir", "hayır.", "no", "false", "yok", "0")
    private val YES = setOf("evet", "yes", "true", "1", "var", "e")
    private val NAME = Regex("""^[\p{L}_][\p{L}\p{N}_.-]*$""")

    private enum class Key { INPUT, SHARE, VAR, VALIDATE, RETRY, RETRY_DELAY, RETRY_ACCOUNT, RETRY_HINT, ON_FAIL, ERROR_RETRY, SOURCES }

    private val KEYS: Map<String, Key> = mapOf(
        "girdi" to Key.INPUT, "input" to Key.INPUT,
        "ilet" to Key.SHARE, "share" to Key.SHARE,
        "değişken" to Key.VAR, "degisken" to Key.VAR, "var" to Key.VAR, "set" to Key.VAR,
        "doğrula" to Key.VALIDATE, "dogrula" to Key.VALIDATE, "validate" to Key.VALIDATE,
        "tekrar" to Key.RETRY, "retry" to Key.RETRY,
        "tekrar-bekle" to Key.RETRY_DELAY, "retry-delay" to Key.RETRY_DELAY,
        "tekrar-hesap" to Key.RETRY_ACCOUNT, "retry-account" to Key.RETRY_ACCOUNT,
        "tekrar-ipucu" to Key.RETRY_HINT, "retry-hint" to Key.RETRY_HINT,
        "hatada" to Key.ON_FAIL, "on-fail" to Key.ON_FAIL,
        "hata-tekrar" to Key.ERROR_RETRY, "error-retry" to Key.ERROR_RETRY,
        "kaynak" to Key.SOURCES, "sources" to Key.SOURCES,
    )

    /** Kalite yeniden denemesi ve hata denemesi için üst sınırlar (kontrolsüz maliyeti önler). */
    const val MAX_RETRIES = 10
    const val MAX_ERROR_ATTEMPTS = 60
    const val MAX_DELAY_MS = 120_000L

    fun parse(text: String): FlowDirectives {
        val lines = text.replace("\r\n", "\n").split('\n')
        val inputs = mutableListOf<String>()
        val vars = LinkedHashMap<String, VariableDef>()
        val rules = mutableListOf<OutputRule>()
        val issues = mutableListOf<DirectiveIssue>()
        var share = true
        var requireSources = false
        var retry = RetryRules()
        var idx = 0
        var any = false
        while (idx < lines.size) {
            val line = lines[idx]
            if (line.isBlank()) {
                idx++
                continue
            }
            val m = LINE.matchEntire(line) ?: break
            val key = KEYS[m.groupValues[1].lowercase()] ?: break
            val value = m.groupValues[2]
            any = true
            when (key) {
                Key.INPUT -> value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { inputs += it }

                Key.SHARE -> if (value.trim().lowercase() in NO) share = false

                Key.VAR -> {
                    val eq = value.indexOf('=')
                    val name = if (eq > 0) value.substring(0, eq).trim() else ""
                    if (eq <= 0 || !NAME.matches(name)) {
                        issues += DirectiveIssue(IssueCode.BAD_VARIABLE, value)
                    } else {
                        val key2 = PromptTemplate.normName(name)
                        if (key2 in vars) issues += DirectiveIssue(IssueCode.DUPLICATE_VARIABLE, name)
                        vars[key2] = VariableDef(name, unquote(value.substring(eq + 1).trim()))
                    }
                }

                Key.VALIDATE -> for (item in splitList(value)) parseRule(item, issues)?.let { rules += it }

                Key.RETRY -> number(value, 0, MAX_RETRIES, issues)?.let { retry = retry.copy(maxRetries = it) }

                Key.RETRY_DELAY -> duration(value)?.let { retry = retry.copy(delayMs = it.coerceAtMost(MAX_DELAY_MS)) }
                    ?: run { issues += DirectiveIssue(IssueCode.BAD_DURATION, value) }

                Key.RETRY_ACCOUNT -> when (value.trim().lowercase()) {
                    "değiştir", "degistir", "switch", "farklı", "farkli", "other" -> retry = retry.copy(switchAccount = true)
                    "aynı", "ayni", "same" -> retry = retry.copy(switchAccount = false)
                    else -> issues += DirectiveIssue(IssueCode.BAD_CHOICE, "tekrar-hesap: $value")
                }

                Key.RETRY_HINT -> when (value.trim().lowercase()) {
                    in NO -> retry = retry.copy(feedback = false)
                    in YES -> retry = retry.copy(feedback = true)
                    else -> issues += DirectiveIssue(IssueCode.BAD_CHOICE, "tekrar-ipucu: $value")
                }

                Key.ON_FAIL -> when (value.trim().lowercase()) {
                    "dur", "stop", "düş", "dus", "fail" -> retry = retry.copy(onExhausted = OnExhausted.STOP)
                    "devam", "continue", "kabul", "accept" -> retry = retry.copy(onExhausted = OnExhausted.CONTINUE)
                    else -> issues += DirectiveIssue(IssueCode.BAD_CHOICE, "hatada: $value")
                }

                Key.ERROR_RETRY -> number(value, 1, MAX_ERROR_ATTEMPTS, issues)?.let { retry = retry.copy(maxErrorAttempts = it) }

                Key.SOURCES -> when (value.trim().lowercase()) {
                    "zorunlu", "required", "evet", "yes" -> requireSources = true
                    "isteğe bağlı", "isteğe-bağlı", "istege bagli", "optional", "hayır", "hayir", "no" -> requireSources = false
                    else -> issues += DirectiveIssue(IssueCode.BAD_CHOICE, "kaynak: $value")
                }
            }
            idx++
        }
        if (!any) return FlowDirectives(emptyList(), true, text)
        val body = lines.drop(idx).joinToString("\n").trim()
        val variables = vars.values.toList()
        // Yalnız değişken tanımlayan düğüm hiçbir şey üretmez: birleştirmeye girmesi anlamsızdır.
        val effectiveShare = if (body.isBlank() && variables.isNotEmpty()) false else share
        return FlowDirectives(
            inputs = inputs.distinct(),
            share = effectiveShare,
            body = body,
            variables = variables,
            rules = rules,
            retry = retry,
            requireSources = requireSources,
            issues = issues,
        )
    }

    fun normalize(title: String?): String = title.orEmpty().trim().lowercase()

    // --- yardımcılar -------------------------------------------------------------------------------------------

    private fun unquote(s: String): String {
        if (s.length >= 2 && ((s.first() == '"' && s.last() == '"') || (s.first() == '\'' && s.last() == '\''))) {
            return s.substring(1, s.length - 1)
        }
        return s
    }

    /** Virgülle ayırır; çift tırnak içindeki virgüller ayraç değildir. */
    internal fun splitList(value: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        for (c in value) {
            when {
                c == '"' -> {
                    quoted = !quoted
                    cur.append(c)
                }

                c == ',' && !quoted -> {
                    out += cur.toString().trim()
                    cur.clear()
                }

                else -> cur.append(c)
            }
        }
        out += cur.toString().trim()
        return out.filter { it.isNotEmpty() }
    }

    private fun number(value: String, min: Int, max: Int, issues: MutableList<DirectiveIssue>): Int? {
        val n = value.trim().toIntOrNull()
        if (n == null || n < min) {
            issues += DirectiveIssue(IssueCode.BAD_NUMBER, value)
            return null
        }
        return n.coerceAtMost(max)
    }

    /** `20`, `20s`, `20sn`, `2dk`, `2m`, `1sa`, `1h` → ms (yalnız sayı: saniye). Geçersizse null. */
    internal fun duration(value: String): Long? {
        val m = Regex("""^\s*(\d+)\s*([a-zçğıöşü]*)\s*$""", RegexOption.IGNORE_CASE).matchEntire(value) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        val unit = when (m.groupValues[2].lowercase()) {
            "", "s", "sn", "sec", "saniye" -> 1_000L
            "ms" -> 1L
            "m", "dk", "min", "dakika" -> 60_000L
            "h", "sa", "saat" -> 3_600_000L
            else -> return null
        }
        return n * unit
    }

    private fun parseRule(item: String, issues: MutableList<DirectiveIssue>): OutputRule? {
        val colon = item.indexOf(':')
        val name = (if (colon >= 0) item.substring(0, colon) else item).trim().lowercase()
        val arg = if (colon >= 0) unquote(item.substring(colon + 1).trim()) else ""
        fun bad(): OutputRule? {
            issues += DirectiveIssue(IssueCode.BAD_RULE, item)
            return null
        }
        return when (name) {
            "min" -> arg.toIntOrNull()?.takeIf { it >= 0 }?.let { OutputRule.MinChars(it) } ?: bad()
            "max" -> arg.toIntOrNull()?.takeIf { it > 0 }?.let { OutputRule.MaxChars(it) } ?: bad()
            "içerir", "icerir", "contains" -> if (arg.isEmpty()) bad() else OutputRule.Contains(arg)
            "içermez", "icermez", "not-contains" -> if (arg.isEmpty()) bad() else OutputRule.NotContains(arg)
            "regex", "desen" -> {
                if (arg.isEmpty()) {
                    bad()
                } else {
                    runCatching { OutputRule.Matches(arg) }.getOrElse {
                        issues += DirectiveIssue(IssueCode.BAD_REGEX, arg)
                        null
                    }
                }
            }

            "dosya", "file" -> OutputRule.HasFile(arg.ifEmpty { null })
            "yer-tutucu-yok", "no-placeholders" -> OutputRule.NoPlaceholders
            "kesilmemiş", "kesilmemis", "complete" -> OutputRule.Complete
            else -> bad()
        }
    }
}
