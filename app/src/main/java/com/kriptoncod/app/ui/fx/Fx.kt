package com.kriptoncod.app.ui.fx

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Uygulama genelindeki "efekt katmanı": ekranın üstünde, dokunmaları engellemeyen tek bir tuval.
 * Parçacıklar (altıgen kıymıklar, noktalar, kıvılcımlar) ve şok dalgaları burada çizilir; böylece logo gibi küçük
 * bileşenler kendi sınırlarının dışına taşan büyük efektler (konfeti, süpernova) oynatabilir.
 *
 * Kare döngüsü yalnız ekranda parçacık varken çalışır; boştayken hiçbir iş yapılmaz (pil dostu).
 */
enum class FxKind {
    /** Bir noktadan her yöne saçılan altıgen kıymıklar ve noktalar. */
    Confetti,

    /** Büyük şok dalgası + radyal kıvılcımlar + kıymıklar (logo finali). */
    Supernova,

    /** Aşağıdan yukarı fışkıran kutlama (çalışma başarıyla bitince). */
    Fountain,

    /** Birkaç küçük parıltı (açılış, küçük dokunuşlar). */
    Sparkle,
}

private const val SHAPE_DOT = 0
private const val SHAPE_HEX = 1
private const val SHAPE_SPARK = 2
private const val MAX_PARTICLES = 320
private const val MAX_WAVES = 24

internal class FxParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val size: Float,
    val life: Float,
    val color: Color,
    val shape: Int,
    var rot: Float,
    val vrot: Float,
    val gravity: Float,
    val drag: Float,
) {
    var age = 0f
}

internal class FxWave(
    val x: Float,
    val y: Float,
    val r0: Float,
    val r1: Float,
    val life: Float,
    val color: Color,
    val width: Float,
    val alpha: Float,
) {
    var age = 0f
}

@Stable
class FxController {
    internal val particles = ArrayList<FxParticle>()
    internal val waves = ArrayList<FxWave>()
    internal val scratch = Path()

    /** Her kare değişir; tuval bunu çizim aşamasında okuyarak yalnız yeniden çizilir. */
    internal var tick by mutableLongStateOf(0L)

    /** Her yeni efektte artar; kare döngüsünü (yeniden) başlatır. */
    internal var epoch by mutableIntStateOf(0)

    /** Tuvalin kök koordinatlardaki konumu; logo gibi bileşenler kök koordinatını yerele çevirir. */
    var hostOffset: Offset = Offset.Zero

    /** dp -> px dönüşümü (tuval yerleşince güncellenir). */
    var density: Float = 3f

    val idle: Boolean get() = particles.isEmpty() && waves.isEmpty()
    val particleCount: Int get() = particles.size
    val waveCount: Int get() = waves.size

    fun toLocal(rootPosition: Offset): Offset = rootPosition - hostOffset

    /** Dokunma dalgası: küçük, hızlı sönen tek halka. */
    fun ripple(at: Offset, color: Color, maxRadiusDp: Float = 40f, alpha: Float = 0.28f) {
        addWave(FxWave(at.x, at.y, 4f * density, maxRadiusDp * density, 0.55f, color, 1.6f * density, alpha))
        kick()
    }

    /** Büyük tek halka (hata / bildirim vurgusu). */
    fun pulse(at: Offset, color: Color, maxRadiusDp: Float = 180f) {
        addWave(FxWave(at.x, at.y, 8f * density, maxRadiusDp * density, 1.1f, color, 2.4f * density, 0.5f))
        kick()
    }

    fun burst(at: Offset, kind: FxKind, colors: List<Color>) {
        val palette = colors.ifEmpty { listOf(Color.White) }
        val d = density
        when (kind) {
            FxKind.Confetti -> {
                repeat(26) {
                    val a = rnd(0f, TAU)
                    val s = rnd(90f, 260f) * d
                    add(at, cos(a) * s, sin(a) * s - 60f * d, rnd(2.5f, 5.5f) * d, rnd(0.9f, 1.6f), palette.random(), randomShape(), 260f * d, 1.6f)
                }
                addWave(FxWave(at.x, at.y, 6f * d, 70f * d, 0.6f, palette.first(), 1.8f * d, 0.45f))
            }

            FxKind.Supernova -> {
                val rings = floatArrayOf(150f, 220f, 300f)
                rings.forEachIndexed { i, r ->
                    addWave(FxWave(at.x, at.y, 6f * d, r * d, 0.9f + 0.3f * i, palette.first(), 2.2f * d, 0.6f - 0.1f * i))
                }
                repeat(48) {
                    val a = rnd(0f, TAU)
                    val s = rnd(260f, 700f) * d
                    add(at, cos(a) * s, sin(a) * s, rnd(3f, 6f) * d, rnd(0.5f, 1.0f), palette.random(), SHAPE_SPARK, 0f, 3f)
                }
                repeat(14) {
                    val a = rnd(0f, TAU)
                    val s = rnd(120f, 380f) * d
                    add(at, cos(a) * s, sin(a) * s, rnd(4f, 8f) * d, rnd(1.0f, 1.8f), palette.random(), SHAPE_HEX, 120f * d, 1.4f)
                }
            }

            FxKind.Fountain -> {
                repeat(36) {
                    val a = (-PI.toFloat() / 2f) + rnd(-0.38f, 0.38f)
                    val s = rnd(380f, 720f) * d
                    add(at, cos(a) * s, sin(a) * s, rnd(2.5f, 5.5f) * d, rnd(1.2f, 2.0f), palette.random(), randomShape(), 900f * d, 0.8f)
                }
            }

            FxKind.Sparkle -> {
                repeat(8) {
                    val a = rnd(0f, TAU)
                    val s = rnd(40f, 120f) * d
                    add(at, cos(a) * s, sin(a) * s, rnd(1.5f, 3f) * d, rnd(0.5f, 0.9f), palette.random(), SHAPE_DOT, 0f, 2.2f)
                }
            }
        }
        kick()
    }

