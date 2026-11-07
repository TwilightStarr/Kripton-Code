package com.kriptoncod.app.ui.editor

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.kriptoncod.app.ui.theme.ThemeSpec

/**
 * Editör sekmesinin kendi temaları. Her tema hem arayüz paletini (araç çubuğu, sekmeler, iletişim kutuları)
 * hem de kod renklendirme temasını (assets/textmate/<syntax>.json) birlikte tanımlar; uygulamanın genel temasından bağımsızdır.
 * [spec] null ise uygulamanın o anki teması kullanılır ("Kripton").
 */
internal class EditorTheme(
    val id: String,
    val label: String,
    /** TextMate tema adı; null ise uygulamanın açık/koyu durumuna göre kripton-dark / kripton-light. */
    val syntax: String?,
    val spec: ThemeSpec?,
    /** Önizleme noktaları: zemin, vurgu, anahtar sözcük, metin (dizgi), sayı. */
    val swatch: List<Color>,
)

internal object EditorThemes {
    const val DEFAULT_ID = "kripton"

    private fun c(hex: Long) = Color(0xFF000000 or hex)

    private fun spec(
        dark: Boolean,
        bg: Long, surface: Long, raised: Long, border: Long, text: Long, muted: Long, accent: Long, onAccent: Long,
        success: Long, successFill: Long, danger: Long, dangerFill: Long,
    ) = ThemeSpec(
        isDark = dark,
        background = c(bg), surface = c(surface), raised = c(raised), border = c(border),
        text = c(text), muted = c(muted), accent = c(accent), onAccent = c(onAccent),
        success = c(success), successFill = c(successFill), danger = c(danger), dangerFill = c(dangerFill),
    )

    val all: List<EditorTheme> = listOf(
        EditorTheme("kripton", "Kripton", null, null, listOf(c(0x000000), c(0xE4E4E7), c(0x9FB4D9), c(0xA8C5A0), c(0xD6B98C))),
        EditorTheme(
            "obsidyen", "Obsidyen", "editor-obsidyen",
            spec(true, 0x0E0B16, 0x15111F, 0x1D1829, 0x35304A, 0xEDE9F7, 0x8F88A8, 0xB794F6, 0x120C20, 0xA6E3B8, 0x15251B, 0xFF7B8A, 0x2A1218),
            listOf(c(0x0E0B16), c(0xB794F6), c(0xC79BFF), c(0xA6E3B8), c(0xFFB86B)),
        ),
        EditorTheme(
            "kehribar", "Kehribar", "editor-kehribar",
            spec(true, 0x15110B, 0x1C170F, 0x251F15, 0x40362A, 0xF3E9D6, 0x9C8F78, 0xF2A93B, 0x1A1204, 0xB5D18A, 0x1A2112, 0xFF7A66, 0x2B130E),
            listOf(c(0x15110B), c(0xF2A93B), c(0xF2A93B), c(0xB5D18A), c(0xFF8F66)),
        ),
        EditorTheme(
            "zumrut", "Zümrüt", "editor-zumrut",
            spec(true, 0x07120F, 0x0C1A16, 0x112420, 0x24403A, 0xDDF3EA, 0x7FA397, 0x3DDC97, 0x03140D, 0x8EE3B8, 0x0F2A1F, 0xFF7B7B, 0x2A1212),
            listOf(c(0x07120F), c(0x3DDC97), c(0x3DDC97), c(0xC8E58C), c(0xFFB36B)),
        ),
        EditorTheme(
            "kiragi", "Kırağı", "editor-kiragi",
            spec(false, 0xF2F6FB, 0xFFFFFF, 0xE4ECF6, 0xC5D3E4, 0x14202E, 0x5C6E84, 0x2F6FED, 0xFFFFFF, 0x1F8A5B, 0xDDF2E8, 0xD13B34, 0xFCE8E7),
            listOf(c(0xF2F6FB), c(0x2F6FED), c(0x2F6FED), c(0x1F8A5B), c(0xC2570C)),
        ),
        EditorTheme(
            "kum", "Kum", "editor-kum",
            spec(false, 0xF8F2E7, 0xFFFBF3, 0xEEE4D1, 0xD8C9AC, 0x2A2014, 0x7A6A52, 0xB4581B, 0xFFFFFF, 0x5C7A2E, 0xE9F0D8, 0xC2362E, 0xFBE5E2),
            listOf(c(0xF8F2E7), c(0xB4581B), c(0xB4581B), c(0x5C7A2E), c(0xA8321F)),
        ),
    )

    /** Bilinmeyen/eski kimlik varsayılana düşer. */
    fun byId(id: String?): EditorTheme = all.firstOrNull { it.id == id } ?: all.first()

    /** Dosyada yüklenmesi gereken TextMate tema adları (SoraSetup kaydeder). */
    val syntaxNames: List<String> get() = all.mapNotNull { it.syntax }

    private const val PREFS = "code_editor"
    private const val KEY = "theme"

    fun load(context: Context): EditorTheme =
        byId(context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, DEFAULT_ID))

    fun save(context: Context, theme: EditorTheme) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, theme.id).apply()
    }
}
