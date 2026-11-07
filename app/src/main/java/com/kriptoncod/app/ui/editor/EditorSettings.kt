package com.kriptoncod.app.ui.editor

import android.content.Context

/** Kod düzenleyicinin kullanıcı ayarları (SharedPreferences'ta saklanır). */
data class EditorSettings(
    val fontSizeSp: Float = 13f,
    val wordWrap: Boolean = false,
    val lineNumbers: Boolean = true,
    val showWhitespace: Boolean = false,
    val stickyScroll: Boolean = true,
    val highlightLine: Boolean = true,
    val highlightBrackets: Boolean = true,
    val autoIndent: Boolean = true,
    val autoClosePairs: Boolean = true,
    val autoComplete: Boolean = true,
    val symbolBar: Boolean = true,
    val tabWidth: Int = 4,
) {
    /** Geçersiz/eski değerleri sınırlar içine çeker. */
    fun normalized(): EditorSettings = copy(
        fontSizeSp = fontSizeSp.coerceIn(MIN_FONT, MAX_FONT),
        tabWidth = tabWidth.coerceIn(1, 8),
    )

    companion object {
        const val MIN_FONT = 9f
        const val MAX_FONT = 28f
        const val FONT_STEP = 1f
    }
}

object EditorPrefs {
    private const val PREFS = "code_editor"

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): EditorSettings {
        val p = prefs(context)
        val d = EditorSettings()
        return EditorSettings(
            fontSizeSp = p.getFloat("font", d.fontSizeSp),
            wordWrap = p.getBoolean("wrap", d.wordWrap),
            lineNumbers = p.getBoolean("lines", d.lineNumbers),
            showWhitespace = p.getBoolean("ws", d.showWhitespace),
            stickyScroll = p.getBoolean("sticky", d.stickyScroll),
            highlightLine = p.getBoolean("hlLine", d.highlightLine),
            highlightBrackets = p.getBoolean("hlBr", d.highlightBrackets),
            autoIndent = p.getBoolean("indent", d.autoIndent),
            autoClosePairs = p.getBoolean("pairs", d.autoClosePairs),
            autoComplete = p.getBoolean("complete", d.autoComplete),
            symbolBar = p.getBoolean("symbols", d.symbolBar),
            tabWidth = p.getInt("tab", d.tabWidth),
        ).normalized()
    }

    fun save(context: Context, s: EditorSettings) {
        val n = s.normalized()
        prefs(context).edit()
            .putFloat("font", n.fontSizeSp)
            .putBoolean("wrap", n.wordWrap)
            .putBoolean("lines", n.lineNumbers)
            .putBoolean("ws", n.showWhitespace)
            .putBoolean("sticky", n.stickyScroll)
            .putBoolean("hlLine", n.highlightLine)
            .putBoolean("hlBr", n.highlightBrackets)
            .putBoolean("indent", n.autoIndent)
            .putBoolean("pairs", n.autoClosePairs)
            .putBoolean("complete", n.autoComplete)
            .putBoolean("symbols", n.symbolBar)
            .putInt("tab", n.tabWidth)
            .apply()
    }
}