    /** [dt] saniye ilerletir; ömrü dolanlar silinir. Yalnız ana iş parçacığından çağrılır. */
    fun step(dt: Float) {
        var i = particles.size - 1
        while (i >= 0) {
            val p = particles[i]
            p.age += dt
            if (p.age >= p.life) {
                particles.removeAt(i)
            } else {
                val damp = max(0f, 1f - p.drag * dt)
                p.vx *= damp
                p.vy = p.vy * damp + p.gravity * dt
                p.x += p.vx * dt
                p.y += p.vy * dt
                p.rot += p.vrot * dt
            }
            i--
        }
        var j = waves.size - 1
        while (j >= 0) {
            val w = waves[j]
            w.age += dt
            if (w.age >= w.life) waves.removeAt(j)
            j--
        }
    }

    private fun kick() {
        epoch++
    }

    private fun addWave(w: FxWave) {
        if (waves.size >= MAX_WAVES) waves.removeAt(0)
        waves.add(w)
    }

    private fun add(
        at: Offset,
        vx: Float,
        vy: Float,
        size: Float,
        life: Float,
        color: Color,
        shape: Int,
        gravity: Float,
        drag: Float,
    ) {
        if (particles.size >= MAX_PARTICLES) particles.removeAt(0)
        particles.add(
            FxParticle(
                x = at.x, y = at.y, vx = vx, vy = vy, size = size, life = life, color = color, shape = shape,
                rot = rnd(0f, TAU), vrot = rnd(-6f, 6f), gravity = gravity, drag = drag,
            ),
        )
    }

    private fun randomShape(): Int = when (Random.nextInt(10)) {
        in 0..3 -> SHAPE_DOT
        in 4..7 -> SHAPE_HEX
        else -> SHAPE_SPARK
    }

    private fun rnd(a: Float, b: Float): Float = a + Random.nextFloat() * (b - a)
}

private val TAU = (2.0 * PI).toFloat()

/** Ana ağaçta tek bir denetleyici sağlanır; sağlanmadıysa (önizleme/test) hiçbir şey çizilmez. */
val LocalFx = staticCompositionLocalOf { FxController() }

/** Tuval: ekranı kaplar, dokunmayı yutmaz; yalnız parçacık varken kare döngüsü çalışır. */
@Composable
fun FxHost(controller: FxController, modifier: Modifier = Modifier) {
    controller.density = LocalDensity.current.density
    val epoch = controller.epoch
    LaunchedEffect(epoch) {
        if (controller.idle) return@LaunchedEffect
        var last = 0L
        while (true) {
            val now = androidx.compose.runtime.withFrameNanos { it }
            val dt = if (last == 0L) 0.016f else ((now - last) / 1_000_000_000f).coerceAtMost(0.05f)
            last = now
            controller.step(dt)
            controller.tick = now
            if (controller.idle) break
        }
    }
    Canvas(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { controller.hostOffset = it.positionInRoot() },
    ) {
        if (controller.tick < 0L) return@Canvas
        drawFx(controller)
    }
}

private fun DrawScope.drawFx(c: FxController) {
    for (w in c.waves) {
        val t = (w.age / w.life).coerceIn(0f, 1f)
        val e = 1f - (1f - t) * (1f - t)
        val r = w.r0 + (w.r1 - w.r0) * e
        drawCircle(
            color = w.color.copy(alpha = w.color.alpha * w.alpha * (1f - t) * (1f - t)),
            radius = r,
            center = Offset(w.x, w.y),
            style = Stroke(width = w.width * (1f - 0.5f * t)),
        )
    }
    for (p in c.particles) {
        val t = (p.age / p.life).coerceIn(0f, 1f)
        val a = (1f - t).pow(0.7f)
        val col = p.color.copy(alpha = p.color.alpha * a)
        when (p.shape) {
            SHAPE_DOT -> drawCircle(col, radius = p.size * (1f - 0.4f * t), center = Offset(p.x, p.y))

            SHAPE_SPARK -> {
                val tail = Offset(p.x - p.vx * 0.045f, p.y - p.vy * 0.045f)
                drawLine(col, Offset(p.x, p.y), tail, strokeWidth = p.size * 0.5f, cap = StrokeCap.Round)
            }

            else -> {
                val r = p.size * (1f - 0.3f * t)
                c.scratch.reset()
                for (k in 0..5) {
                    val ang = p.rot + k * (PI.toFloat() / 3f)
                    val px = p.x + r * cos(ang)
                    val py = p.y + r * sin(ang)
                    if (k == 0) c.scratch.moveTo(px, py) else c.scratch.lineTo(px, py)
                }
                c.scratch.close()
                drawPath(c.scratch, col, style = Stroke(width = max(1f, r * 0.4f), join = StrokeJoin.Round))
            }
        }
    }
}

/**
 * Her dokunuşta (kaydırma değil) dokunulan noktadan küçük, hızlı sönen bir halka yayar. Olayları tüketmez;
 * düğmeler ve listeler olduğu gibi çalışır.
 */
fun Modifier.tapRipples(controller: FxController, color: Color, enabled: Boolean): Modifier = if (!enabled) {
    this
} else {
    pointerInput(controller, color) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val slop = viewConfiguration.touchSlop
            var moved = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if ((change.position - down.position).getDistance() > slop) moved = true
                if (change.changedToUpIgnoreConsumed()) {
                    if (!moved) controller.ripple(down.position, color)
                    break
                }
                if (!change.pressed) break
            }
        }
    }
}
