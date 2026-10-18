package com.kriptoncod.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kriptoncod.app.domain.ThemePreference

// Sade temalar: nötr zemin + tek bir zümrüt vurgu. Koyu ve açık sürüm aynı kimliği taşır.

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
    /** Koyu: derin nötr zemin + zümrüt vurgu. */
    val Dark = ThemeSpec(
        isDark = true,
        background = Color(0xFF0E0F11),
        surface = Color(0xFF16171A),
        raised = Color(0xFF1E2024),
        border = Color(0xFF30333A),
        text = Color(0xFFEDEEF0),
        muted = Color(0xFF9A9FA8),
        accent = Color(0xFF4ADE9B),
        onAccent = Color(0xFF04130B),
        success = Color(0xFF4ADE9B),
        successFill = Color(0xFF13251C),
        danger = Color(0xFFF2706B),
        dangerFill = Color(0xFF2E1A1A),
    )

    /** Açık: kırık beyaz zemin + koyu zümrüt vurgu (beyaz üstünde okunur). */
    val Light = ThemeSpec(
        isDark = false,
        background = Color(0xFFF7F8F9),
        surface = Color(0xFFFFFFFF),
        raised = Color(0xFFEEF0F2),
        border = Color(0xFFD3D8DE),
        text = Color(0xFF16181B),
        muted = Color(0xFF626973),
        accent = Color(0xFF0B8A57),
        onAccent = Color(0xFFFFFFFF),
        success = Color(0xFF0B8A57),
        successFill = Color(0xFFE1F4EA),
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
