package com.kriptoncod.app.data.web

import com.kriptoncod.app.data.run.CodeBlockParser

/**
 * Kesilen yanıtın "devam et" parçalarını birleştirir. Kimi gibi sağlayıcılar uzun kod bloğunu
 * araç/uzunluk sınırında keser; sonraki parça ya kodun devamıdır ya da yeni bir çitle başlar.
 */
object ContinuationMerger {

    /** [text] kapanmamış bir kod çiti ile bitiyorsa true: yanıt büyük olasılıkla yarım kesilmiştir. */
    fun endsInsideFence(text: String): Boolean = CodeBlockParser.analyze(text).unclosedFence

    fun merge(parts: List<String>): String {
        val nonEmpty = parts.filter { it.isNotBlank() }
        if (nonEmpty.isEmpty()) return ""
        var acc = nonEmpty.first().trimEnd()
        for (next in nonEmpty.drop(1)) {
            val n = next.trim('\n', '\r')
            acc = if (endsInsideFence(acc)) {
                if (startsWithFence(n)) {
                    // Yeni parça kendi çitiyle başlıyor: yarım bloğu kapat; aynı yola yazan sonraki blok öncekini ezer.
                    acc + "\n" + closingFence(acc) + "\n" + n
                } else {
                    acc + "\n" + n
                }
            } else {
                acc + "\n\n" + n
            }
        }
        return acc
    }

    private fun startsWithFence(s: String): Boolean = s.lineSequence().firstOrNull { it.isNotBlank() }?.trimStart()?.let {
        it.startsWith("```") || it.startsWith("~~~")
    } ?: false

    private fun closingFence(acc: String): String {
        // Açık çitin uzunluğunu bul: son açılış satırı.
        var len = 3
        var ch = '`'
        var open = false
        for (line in acc.lineSequence()) {
            val t = line.trimStart()
            val c = t.firstOrNull() ?: continue
            if (c != '`' && c != '~') continue
            val run = t.takeWhile { it == c }.length
            if (run < 3) continue
            val info = t.drop(run).trim()
            if (!open && !(c == '`' && '`' in info)) {
                open = true
                len = run
                ch = c
            } else if (open && c == ch && run >= len && info.isEmpty()) {
                open = false
            }
        }
        return ch.toString().repeat(len)
    }
}
