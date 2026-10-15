package com.kriptoncod.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Kripton paleti: mavi düğüm, yeşil ok, nadiren kırmızı.
// NOT: lib/core/theme.dart yüklenmediği için değerler Kripton'un görsel kimliğine göre
// seçildi; birebir değerleri yalnız bu dosyada güncellemek yeterli.

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

internal object Palette {
    // Açık
    val LightBackground = Color(0xFFF4F7FD)
    val LightSurface = Color(0xFFFFFFFF)
    val LightNode = Color(0xFF2563EB)
    val LightNodeFill = Color(0xFFE8F0FE)
    val LightNodeBorder = Color(0xFF9DB2DA)
    val LightText = Color(0xFF0B1630)
    val LightMuted = Color(0xFF51607F)
    val LightArrow = Color(0xFF16A34A)
    val LightArrowFill = Color(0xFFE6F6EC)
    val LightDanger = Color(0xFFDC2626)
    val LightDangerFill = Color(0xFFFDECEC)

    // Koyu
    val DarkBackground = Color(0xFF0A1226)
    val DarkSurface = Color(0xFF111B36)
    val DarkNode = Color(0xFF6B9BFF)
    val DarkNodeFill = Color(0xFF14224A)
    val DarkNodeBorder = Color(0xFF2C4380)
    val DarkText = Color(0xFFE6ECFA)
    val DarkMuted = Color(0xFF9AA9C9)
    val DarkArrow = Color(0xFF34D399)
    val DarkArrowFill = Color(0xFF0E2A24)
    val DarkDanger = Color(0xFFF87171)
    val DarkDangerFill = Color(0xFF3A1519)
}

internal val LightKriptonColors = KriptonColors(
    nodeFill = Palette.LightNodeFill,
    nodeBorder = Palette.LightNodeBorder,
    arrow = Palette.LightArrow,
    arrowFill = Palette.LightArrowFill,
    danger = Palette.LightDanger,
    dangerFill = Palette.LightDangerFill,
    muted = Palette.LightMuted,
)

internal val DarkKriptonColors = KriptonColors(
    nodeFill = Palette.DarkNodeFill,
    nodeBorder = Palette.DarkNodeBorder,
    arrow = Palette.DarkArrow,
    arrowFill = Palette.DarkArrowFill,
    danger = Palette.DarkDanger,
    dangerFill = Palette.DarkDangerFill,
    muted = Palette.DarkMuted,
)
