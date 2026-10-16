package com.kriptoncod.app.domain

data class CatalogPrompt(val title: String, val body: String)

data class Catalog(val name: String, val prompts: List<CatalogPrompt>) {
    /** Katalogun toplam metin uzunluğu; iş yükü tahmini için kullanılır. */
    val size: Int get() = prompts.sumOf { it.body.length }
}

/**
 * Kataloglama biçimi (kullanıcının zip'i ve planlayıcı AI'ın çıktısı aynı biçimi kullanır):
 *
 *     ## KATALOG: Arayüz
 *     ### PROMPT: Ana sayfa
 *     Ana sayfa için ayrıntılı talimat...
 *     ### PROMPT: Menü
 *     Menü için talimat...
 *     ## KATALOG: Sunucu
 *     ### PROMPT: API
 *     ...
 *
 * Zip girdisi: her üst klasör bir katalog, içindeki her dosya bir prompttur (kökteki dosyalar "genel"
 * kataloğuna girer). Kökte yukarıdaki biçimde yazılmış bir metin dosyası da olabilir.
 */
object CatalogParser {
    const val DEFAULT_CATALOG = "genel"

    private val CATALOG_RE = Regex("""^##\s*KATALOG\s*:\s*(.+?)\s*$""", RegexOption.IGNORE_CASE)
    private val PROMPT_RE = Regex("""^###\s*PROMPT\s*:\s*(.*?)\s*$""", RegexOption.IGNORE_CASE)
    private val TEXT_EXT = setOf("txt", "md", "markdown", "prompt", "text")

    /** Metni kataloglara ayırır; hiç katalog başlığı yoksa boş liste döner. */
    fun parseText(text: String): List<Catalog> {
        val result = linkedMapOf<String, MutableList<CatalogPrompt>>()
        var catalog: String? = null
        var title: String? = null
        val buf = StringBuilder()

        fun flush() {
            val c = catalog
            val body = buf.toString().trim()
            if (c != null && body.isNotEmpty()) {
                result.getOrPut(c) { mutableListOf() } += CatalogPrompt(title?.takeIf { it.isNotBlank() } ?: c, body)
            }
            buf.clear()
            title = null
        }

        for (line in text.lineSequence()) {
            val cat = CATALOG_RE.find(line)
            if (cat != null) {
                flush()
                catalog = cat.groupValues[1]
                result.getOrPut(cat.groupValues[1]) { mutableListOf() }
                continue
            }
            val pr = PROMPT_RE.find(line)
            if (pr != null && catalog != null) {
                flush()
                title = pr.groupValues[1]
                continue
            }
            if (catalog != null) buf.appendLine(line)
        }
        flush()
        return result.filter { it.value.isNotEmpty() }.map { Catalog(it.key, it.value) }
    }

    /** Zip girdilerini (yol, içerik) kataloglara çevirir. */
    fun parseZip(entries: List<Pair<String, ByteArray>>): List<Catalog> {
        val files = entries
            .map { (path, bytes) -> path.replace('\\', '/').trim('/') to bytes }
            .filter { (path, _) ->
                path.isNotEmpty() &&
                    path.split('/').none { it.startsWith(".") || it == "__MACOSX" } &&
                    path.substringAfterLast('.', "").lowercase() in TEXT_EXT
            }
            .sortedBy { it.first }
        if (files.isEmpty()) return emptyList()

        // Tüm dosyalar tek bir üst klasörün içindeyse (zip'i klasör olarak sıkıştırma) o klasörü at.
        val firstSegments = files.map { it.first.substringBefore('/') }.toSet()
        val strip = firstSegments.size == 1 && files.all { it.first.contains('/') }
        val normalized = files.map { (p, b) -> (if (strip) p.substringAfter('/') else p) to b }

        val result = linkedMapOf<String, MutableList<CatalogPrompt>>()
        for ((path, bytes) in normalized) {
            val text = bytes.toString(Charsets.UTF_8).trim()
            if (text.isEmpty()) continue
            if (!path.contains('/')) {
                val parsed = if (text.contains("KATALOG", ignoreCase = true)) parseText(text) else emptyList()
                if (parsed.isNotEmpty()) {
                    parsed.forEach { c -> result.getOrPut(c.name) { mutableListOf() } += c.prompts }
                    continue
                }
            }
            val catalog = if (path.contains('/')) path.substringBefore('/') else DEFAULT_CATALOG
            val rest = if (path.contains('/')) path.substringAfter('/') else path
            val title = rest.substringBeforeLast('.')
            result.getOrPut(catalog) { mutableListOf() } += CatalogPrompt(title, text)
        }
        return result.filter { it.value.isNotEmpty() }.map { Catalog(it.key, it.value) }
    }

    /** Planlayıcı AI'a gönderilecek yönerge + ham promptlar (aynı biçimde, tek "genel" katalogda). */
    fun plannerPrompt(prompts: List<CatalogPrompt>): String = buildString {
        appendLine("Aşağıda bir yazılım işini oluşturan ayrıntılı promptlar var.")
        appendLine("Görevin: bu promptları birbiriyle ilgili KATALOGLARA ayırmak. Her katalog, farklı bir yapay zekaya")
        appendLine("paralel olarak verilecek. Prompt metinlerini koru (gerekirse netleştir), hiçbirini atlama.")
        appendLine("Yanıtında YALNIZCA şu biçimi kullan, başka açıklama yazma (başlıklar satır başında olmalı):")
        appendLine()
        appendLine("    ## KATALOG: <katalog adı>")
        appendLine("    ### PROMPT: <prompt başlığı>")
        appendLine("    <prompt metni>")
        appendLine()
        appendLine("GİRDİ:")
        appendLine("## KATALOG: $DEFAULT_CATALOG")
        prompts.forEach {
            appendLine("### PROMPT: ${it.title.lineSequence().firstOrNull().orEmpty()}")
            appendLine(it.body)
        }
    }
}

/** İş yüküne göre hesap seçimi: güçlü hesap daha çok / daha büyük iş alır. */
object WorkAssigner {
    data class Candidate(val accountId: Long, val power: Int)

    /**
     * Her iş için hesap kimliği döndürür (en büyük iş önce; iş, tamamlanma yükü
     * (yük + boyut) / güç en küçük olan hesaba verilir). Aday yoksa hepsi null.
     */
    fun assign(sizes: List<Int>, candidates: List<Candidate>): List<Long?> {
        if (candidates.isEmpty()) return sizes.map { null }
        val load = DoubleArray(candidates.size)
        val out = arrayOfNulls<Long>(sizes.size)
        sizes.indices.sortedByDescending { sizes[it] }.forEach { i ->
            val size = sizes[i].coerceAtLeast(1)
            var best = 0
            var bestCost = Double.MAX_VALUE
            candidates.forEachIndexed { k, c ->
                val cost = (load[k] + size) / (c.power.coerceAtLeast(1))
                if (cost < bestCost) { bestCost = cost; best = k }
            }
            load[best] += size.toDouble()
            out[i] = candidates[best].accountId
        }
        return out.toList()
    }
}
