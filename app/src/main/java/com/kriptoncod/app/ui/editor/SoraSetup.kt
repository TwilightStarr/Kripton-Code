package com.kriptoncod.app.ui.editor

import android.content.Context
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.tm4e.core.registry.IThemeSource

/** Uygulamanın paletinden türetilen düzenleyici renkleri (ARGB). Sözdizimi renkleri TextMate temasından gelir. */
class EditorChrome(
    val dark: Boolean,
    val background: Int,
    val text: Int,
    val muted: Int,
    val raised: Int,
    val border: Int,
    val accent: Int,
    val danger: Int,
    val selection: Int,
    /** TextMate tema adı; null ise [dark] durumuna göre kripton-dark / kripton-light. */
    val syntax: String? = null,
) {
    /** Gerçekten kullanılacak TextMate tema adı. */
    fun syntaxName(): String = syntax ?: if (dark) SoraSetup.THEME_DARK else SoraSetup.THEME_LIGHT

    fun key(): Int = listOf(syntaxName(), dark, background, text, muted, raised, border, accent, danger, selection).hashCode()

    /** Tema üstüne uygulama arayüzünü (satır numarası, imleç, açılır pencereler, kaydırma çubuğu…) bindirir. */
    fun applyTo(s: EditorColorScheme) {
        val clear = 0x00000000
        s.setColor(EditorColorScheme.WHOLE_BACKGROUND, background)
        s.setColor(EditorColorScheme.LINE_NUMBER_BACKGROUND, background)
        s.setColor(EditorColorScheme.LINE_NUMBER, muted)
        s.setColor(EditorColorScheme.LINE_NUMBER_CURRENT, text)
        s.setColor(EditorColorScheme.LINE_DIVIDER, clear)
        s.setColor(EditorColorScheme.TEXT_NORMAL, text)
        s.setColor(EditorColorScheme.CURRENT_LINE, raised)
        s.setColor(EditorColorScheme.SELECTION_INSERT, accent)
        s.setColor(EditorColorScheme.SELECTION_HANDLE, accent)
        s.setColor(EditorColorScheme.SELECTED_TEXT_BACKGROUND, selection)
        s.setColor(EditorColorScheme.MATCHED_TEXT_BACKGROUND, selection)
        s.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_FOREGROUND, text)
        s.setColor(EditorColorScheme.HIGHLIGHTED_DELIMITERS_UNDERLINE, accent)
        s.setColor(EditorColorScheme.NON_PRINTABLE_CHAR, border)
        s.setColor(EditorColorScheme.SCROLL_BAR_THUMB, border)
        s.setColor(EditorColorScheme.SCROLL_BAR_THUMB_PRESSED, muted)
        s.setColor(EditorColorScheme.SCROLL_BAR_TRACK, clear)
        s.setColor(EditorColorScheme.BLOCK_LINE, border)
        s.setColor(EditorColorScheme.BLOCK_LINE_CURRENT, muted)
        s.setColor(EditorColorScheme.SIDE_BLOCK_LINE, border)
        s.setColor(EditorColorScheme.STICKY_SCROLL_DIVIDER, border)
        s.setColor(EditorColorScheme.UNDERLINE, accent)
        s.setColor(EditorColorScheme.LINE_NUMBER_PANEL, raised)
        s.setColor(EditorColorScheme.LINE_NUMBER_PANEL_TEXT, text)
        s.setColor(EditorColorScheme.COMPLETION_WND_BACKGROUND, raised)
        s.setColor(EditorColorScheme.COMPLETION_WND_CORNER, border)
        s.setColor(EditorColorScheme.COMPLETION_WND_TEXT_PRIMARY, text)
        s.setColor(EditorColorScheme.COMPLETION_WND_TEXT_SECONDARY, muted)
        s.setColor(EditorColorScheme.COMPLETION_WND_TEXT_MATCHED, accent)
        s.setColor(EditorColorScheme.COMPLETION_WND_ITEM_CURRENT, border)
        s.setColor(EditorColorScheme.TEXT_ACTION_WINDOW_BACKGROUND, raised)
        s.setColor(EditorColorScheme.TEXT_ACTION_WINDOW_ICON_COLOR, text)
        s.setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BACKGROUND, raised)
        s.setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_BRIEF_MSG, text)
        s.setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_DETAILED_MSG, muted)
        s.setColor(EditorColorScheme.DIAGNOSTIC_TOOLTIP_ACTION, accent)
        s.setColor(EditorColorScheme.HOVER_BACKGROUND, raised)
        s.setColor(EditorColorScheme.HOVER_BORDER, border)
        s.setColor(EditorColorScheme.HOVER_TEXT_NORMAL, text)
        s.setColor(EditorColorScheme.SIGNATURE_BACKGROUND, raised)
        s.setColor(EditorColorScheme.SIGNATURE_BORDER, border)
        s.setColor(EditorColorScheme.SIGNATURE_TEXT_NORMAL, text)
        s.setColor(EditorColorScheme.PROBLEM_ERROR, danger)
    }
}

/** TextMate dil/tema kayıtlarını (assets/textmate) bir kez yükler ve düzenleyiciye dil/renk şeması üretir. */
object SoraSetup {
    const val THEME_DARK = "kripton-dark"
    const val THEME_LIGHT = "kripton-light"

    @Volatile private var ready = false

    @Synchronized
    fun ensure(context: Context) {
        if (ready) return
        ready = true // yükleme başarısız olsa bile her açılışta yeniden denenip takılmasın; düz metin olarak çalışır
        runCatching {
            val files = FileProviderRegistry.getInstance()
            files.addFileProvider(AssetsFileResolver(context.applicationContext.assets))
            val themes = ThemeRegistry.getInstance()
            val all = listOf(THEME_DARK to true, THEME_LIGHT to false) +
                EditorThemes.all.mapNotNull { t -> t.syntax?.let { it to (t.spec?.isDark ?: true) } }
            for ((name, dark) in all) {
                // Tek tema yüklenemese bile diğerleri çalışsın.
                runCatching {
                    val path = "textmate/$name.json"
                    val model = ThemeModel(IThemeSource.fromInputStream(files.tryGetInputStream(path), path, null), name)
                    model.isDark = dark
                    themes.loadTheme(model)
                }
            }
            GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")
        }
    }

    /** Uygulama paletiyle boyanmış TextMate renk şeması. Düzenleyici kapanınca [release] çağrılmalıdır. */
    fun createScheme(chrome: EditorChrome): TextMateColorScheme? = runCatching {
        val themes = ThemeRegistry.getInstance()
        themes.setTheme(chrome.syntaxName())
        TextMateColorScheme.create(themes).also { chrome.applyTo(it) }
    }.getOrNull()

    fun release(scheme: EditorColorScheme?) {
        if (scheme is TextMateColorScheme) runCatching { ThemeRegistry.getInstance().removeListener(scheme) }
    }

    /** [scope] için dil; kapsam yok/yüklenemezse null (düz metin). */
    fun createLanguage(scope: String?): TextMateLanguage? {
        if (scope == null) return null
        return runCatching { TextMateLanguage.create(scope, true) }.getOrNull()
    }
}
