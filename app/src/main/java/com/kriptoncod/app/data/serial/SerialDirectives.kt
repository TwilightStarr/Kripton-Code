package com.kriptoncod.app.data.serial

import com.kriptoncod.app.data.db.PromptItem
import com.kriptoncod.app.data.run.FlowDirectivesParser
import com.kriptoncod.app.data.run.PromptTemplate
import com.kriptoncod.app.domain.Catalog

/**
 * Seri mod yönergeleri; prompt metninin BAŞINDA, kendi satırlarında yazılır ve AI'a gönderilmez:
 *
 *     [[yeni-sohbet]]        bu adım YENİ bir sohbette başlar (projenin güncel hâli dosya olarak verilir)
 *     [[dosyasız]]           bu adımdan dosya beklenmez (yalnız plan/inceleme); commit ve CI atlanır
 *
 * İngilizce karşılıkları: `new-chat`, `no-files`. Başka yönergeler ([[değişken: ...]] gibi) normal akıştaki gibi çalışır.
 * Yönergesiz prompt AYNI sohbette sürer.
 */
object SerialDirectives {

    data class Parsed(val body: String, val newChat: Boolean, val expectFiles: Boolean)

    private val LINE = Regex("""^\s*\[\[\s*([\p{L}-]+)\s*(?::\s*(.*?))?\s*\]\]\s*$""")
    private val NEW_CHAT = setOf("yeni-sohbet", "yeni-sohbet-aç", "new-chat", "newchat")
    private val NO_FILES = setOf("dosyasız", "dosyasiz", "no-files", "nofiles")
    private val OFF = setOf("hayır", "hayir", "no", "false", "0", "yok")

    fun parse(raw: String): Parsed {
        val lines = raw.replace("\r\n", "\n").split('\n')
        var newChat = false
        var expectFiles = true
        var idx = 0
        // Yalnız baştaki yönerge satırları taranır; bilinmeyen bir yönerge de taramayı durdurmaz (normal ayrıştırıcıya kalır).
        val rest = ArrayList<String>()
        var scanning = true
        while (idx < lines.size) {
            val line = lines[idx]
            if (scanning) {
                if (line.isBlank()) {
                    rest += line
                    idx++
                    continue
                }
                val m = LINE.matchEntire(line)
                if (m != null) {
                    val key = m.groupValues[1].lowercase()
                    val value = m.groupValues[2].trim().lowercase()
                    when (key) {
                        in NEW_CHAT -> {
                            newChat = value !in OFF
                            idx++
                            continue
                        }

                        in NO_FILES -> {
                            expectFiles = value in OFF
                            idx++
                            continue
                        }
                    }
                    rest += line
                    idx++
                    continue
                }
                scanning = false
            }
            rest += line
            idx++
        }
        return Parsed(rest.joinToString("\n").trim(), newChat, expectFiles)
    }
}

/**
 * Bir prompt setinden seri adımlar kurar: yönergeleri ayıklar, `[[değişken: ...]]` tanımlarını sırayla biriktirip
 * `{{ad}}` kullanımlarını çözer. `{{çıktı:...}}` gibi başka düğümlerin çıktısına başvurular seri modda KULLANILMAZ
 * (AI zaten aynı sohbette önceki çıktıları görür); çözülemeyen başvurular metinde olduğu gibi kalır.
 */
object SerialPlanner {

    fun plan(items: List<PromptItem>, nowMs: Long = System.currentTimeMillis(), runId: Long = 0L): List<SerialPrompt> = planEntries(items.sortedBy { it.order }.map { it.title to it.body }, nowMs, runId)

    /**
     * Zip kataloglarını (katalog sırasıyla, katalog içi prompt sırasıyla) seri adımlara çevirir.
     * Birden çok katalog varsa başlığın önüne katalog adı eklenir. Yönergeler ve değişkenler [plan] ile aynı çözülür.
     */
    fun planCatalogs(catalogs: List<Catalog>, nowMs: Long = System.currentTimeMillis(), runId: Long = 0L): List<SerialPrompt> {
        val multi = catalogs.size > 1
        val entries = catalogs.flatMap { c ->
            c.prompts.map { p -> (if (multi) "${c.name} · ${p.title}" else p.title) to p.body }
        }
        return planEntries(entries, nowMs, runId)
    }

    /** [entries]: (başlık, ham metin) çiftleri, gönderim sırasıyla. */
    fun planEntries(entries: List<Pair<String, String>>, nowMs: Long = System.currentTimeMillis(), runId: Long = 0L): List<SerialPrompt> {
        val vars = LinkedHashMap<String, String>()
        val out = ArrayList<SerialPrompt>()
        entries.forEachIndexed { index, (title, body) ->
            val serial = SerialDirectives.parse(body)
            val flow = FlowDirectivesParser.parse(serial.body)
            flow.variables.forEach { vars[PromptTemplate.normName(it.name)] = it.value }
            if (flow.isDefinitionOnly || flow.body.isBlank()) return@forEachIndexed
            val builtins = PromptTemplate.builtins(runId, title, null, index, nowMs, 1)
            val rendered = PromptTemplate.render(flow.body, PromptTemplate.Context(vars = vars, builtins = builtins)).text
            out += SerialPrompt(
                title = title,
                body = rendered.trim(),
                newChat = serial.newChat,
                expectFiles = serial.expectFiles,
            )
        }
        return out
    }
}
