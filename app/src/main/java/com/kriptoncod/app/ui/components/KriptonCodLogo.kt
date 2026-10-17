package com.kriptoncod.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Düğüm noktasının altıgen çevresinde bir tur atma süresi. */
private const val LAP_MS = 3200f

/** En geniş "silinmiş" aralık ve yumuşak geçiş genişliği (derece). */
private const val MAX_GAP_DEG = 85f
private const val FADE_DEG = 50f
private const val STOP_COUNT = 72

/**
 * Tek renkli, tek çizgili logo: sağ kenarı açık bir altıgen ("C" harfi) ve açıklıkta bir düğüm noktası.
 * Başlatıcı ikonuyla (res/drawable/ic_launcher_foreground.xml) aynı geometridir; renk aktif temadan gelir.
 *
 * [busy] true iken (ve animasyonlar açıksa) düğüm noktası altıgenin çevresinde yumuşakça dolanır:
 * geçtiği yerdeki çizgiyi siler, çizgi ise noktanın arkasından yavaşça geri gelir. [busy] false olunca
 * nokta turunu tamamlayıp yerine oturur; logo hiçbir zaman yarım kalmış görünmez.
 */
@Composable
fun KriptonCodLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    busy: Boolean = false,
) {
    val description = stringResource(R.string.logo_description)
    val animationsOn = LocalAnimationsEnabled.current
    val active = busy && animationsOn

    // 0 = dinlenme (nokta açıklıkta), 0..1 arası = tur ilerlemesi.
    val phase = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            while (true) {
                phase.animateTo(1f, tween((LAP_MS * (1f - phase.value)).roundToInt().coerceAtLeast(1), easing = LinearEasing))
                phase.snapTo(0f)
            }
        } else if (phase.value > 0f) {
            // Animasyon kapatıldıysa hemen dinlenmeye geç; yoksa turu aynı hızda bitir.
            if (animationsOn) {
                phase.animateTo(1f, tween((LAP_MS * (1f - phase.value)).roundToInt().coerceAtLeast(1), easing = LinearEasing))
            }
            phase.snapTo(0f)
        }
    }

    Canvas(modifier = modifier.size(logoSize).semantics { contentDescription = description }) {
        // 108x108'lik başlatıcı ikonun ortadaki 22..86 penceresi (64 birim) tuvale ölçeklenir.
        val k = size.minDimension / 64f
        val ox = -22f * k
        val oy = -22f * k
        fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)

        val path = Path().apply {
            val a = p(76.5f, 41f)
            moveTo(a.x, a.y)
            listOf(p(54f, 28f), p(31.5f, 41f), p(31.5f, 67f), p(54f, 80f), p(76.5f, 67f)).forEach { lineTo(it.x, it.y) }
        }
        val stroke = Stroke(width = 9f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val t = phase.value
        val center = p(54f, 54f)

        if (t <= 0f || t >= 1f) {
            drawPath(path, color, style = stroke)
            drawCircle(color, radius = 4.5f * k, center = p(76.5f, 54f))
            return@Canvas
        }

        // Zarf: tur başında/sonunda 0, ortasında 1 -> silinen aralık yumuşakça açılıp kapanır.
        val env = sin(PI * t).toFloat()
        val gap = MAX_GAP_DEG * env
        val fade = FADE_DEG * env
        val dotDeg = 360f * t // saat yönünde; 0° = sağ (dinlenme konumu)

        // Çizginin görünürlüğü, noktaya göre açının fonksiyonudur: noktanın hemen arkası silik,
        // daha gerisi yavaşça geri gelir; noktanın önünde de çizgi yaklaşırken solar.
        val stops = Array(STOP_COUNT + 1) { i ->
            val angle = 360f * i / STOP_COUNT
            val behind = ((dotDeg - angle) % 360f + 360f) % 360f
            (i.toFloat() / STOP_COUNT) to color.copy(alpha = color.alpha * lineVisibility(behind, gap, fade))
        }
        drawPath(path, Brush.sweepGradient(*stops, center = center), style = stroke)

        // Nokta: altıgenin kenarı boyunca (merkezden apothem / cos) ilerler.
        val rad = dotDeg * (PI.toFloat() / 180f)
        val nearestNormal = (dotDeg / 60f).roundToInt() * 60f
        val r = 22.5f / cos((dotDeg - nearestNormal) * (PI.toFloat() / 180f))
        val dot = p(54f + r * cos(rad), 54f + r * sin(rad))
        drawCircle(color.copy(alpha = color.alpha * 0.16f * env), radius = 9.5f * k, center = dot)
        drawCircle(color, radius = 4.5f * k * (1f + 0.12f * env), center = dot)
    }
}

/** [behind]: noktanın arkasındaki açısal uzaklık (0..360). Dönen değer 0 (görünmez) .. 1 (tam görünür). */
internal fun lineVisibility(behind: Float, gap: Float, fade: Float): Float = when {
    gap <= 0f -> 1f
    behind < gap -> 0f
    fade > 0f && behind < gap + fade -> smooth((behind - gap) / fade)
    fade > 0f && behind > 360f - fade -> smooth((360f - behind) / fade)
    else -> 1f
}

internal fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}
