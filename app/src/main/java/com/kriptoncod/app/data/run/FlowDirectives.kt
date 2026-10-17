package com.kriptoncod.app.data.run

/**
 * Prompt metninin başına yazılabilen akış yönergeleri (veritabanı değişikliği gerektirmez):
 *
 *     [[girdi: Ana sayfa, Menü]]   bu prompt, başlığı verilen ÖNCEKİ promptların çıktısını da görür
 *                                  (onlar bitene kadar bekler; yalnız daha önce sıralanmış promptlar seçilebilir)
 *     [[ilet: hayır]]              bu promptun çıktısı katalog/nihai birleştirmeye GİTMEZ
 *                                  (yalnız düğüm kaydı olarak saklanır)
 *
 * İngilizce karşılıkları: `input:` ve `share: no`. Yönergeler AI'a gönderilmeden önce metinden çıkarılır.
 */
data class FlowDirectives(
    val inputs: List<String>,
    val share: Boolean,
    val body: String,
) {
    val isDefault: Boolean get() = inputs.isEmpty() && share
}

object FlowDirectivesParser {

    private val LINE = Regex("""^\s*\[\[\s*(girdi|input|ilet|share)\s*:\s*(.*?)\s*]]\s*$""", RegexOption.IGNORE_CASE)
    private val NO = setOf("hayır", "hayir", "hayır.", "no", "false", "yok", "0")

    fun parse(text: String): FlowDirectives {
        val lines = text.replace("\r\n", "\n").split('\n')
        val inputs = mutableListOf<String>()
        var share = true
        var idx = 0
        while (idx < lines.size) {
            val line = lines[idx]
            if (line.isBlank()) { idx++; continue }
            val m = LINE.matchEntire(line) ?: break
            val value = m.groupValues[2]
            when (m.groupValues[1].lowercase()) {
                "girdi", "input" ->
                    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { inputs += it }
                else -> if (value.trim().lowercase() in NO) share = false
            }
            idx++
        }
        if (idx == 0 && inputs.isEmpty() && share) return FlowDirectives(emptyList(), true, text)
        val body = lines.drop(idx).joinToString("\n").trim()
        return FlowDirectives(inputs.distinct(), share, body)
    }

    fun normalize(title: String?): String = title.orEmpty().trim().lowercase()
}
