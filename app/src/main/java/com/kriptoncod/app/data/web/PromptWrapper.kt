package com.kriptoncod.app.data.web

/**
 * Web sohbetine giden prompt'a sağlayıcının çıktı biçimine uygun talimat ekler.
 * Çıktı, projenin kod bloğu biçimine (```tam/yol) uymak zorundadır.
 */
object PromptWrapper {

    enum class Mode {
        /** Önce tek zip arşivi istenir (Claude/ChatGPT dosya üretir). */
        ZIP,

        /** Dosyalar kod bloğu olarak istenir (zip üretilemezse ya da Kimi). */
        CODE_BLOCKS,
    }

    private const val BLOCK_RULES =
        "Return EVERY file as its own fenced code block whose info string is the full relative file path, " +
            "for example ```src/main/App.kt. Write complete files only: no placeholders, no '...', no omitted parts, " +
            "and no explanations outside the code blocks."

    fun wrap(provider: WebProvider, prompt: String, mode: Mode): String = when (mode) {
        Mode.ZIP ->
            "$prompt\n\n---\nDeliver ALL files together as a single downloadable .zip archive " +
                "(create the file, do not just describe it). Keep the folder structure with full relative paths. " +
                "Write complete files only."
        Mode.CODE_BLOCKS ->
            if (provider.output == OutputStyle.LONG_CODE_BLOCK) {
                "$prompt\n\n---\n$BLOCK_RULES If the answer is long, keep going until every file is finished; " +
                    "if you are cut off I will say 'continue'."
            } else {
                "$prompt\n\n---\n$BLOCK_RULES"
            }
    }

    /** Kesilen yanıtı sürdürmek için gönderilen mesaj. */
    fun continuation(): String =
        "Continue exactly where you stopped. Do not repeat finished files. If you were in the middle of a file, " +
            "restart that file with the same ```path fence and write it complete, then finish the remaining files."
}
