package com.kriptoncod.app.ui.fx

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import com.kriptoncod.app.ui.theme.LocalEffectsEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Arka plan aurası: temanın vurgu renginde, çok sönük, çok yavaş süzülen üç ışık lekesi.
 *  - Boştayken sakin ve sönüktür (siyah-beyaz temada gri bir parıltı; renkli bir şey eklemez).
 *  - Bir çalışma sürerken [busy] ile parlaklaşır ve yaklaşık 5 sn'lik bir nefes alır.
 *  - [flashKey] artınca ([flashColor] renginde) üstten yayılan tek seferlik bir dalga oynar: çalışma başarıyla
 *    bitince yeşil, hata verince kırmızı.
 *
 * Yalnız bir tuvaldir: etkileşimi engellemez. [LocalEffectsEnabled] kapalıysa hiçbir şey çizilmez ve hiçbir
 * animasyon çalışmaz.
 */
@Composable
fun AuraBackdrop(
    busy: Boolean,
    flashKey: Int,
    flashColor: Color,
    modifier: Modifier = Modifier,
) {
    if (!LocalEffectsEnabled.current) return
    val tint = MaterialTheme.colorScheme.primary
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val strength by animateFloatAsState(if (busy) 1f else 0f, tween(900), label = "auraBusy")
    val phase = rememberInfiniteTransition(label = "aura").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing)),
        label = "auraPhase",
    )
    val flash = remember { Animatable(1f) }
    LaunchedEffect(flashKey) {
        if (flashKey > 0) {
            flash.snapTo(0f)
            flash.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing))
        }
    }
    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val base = if (dark) 0.075f else 0.05f
        val alpha = base * (1f + 0.9f * strength)
        val t = phase.value * 2f * PI.toFloat()
        val radius = max(w, h) * 0.55f
        val breath = 1f + 0.10f * strength * sin(t * 8f)
        blob(tint, w * (0.25f + 0.18f * sin(t)), h * (0.18f + 0.10f * cos(t * 2f)), radius * breath, alpha)
        blob(tint, w * (0.80f + 0.12f * cos(t * 2f)), h * (0.55f + 0.15f * sin(t * 3f)), radius * 0.9f * breath, alpha * 0.8f)
        blob(tint, w * (0.40f + 0.20f * sin(t * 3f)), h * (0.95f + 0.05f * cos(t)), radius * 0.8f, alpha * 0.7f)
        val f = flash.value
        if (f < 1f) {
            val k = (1f - f) * (1f - f)
            blob(flashColor, w / 2f, h * 0.18f, radius * (0.3f + 1.1f * f), 0.26f * k)
        }
    }
}

private fun DrawScope.blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
    if (alpha <= 0f || radius <= 0f) return
    val center = Offset(cx, cy)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = color.alpha * alpha), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}
