package com.kriptoncod.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled

/**
 * Yükleme/işlem göstergesi: dönen çember yerine animasyonlu logo; çeşitler (yörünge, dalga, çizgi) sırayla döner.
 * Animasyonlar kapalıysa logo sönük ve hareketsiz görünür (hareket içeren hiçbir şey çizilmez).
 */
@Composable
fun KriptonLoader(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val animationsOn = LocalAnimationsEnabled.current
    KriptonCodLogo(
        modifier = modifier,
        logoSize = size,
        color = if (animationsOn) color else color.copy(alpha = 0.6f),
        mood = LogoMood.Working,
    )
}
