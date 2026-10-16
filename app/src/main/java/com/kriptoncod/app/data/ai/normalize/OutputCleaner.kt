package com.kriptoncod.app.data.ai.normalize

import com.kriptoncod.app.data.run.CodeBlockParser

/** Normalizer'ların paylaştığı, sağlayıcıdan bağımsız metin temizliği. */
internal object OutputCleaner {

    private const val TAGS = "think|thinking|thought|reasoning"
    private val LEADING_BLOCK = Regex("""^\s*<($TAGS)>.*?</\1>\s*""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    private val LEADING_OPEN = Regex("""^\s*<($TAGS)>""", RegexOption.IGNORE_CASE)
    private val ORPHAN_CLOSE = Regex("""</($TAGS)>""", RegexOption.IGNORE_CASE)
    private val ANY_OPEN = Regex("""<($TAGS)>""", RegexOption.IGNORE_CASE)

    private val SPECIAL_ASCII = Regex("""<\|[^|<>\n]{1,48}\|>""")
    private val SPECIAL_FULLWIDTH = Regex("""<｜[^｜<>\n]{1,48}｜>""")
    private val TOOL_SECTION = Regex(
        """<\|tool_calls_section_begin\|>.*?(?:<\|tool_calls_section_end\|>|$)""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val OUTER_FENCE = Regex("""^(`{3,}|~{3,})\s*(?:markdown|md|text|plaintext)?\s*$""", RegexOption.IGNORE_CASE)

    fun clean(
        raw: String,
        aggressiveThinking: Boolean = false,
        specialTokens: Boolean = false,
        toolSections: Boolean = false,
    ): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n').replace("\uFEFF", "").replace("\u200B", "")
        if (toolSections) text = TOOL_SECTION.replace(text, "")
        if (specialTokens) text = SPECIAL_FULLWIDTH.replace(SPECIAL_ASCII.replace(text, ""), "")
        text = stripThinking(text, aggressiveThinking)
        text = unwrapOuterFence(text.trim())
        return text.trim()
    }

    /**
     * Baştaki `<think>…</think>` (ve eşdeğeri) blokları atılır. Kapanışı olmayan baştaki blok tüm yanıtın
     * muhakeme olduğu anlamına gelir → boş döner (bozuk yanıt; yeniden denenir).
     * [aggressive]: açılışı olmayan `</think>` varsa (bazı DeepSeek/Kimi sunucuları) ondan sonrası alınır.
     */
    private fun stripThinking(input: String, aggressive: Boolean): String {
        var text = input
        while (true) {
            val m = LEADING_BLOCK.find(text) ?: break
            text = text.substring(m.range.last + 1)
        }
        if (LEADING_OPEN.containsMatchIn(text)) return ""
        if (aggressive && !ANY_OPEN.containsMatchIn(text)) {
            val last = ORPHAN_CLOSE.findAll(text).lastOrNull()
            if (last != null) text = text.substring(last.range.last + 1)
        }
        return text
    }

    /**
     * Bazı modeller tüm yanıtı ```markdown … ``` içine sarar. İçinde yollu kod blokları varsa sarmalayıcı atılır;
     * yoksa (yanıtın kendisi belgedir) dokunulmaz.
     */
    private fun unwrapOuterFence(text: String): String {
        val lines = text.split('\n')
        if (lines.size < 3) return text
        val open = OUTER_FENCE.matchEntire(lines.first().trim()) ?: return text
        val marks = open.groupValues[1]
        val last = lines.last().trim()
        if (last.length < marks.length || !last.all { it == marks[0] }) return text
        val inner = lines.subList(1, lines.size - 1).joinToString("\n")
        val hasPathBlocks = CodeBlockParser.analyze(inner).blocks.any { it.path != null }
        return if (hasPathBlocks) inner else text
    }
}
