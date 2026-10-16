package com.kriptoncod.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.kriptoncod.app.domain.ThemePreference

// Sade, koyu temalar: nötr gri zemin + tek bir yumuşak vurgu rengi. Mavi kullanılmaz.

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
    /** Grafit: nötr koyu gri + zümrüt vurgu. */
    val Graphite = ThemeSpec(
        background = Color(0xFF131416),
        surface = Color(0xFF1A1B1E),
        raised = Color(0xFF222428),
        border = Color(0xFF33363B),
        text = Color(0xFFEDEDEF),
        muted = Color(0xFF9A9DA5),
        accent = Color(0xFF4ADE9B),
        onAccent = Color(0xFF07140D),
        success = Color(0xFF4ADE9B),
        successFill = Color(0xFF15261D),
        danger = Color(0xFFF2706B),
        dangerFill = Color(0xFF2E1A1A),
    )

    /** Siyah-Beyaz: saf siyah (AMOLED) + beyaz vurgu (varsayılan). */
    val Black = ThemeSpec(
        background = Color(0xFF000000),
        surface = Color(0xFF0C0C0C),
        raised = Color(0xFF171717),
        border = Color(0xFF2B2B2B),
        text = Color(0xFFF2F2F2),
        muted = Color(0xFF8E8E93),
        accent = Color(0xFFF2F2F2),
        onAccent = Color(0xFF000000),
        success = Color(0xFF5BD69B),
        successFill = Color(0xFF0F1F17),
        danger = Color(0xFFF2706B),
        dangerFill = Color(0xFF2A1515),
    )

    /** Adaçayı: yeşilimsi koyu gri + sönük adaçayı vurgu. */
    val Sage = ThemeSpec(
        background = Color(0xFF111412),
        surface = Color(0xFF181C19),
        raised = Color(0xFF1F2420),
        border = Color(0xFF303832),
        text = Color(0xFFE8ECE8),
        muted = Color(0xFF97A199),
        accent = Color(0xFFA9CBAE),
        onAccent = Color(0xFF0E1710),
        success = Color(0xFF7FD6A0),
        successFill = Color(0xFF16251B),
        danger = Color(0xFFF2706B),
        dangerFill = Color(0xFF2B1A19),
    )
}

internal fun ThemePreference.spec(): ThemeSpec = when (this) {
    ThemePreference.GRAPHITE -> Palette.Graphite
    ThemePreference.BLACK -> Palette.Black
    ThemePreference.SAGE -> Palette.Sage
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
