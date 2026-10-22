package com.kriptoncod.app.data.run

/**
 * Bir düğümün çıktısının KABUL edilmesi için kurallar (`[[doğrula: ...]]`). Saf mantık.
 *
 * Kural bozulursa düğüm "başarısız" sayılmaz; [RetryRules] izin verdiği kadar yeniden üretilir
 * (bkz. PromptDispatcher). Kurallar bilerek muhafazakârdır ve yalnız çıktının kendisine bakar.
 */
sealed interface OutputRule {
    /** Kuralın kullanıcıya/AI'a gösterilecek kısa adı. */
    val label: String

    /** @return çıktı kabul edilmiyorsa nedeni, edilmiyorsa null. */
    fun violation(output: String): String?

    data class MinChars(val n: Int) : OutputRule {
        override val label get() = "min:$n"
        override fun violation(output: String) = if (output.trim().length < n) "çıktı en az $n karakter olmalı (${output.trim().length} var)" else null
    }

    data class MaxChars(val n: Int) : OutputRule {
        override val label get() = "max:$n"
        override fun violation(output: String) = if (output.length > n) "çıktı en çok $n karakter olmalı (${output.length} var)" else null
    }

    data class Contains(val text: String) : OutputRule {
        override val label get() = "içerir:$text"
        override fun violation(output: String) = if (!output.contains(text, ignoreCase = true)) "çıktıda \"$text\" geçmeli" else null
    }

    data class NotContains(val text: String) : OutputRule {
        override val label get() = "içermez:$text"
        override fun violation(output: String) = if (output.contains(text, ignoreCase = true)) "çıktıda \"$text\" geçmemeli" else null
    }

    data class Matches(val pattern: String) : OutputRule {
        private val regex = Regex(pattern)
        override val label get() = "regex:$pattern"
        override fun violation(output: String) = if (!regex.containsMatchIn(output)) "çıktı /$pattern/ desenine uymalı" else null
    }

    /** En az bir yollu kod bloğu (dosya) bulunmalı; [path] verilirse o yol bulunmalı (sonek eşleşmesi). */
    data class HasFile(val path: String? = null) : OutputRule {
        override val label get() = if (path == null) "dosya" else "dosya:$path"
        override fun violation(output: String): String? {
            val blocks = CodeBlockParser.parse(output)
            if (path == null) return if (blocks.isEmpty()) "çıktıda en az bir dosya bloğu (```yol/dosya.uzantı) olmalı" else null
            val want = path.replace('\\', '/').trim('/').lowercase()
            val found = blocks.any { b ->
                val p = b.path.replace('\\', '/').trim('/').lowercase()
                p == want || p.endsWith("/$want")
            }
            return if (found) null else "çıktıda \"$path\" dosyası olmalı"
        }
    }

    /** "Geri kalanı aynı" / `// ...` gibi yer tutucular olmamalı. */
    data object NoPlaceholders : OutputRule {
        override val label get() = "yer-tutucu-yok"
        override fun violation(output: String): String? {
            val hits = PlaceholderDetector.findInText(output)
            if (hits.isEmpty()) return null
            val first = hits.first()
            return "${hits.size} yerde yer tutucu var (ör. ${first.path}:${first.line} → \"${first.text.take(60)}\"); dosyalar eksiksiz yazılmalı"
        }
    }

    /** Kapanmamış kod çiti (kesik çıktı) olmamalı. */
    data object Complete : OutputRule {
        override val label get() = "kesilmemiş"
        override fun violation(output: String) =
            if (CodeBlockParser.analyze(output).unclosedFence) "çıktı kesilmiş (kapanmamış kod bloğu)" else null
    }
}

object OutputRules {

    /** Çıktı kabul edilmiyorsa nedenlerin listesi; boşsa kabul. */
    fun check(output: String, rules: List<OutputRule>): List<String> = rules.mapNotNull { it.violation(output) }

    /** Yeniden denemede AI'a eklenen geri bildirim bölümü. */
    fun feedback(reasons: List<String>, attempt: Int, max: Int): String = buildString {
        appendLine()
        appendLine()
        appendLine("---")
        appendLine("ÖNCEKİ DENEME REDDEDİLDİ (deneme $attempt/${max + 1}). Nedenler:")
        reasons.forEach { appendLine("- $it") }
        append("Görevi bu eksikleri gidererek baştan ve eksiksiz yaz; yalnızca istenen çıktıyı ver.")
    }

    /** Sonucu tek satırda özetler (günlük/rapor). */
    fun describe(rules: List<OutputRule>): String = rules.joinToString(", ") { it.label }
}
