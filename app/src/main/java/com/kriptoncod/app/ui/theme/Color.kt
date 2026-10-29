package com.kriptoncod.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kriptoncod.app.domain.ThemePreference

// Tek renkli (gri-beyaz) temalar: logo gibi gümüş/beyaz tonlar. Koyu sürümün zemini AMOLED için saf siyahtır.
// Yalnız hata rengi (kırmızı) işlevsel olarak renkli kalır.

@Immutable
class KriptonColors(
    val nodeFill: Color,
    val nodeBorder: Color,
    val arrow: Color,
    val arrowFill: Color,
    val danger: Color,
    val dangerFill: Color,
    val muted: Color,
)

/** Bir temanın tüm renkleri; Material şeması ve [KriptonColors] buradan türetilir. */
@Immutable
internal class ThemeSpec(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val success: Color,
    val successFill: Color,
    val danger: Color,
    val dangerFill: Color,
)

internal object Palette {
    /** Koyu (AMOLED): saf siyah zemin + gümüş/beyaz vurgu; yüzeyler siyahtan yalnız birkaç kademe açık. */
    val Dark = ThemeSpec(
        isDark = true,
        background = Color(0xFF000000),
        surface = Color(0xFF0B0B0C),
        raised = Color(0xFF151516),
        border = Color(0xFF2E2E31),
        text = Color(0xFFF1F1F2),
        muted = Color(0xFF8D8D92),
        accent = Color(0xFFE4E4E7),
        onAccent = Color(0xFF000000),
        success = Color(0xFFC4C4C9),
        successFill = Color(0xFF111112),
        danger = Color(0xFFF2706B),
        dangerFill = Color(0xFF1F0F0F),
    )

    /** Açık: kırık beyaz zemin + grafit vurgu (beyaz üstünde okunur). */
    val Light = ThemeSpec(
        isDark = false,
        background = Color(0xFFF5F5F6),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFECECEE),
        border = Color(0xFFD2D2D6),
        text = Color(0xFF121213),
        muted = Color(0xFF68686E),
        accent = Color(0xFF2B2B2F),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF4A4A50),
        successFill = Color(0xFFE6E6E9),
        danger = Color(0xFFD13B34),
        dangerFill = Color(0xFFFCE8E7),
    )
}

/** [systemDark]: SYSTEM seçiliyken cihazın koyu modda olup olmadığı. */
internal fun ThemePreference.spec(systemDark: Boolean): ThemeSpec = when (this) {
    ThemePreference.SYSTEM -> if (systemDark) Palette.Dark else Palette.Light
    ThemePreference.DARK -> Palette.Dark
    ThemePreference.LIGHT -> Palette.Light
}

internal fun ThemeSpec.toKriptonColors() = KriptonColors(
    nodeFill = raised,
    nodeBorder = border,
    arrow = success,
    arrowFill = successFill,
    danger = danger,
    dangerFill = dangerFill,
    muted = muted,
)
