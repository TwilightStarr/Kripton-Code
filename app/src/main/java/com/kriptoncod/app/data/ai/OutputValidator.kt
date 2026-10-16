package com.kriptoncod.app.data.ai

import com.kriptoncod.app.data.run.CodeBlockParser

/**
 * Üretilen (zaten normalize edilmiş) metnin kullanılabilir olup olmadığını denetler.
 * Yalnız yapıya bakar; kodun doğruluğuna karışmaz.
 */
object OutputValidator {

    enum class Problem {
        /** Metin boş. */
        EMPTY,

        /** Kod bloğu açılmış ama kapanmamış: çıktı yarıda kesilmiş. */
        UNCLOSED_FENCE,

        /** Prompt yollu kod blokları istedi ama hiç yollu blok yok. */
        MISSING_CODE_BLOCKS,
    }

    /** Birleştirme / nihai paket prompt'ları bu işareti içerir (bkz. PromptDispatcher, FinalAiAssembler). */
    const val PATH_FENCE_MARKER = "```tam/dosya/yolu"

    /** Prompt kod bloğu biçimi istiyor mu. */
    fun expectsCodeBlocks(prompt: String): Boolean = PATH_FENCE_MARKER in prompt

    fun check(text: String, expectCodeBlocks: Boolean): Problem? {
        if (text.isBlank()) return Problem.EMPTY
        val analysis = CodeBlockParser.analyze(text)
        if (analysis.unclosedFence) return Problem.UNCLOSED_FENCE
        if (expectCodeBlocks && analysis.blocks.none { it.path != null }) return Problem.MISSING_CODE_BLOCKS
        return null
    }
}
