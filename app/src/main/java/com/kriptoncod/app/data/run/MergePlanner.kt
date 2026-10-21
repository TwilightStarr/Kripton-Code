package com.kriptoncod.app.data.run

import com.kriptoncod.app.data.web.WebOutputCheck
import com.kriptoncod.app.data.web.ZipBlocks

/**
 * Birleştirme düğümlerini KODLA yapar; AI yalnızca gerçek çakışmada devreye girer.
 *
 *  1. Her kaynak çıktının ```yol/dosya blokları yola göre toplanır.
 *  2. Yalnız bir kaynakta olan ya da tüm kaynaklarda aynı olan dosya doğrudan alınır (AI görmez, değiştiremez, düşüremez).
 *  3. Aynı yolda FARKLI içerik varsa çakışmadır: yalnız o dosyaların sürümleri AI'a gider ([conflictPrompt]).
 *  4. AI cevabı kontrol edilir ([assemble]): boş ya da sürümlerin en uzununun %60'ından kısaysa en uzun sürüm korunur.
 *     Çakışma bütçeyi aşıyorsa AI'a hiç gitmez, en uzun sürüm alınır.
 */
object MergePlanner {

    /** Birleştirmeye girecek bir düğüm çıktısı. [name] okunur ad (prompt başlığı ya da katalog). */
    class Source(val name: String, val jobId: Long, val text: String)

    class Version(val source: String, val jobId: Long, val content: String)

    class Plan(
        /** Çakışmasız dosyalar (yol -> içerik), sıra korunur. */
        val files: LinkedHashMap<String, String>,
        /** AI'a gidecek çakışmalar. */
        val aiConflicts: LinkedHashMap<String, List<Version>>,
        /** Bütçeyi aştığı için kodla (en uzun sürüm) çözülecek çakışmalar. */
        val codeConflicts: LinkedHashMap<String, List<Version>>,
        val notes: List<String>,
    ) {
        val needsAi: Boolean get() = aiConflicts.isNotEmpty()
        val conflictCount: Int get() = aiConflicts.size + codeConflicts.size
    }

    class Assembled(val text: String, val notes: List<String>)

    /** AI'a gidecek çakışma sürümlerinin toplam karakter üst sınırı. */
    const val MAX_CONFLICT_CHARS = 60_000

    private const val SHORT_RATIO_PERCENT = 60

    fun plan(sources: List<Source>, maxConflictChars: Int = MAX_CONFLICT_CHARS): Plan {
        val notes = mutableListOf<String>()
        val byPath = linkedMapOf<String, MutableList<Version>>()
        for (s in sources) {
            val blocks = safeBlocks(s)
            if (blocks.isEmpty()) {
                // Dosya bloğu olmayan düz metin kaybolmasın: not dosyası olarak taşınır.
                val path = "notes/${sanitize(s.name)}_${s.jobId}.md"
                byPath.getOrPut(path) { mutableListOf() } += Version(s.name, s.jobId, s.text)
                continue
            }
            for ((path, content) in blocks) byPath.getOrPut(path) { mutableListOf() } += Version(s.name, s.jobId, content)
        }

        val files = linkedMapOf<String, String>()
        val conflicts = linkedMapOf<String, List<Version>>()
        for ((path, versions) in byPath) {
            val distinct = versions.distinctBy { normalize(it.content) }
            if (distinct.size == 1) files[path] = distinct.first().content else conflicts[path] = distinct
        }

        val ai = linkedMapOf<String, List<Version>>()
        val code = linkedMapOf<String, List<Version>>()
        var used = 0
        for ((path, versions) in conflicts) {
            val size = versions.sumOf { it.content.length }
            if (used + size <= maxConflictChars) {
                ai[path] = versions
                used += size
            } else {
                code[path] = versions
                notes += "Çakışma bütçeyi aştı: $path — AI'a gönderilmedi, en uzun sürüm alındı."
            }
        }
        return Plan(files, ai, code, notes)
    }

