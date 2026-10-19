package com.kriptoncod.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.domain.RunStatus
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animasyonlar açık mı? Ayarlar > Görünüm > "Animasyonlar" anahtarı ile sistemin "animasyonları kaldır"
 * seçeneğinin birleşimidir. Tüm hareketli bileşenler (logo, yükleme göstergesi, ekran geçişleri, kart girişleri,
 * basma efekti) bunu okur; kapalıyken hiçbir şey hareket etmez.
 */
val LocalAnimationsEnabled = compositionLocalOf { true }

/** Arka planda bir çalışma sürüyor mu? Logo bunu görünce "çalışıyor" animasyonunu oynatır. */
val LocalAppBusy = compositionLocalOf { false }

/**
 * Arka plan aurası, dokunma dalgaları ve kutlama efektleri açık mı? Ayarlar > "Arka plan ve dokunma efektleri".
 * Ana anahtar ([LocalAnimationsEnabled]) kapalıysa bu da kapalıdır.
 */
val LocalEffectsEnabled = compositionLocalOf { true }

/** Logo sürprizleri (easter egg) açık mı? Ayarlar > "Logo sürprizleri". Ana anahtar kapalıysa kapalıdır. */
val LocalEggsEnabled = compositionLocalOf { true }

/** Kullanıcı açısından "iş yürüyor" sayılan çalışma durumları (duraklatılmış/bitmiş değil). */
fun RunStatus.isInProgress(): Boolean = when (this) {
    RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING -> true
    RunStatus.PAUSED, RunStatus.DONE, RunStatus.FAILED, RunStatus.CANCELLED -> false
}

/** Aynı anda en fazla bu kadar öğe kademeli girer; sonrakiler gecikmesiz (uzun listeler yavaşlamasın). */
private const val MAX_STAGGER = 8
private const val STAGGER_STEP_MS = 55L

/**
 * Ekrana ilk girişte aşağıdan yukarı yaylanarak belirir (hafif büyüme + taşma); [index] ile öğeler sırayla
 * (kademeli) girer.
 */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    if (!LocalAnimationsEnabled.current) {
        this
    } else {
        val fade = remember { Animatable(0f) }
        val lift = remember { Animatable(0f) }
        LaunchedEffect(Unit) {
            delay(index.coerceIn(0, MAX_STAGGER) * STAGGER_STEP_MS)
            launch { fade.animateTo(1f, tween(durationMillis = 380, easing = LinearOutSlowInEasing)) }
            lift.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
        }
        this.graphicsLayer {
            val l = lift.value
            alpha = fade.value
            translationY = (1f - l) * 28.dp.toPx()
            val s = 0.94f + 0.06f * l
            scaleX = s
            scaleY = s
        }
    }
}

/** Seçildiği anda yaylanarak "zıplar" (gezinme simgeleri, seçili çipler). İlk çizimde oynamaz. */
fun Modifier.popOnSelect(selected: Boolean): Modifier = composed {
    if (!LocalAnimationsEnabled.current) {
        this
    } else {
        val factor = remember { Animatable(1f) }
        var first by remember { mutableStateOf(true) }
        LaunchedEffect(selected) {
            if (first) {
                first = false
                return@LaunchedEffect
            }
            if (selected) {
                factor.snapTo(0.76f)
                factor.animateTo(1f, spring(dampingRatio = 0.36f, stiffness = Spring.StiffnessMedium))
            }
        }
        this.graphicsLayer {
            scaleX = factor.value
            scaleY = factor.value
        }
    }
}

/** Yavaşça yukarı aşağı süzülür (boş durum simgeleri). */
fun Modifier.floating(amplitude: Dp = 4.dp, periodMs: Int = 3200): Modifier = composed {
    if (!LocalAnimationsEnabled.current) {
        this
    } else {
        val phase = rememberInfiniteTransition(label = "float").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearEasing)),
            label = "floatPhase",
        )
        this.graphicsLayer {
            translationY = sin(phase.value * 2f * PI.toFloat()) * amplitude.toPx()
        }
    }
}

