package com.kriptoncod.app.ui.components

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled

/** "Canlı" göstergesi: sürmekte olan işler için küçük nokta + dışa yayılıp sönen halka (animasyon kapalıysa yalnız nokta). */
@Composable
fun LiveDot(color: Color, modifier: Modifier = Modifier, dotSize: Dp = 6.dp) {
    val animated = LocalAnimationsEnabled.current
    val phase = if (animated) {
        rememberInfiniteTransition(label = "liveDot").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearOutSlowInEasing), RepeatMode.Restart),
            label = "liveDotPhase",
        )
    } else {
        null
    }
    Canvas(modifier.size(dotSize * 2.2f)) {
        val c = center
        val r = dotSize.toPx() / 2f
        val t = phase?.value ?: 0f
        if (phase != null) {
            drawCircle(color.copy(alpha = color.alpha * 0.6f * (1f - t)), radius = r + r * 1.6f * t, center = c, style = Stroke(width = 1.4.dp.toPx()))
        }
        drawCircle(color, radius = r, center = c)
    }
}