    /** AI'a gidecek istem: yalnız çakışan dosyaların sürümleri + çakışmasız dosyaların arayüz özeti (bağlam). */
    fun conflictPrompt(plan: Plan, header: String): String = buildString {
        appendLine(header)
        appendLine()
        appendLine(
            "Aşağıda AYNI yolda birbirinden farklı sürümleri olan dosyalar var. Her dosya için sürümleri " +
                "TEK nihai sürümde birleştir: hiçbir işlevi düşürme, hiçbir bölümü kısaltma, eksikleri tamamla. " +
                "Yalnızca bu dosyaları ```tam/dosya/yolu biçiminde kod bloklarıyla döndür; açıklama yazma. " +
                "Çakışmasız dosyalar zaten korunuyor: onları YAZMA.",
        )
        appendLine()
        for ((path, versions) in plan.aiConflicts) {
            appendLine("##### DOSYA: $path")
            versions.forEachIndexed { i, v ->
                appendLine("----- SÜRÜM ${i + 1} (kaynak: ${v.source}) -----")
                appendLine(v.content.trimEnd('\n'))
                appendLine("----- SÜRÜM ${i + 1} SONU -----")
            }
            appendLine()
        }
        if (plan.files.isNotEmpty()) {
            appendLine("##### BAĞLAM: çakışmasız dosyaların arayüz özeti (DEĞİŞTİRME, yeniden yazma)")
            appendLine(InterfaceSummarizer.summarizeFiles(plan.files.map { it.key to it.value }, InterfaceSummarizer.DEFAULT_BUDGET))
        }
    }

    /**
     * Nihai çıktıyı üretir: çakışmasız dosyalar + çözülmüş çakışmalar, ```yol bloklarıyla.
     * [aiOutput] null ise (AI çağrılmadı) AI'a gidecek çakışma da yoktur varsayılır; varsa en uzun sürüm alınır.
     */
    fun assemble(plan: Plan, aiOutput: String?): Assembled {
        val notes = plan.notes.toMutableList()
        val resolved = linkedMapOf<String, String>()
        resolved.putAll(plan.files)

        val ai = linkedMapOf<String, String>()
        if (aiOutput != null) {
            for ((p, c) in safeBlocks(Source("ai", -1, aiOutput))) ai[p] = c
        }

        for ((path, versions) in plan.aiConflicts) {
            val longest = versions.maxByOrNull { it.content.length }!!
            val got = ai[path]
            resolved[path] = when {
                got == null || got.isBlank() -> {
                    notes += "AI $path için sürüm döndürmedi; en uzun sürüm (${longest.source}) alındı."
                    longest.content
                }

                got.length * 100 < longest.content.length * SHORT_RATIO_PERCENT -> {
                    notes += "AI $path dosyasını çok kısalttı; en uzun sürüm (${longest.source}) alındı."
                    longest.content
                }

                else -> got
            }
        }
        for ((path, versions) in plan.codeConflicts) resolved[path] = versions.maxByOrNull { it.content.length }!!.content

        // AI'ın istenmeden eklediği yeni dosyalar kabul edilir; çakışmasız dosyaların üzerine yazılamaz.
        for ((path, content) in ai) {
            if (path in resolved) {
                if (path in plan.files && normalize(plan.files[path]!!) != normalize(content)) {
                    notes += "AI çakışmasız $path dosyasını değiştirmeye çalıştı; yok sayıldı."
                }
            } else if (content.isNotBlank()) {
                resolved[path] = content
                notes += "AI yeni dosya ekledi: $path"
            }
        }

        val text = buildString {
            if (plan.conflictCount > 0 || notes.isNotEmpty()) {
                append("<!-- kripton-birleştirme: ${plan.files.size} dosya çakışmasız alındı, ${plan.aiConflicts.size} çakışma AI ile, ")
                append("${plan.codeConflicts.size} çakışma kodla çözüldü")
                if (notes.isNotEmpty()) append("; ").append(notes.joinToString(" | ").replace("--", "—"))
                append(" -->\n\n")
            }
            append(resolved.entries.joinToString("\n\n") { (p, c) -> ZipBlocks.block(p, c) })
        }
        return Assembled(text, notes)
    }

    // --- yardımcılar ----------------------------------------------------------------------------

    private fun safeBlocks(s: Source): List<Pair<String, String>> {
        val parsed = CodeBlockParser.parse(WebOutputCheck.canonical(s.text))
        return parsed.mapNotNull { b ->
            val raw = if (b.path.startsWith("unnamed/part_")) "unnamed/job${s.jobId}_${b.path.removePrefix("unnamed/")}" else b.path
            when (val r = PathSafety.validate(raw)) {
                is PathSafety.Result.Safe -> r.normalized to b.content
                is PathSafety.Result.Rejected -> null
            }
        }
    }

    private fun normalize(s: String) = s.replace("\r\n", "\n").trimEnd()

    private fun sanitize(name: String) = name.replace(Regex("[^A-Za-z0-9_-]"), "_").trim('_').ifEmpty { "kaynak" }.take(40)
}