/** 0'dan [target]'a sayarak yükselen tam sayı (animasyonlar kapalıysa doğrudan [target]). */
@Composable
fun animatedCount(target: Int): Int {
    if (!LocalAnimationsEnabled.current) return target
    val shown = remember { Animatable(0f) }
    LaunchedEffect(target) { shown.animateTo(target.toFloat(), tween(durationMillis = 900, easing = FastOutSlowInEasing)) }
    return shown.value.roundToInt()
}

/**
 * Kartın kenarında dolanan, kuyruğu sönen ışık (logodaki "yörünge" hareketinin kart hâli).
 * [active] false veya animasyonlar kapalıysa hiçbir şey çizilmez.
 */
fun Modifier.cometBorder(
    active: Boolean,
    color: Color,
    cornerRadius: Dp = 16.dp,
    strokeWidth: Dp = 2.dp,
): Modifier = composed {
    if (!active || !LocalAnimationsEnabled.current) {
        this
    } else {
        val phase = rememberInfiniteTransition(label = "comet").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
            label = "cometPhase",
        )
        this.drawWithCache {
            val inset = strokeWidth.toPx() / 2f
            val radius = (cornerRadius.toPx() - inset).coerceAtLeast(0f)
            val outline = Path().apply {
                addRoundRect(RoundRect(inset, inset, size.width - inset, size.height - inset, CornerRadius(radius)))
            }
            val measure = PathMeasure().apply { setPath(outline, false) }
            val total = measure.length
            val seg = Path()
            val width = strokeWidth.toPx()
            onDrawWithContent {
                drawContent()
                if (total <= 0f) return@onDrawWithContent
                val head = phase.value * total
                val tail = total * 0.26f
                val n = 14
                for (i in 0 until n) {
                    val a = head - tail * (1f - i / n.toFloat())
                    val b = head - tail * (1f - (i + 1) / n.toFloat())
                    val t = (i + 1) / n.toFloat()
                    val c = color.copy(alpha = color.alpha * t * t)
                    if (a >= 0f) {
                        strokeSegment(measure, seg, a, b, c, width)
                    } else {
                        strokeSegment(measure, seg, total + a, total, c, width)
                        strokeSegment(measure, seg, 0f, b, c, width)
                    }
                }
            }
        }
    }
}

private fun DrawScope.strokeSegment(measure: PathMeasure, seg: Path, from: Float, to: Float, color: Color, width: Float) {
    if (to - from <= 0.01f) return
    seg.reset()
    measure.getSegment(from, to, seg, true)
    drawPath(seg, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** Basılıyken hafifçe küçülür, bırakınca yaylanarak döner. */
fun Modifier.pressScale(source: InteractionSource, pressedScale: Float = 0.97f): Modifier = composed {
    val on = LocalAnimationsEnabled.current
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (on && pressed) pressedScale else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pressScale",
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** Run durumuna göre logonun ruh hâli: iş sürerken çalışır, duraklatılınca bekler, bitince sevinir, hatada sarsılır. */
fun RunStatus.logoMood(): com.kriptoncod.app.ui.components.LogoMood = when (this) {
    RunStatus.PREPARING, RunStatus.RUNNING, RunStatus.PUSHING, RunStatus.CI_WAITING, RunStatus.FIXING ->
        com.kriptoncod.app.ui.components.LogoMood.Working
    RunStatus.PAUSED -> com.kriptoncod.app.ui.components.LogoMood.Waiting
    RunStatus.DONE -> com.kriptoncod.app.ui.components.LogoMood.Success
    RunStatus.FAILED -> com.kriptoncod.app.ui.components.LogoMood.Error
    RunStatus.CANCELLED -> com.kriptoncod.app.ui.components.LogoMood.Idle
}
