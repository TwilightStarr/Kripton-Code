package com.kriptoncod.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import com.kriptoncod.app.ui.fx.FxKind
import kotlin.math.PI
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Logo sürprizleri (easter egg). Hiçbiri kazanç, ödül veya ilerleme sağlamaz; yalnızca akıcı birer hareket şovudur.
 * Ayarlar → "Logo sürprizleri" ile kapatılır.
 *
 *  - Tek dokunuş: sakin üçlü sırayla döner ([Boop] → [Blink] → [Hop]).
 *  - Hızlı art arda dokunuş (her biri öncekinden en çok [TAP_CHAIN_MS] sonra): her dokunuş sıradaki sürprizi
 *    oynatır ([Spin] → [Glitch] → [Echo] → [Confetti] → [Split] → [Supernova]); finalden sonra zincir sıfırlanır.
 *  - Uzun basma: [Scan] (hologram taraması).
 *  - Sürükleme: nokta parmağı izler, bırakınca yaylanarak yerine döner (bkz. [LogoEggState.peekTo]).
 */
enum class LogoEgg(val durationMs: Int, val fxKind: FxKind? = null, val fxDelayMs: Int = 0) {
    /** Logo içe çöker, nokta ezilip sıçrar, halka yayılır. */
    Boop(520),

    /** Nokta iki kez göz kırpar. */
    Blink(560),

    /** Nokta aralıktan yukarı zıplar, inip ezilir. */
    Hop(900),

    /** "C" çizgisi bir tam tur döner, arkasında hayalet izler bırakır. */
    Spin(1100),

    /** Logo yatay şeritler hâlinde kayar (tema renginde arıza efekti), sonra oturur. */
    Glitch(800),

    /** Altıgenden dışa doğru yayılan ince yankı çizgileri. */
    Echo(1200),

    /** Nokta sıkışır, sonra ekranın üstünde altıgen kıymıklar saçılır. */
    Confetti(1300, FxKind.Confetti, 280),

    /** Nokta üçe bölünür, altıgenin çevresinde döner ve yeniden birleşir. */
    Split(1500),

    /** Hologram taraması: bir çizgi logoyu yukarıdan aşağı aydınlatır. */
    Scan(1500),

    /** Final: sıkışma, parlama, ekran boyu şok dalgası ve kıvılcımlar. */
    Supernova(2000, FxKind.Supernova, 560),
}

internal val CALM_EGGS = listOf(LogoEgg.Boop, LogoEgg.Blink, LogoEgg.Hop)
internal val CHAIN_EGGS = listOf(LogoEgg.Spin, LogoEgg.Glitch, LogoEgg.Echo, LogoEgg.Confetti, LogoEgg.Split, LogoEgg.Supernova)

/** Zincirdeki dokunuşlar arası en çok bekleme (ms). */
internal const val TAP_CHAIN_MS = 650L
private const val MAX_LOOK_UNITS = 11f

/** Zincirdeki [count]. dokunuşa karşılık gelen sürpriz. İlk dokunuşta [calmIndex] sakin üçlüde seçer. */
internal fun eggForTap(count: Int, calmIndex: Int): LogoEgg = if (count <= 1) {
    CALM_EGGS[calmIndex.mod(CALM_EGGS.size)]
} else {
    CHAIN_EGGS[(count - 2).coerceAtMost(CHAIN_EGGS.size - 1)]
}

/** Bir logonun sürpriz durumu: oynayan hareket, ilerlemesi, dokunma zinciri ve sürükleme "bakışı". */
@Stable
internal class LogoEggState {
    var current by mutableStateOf<LogoEgg?>(null)
        private set
    val progress = Animatable(0f)

    /** Noktanın dinlenme konumundan sapması (logo birimi); sürükleme sırasında parmağa doğru. */
    var lookX by mutableFloatStateOf(0f)
        private set
    var lookY by mutableFloatStateOf(0f)
        private set

    private var taps = 0
    private var lastTapMs = 0L
    private var calmIndex = -1
    private var job: Job? = null
    private var peekJob: Job? = null

    /** Bir dokunuşu zincire işler ve oynanacak sürprizi döndürür. */
    fun nextTapEgg(nowMs: Long): LogoEgg {
        taps = if (taps > 0 && nowMs - lastTapMs <= TAP_CHAIN_MS) taps + 1 else 1
        lastTapMs = nowMs
        if (taps == 1) calmIndex = (calmIndex + 1).mod(CALM_EGGS.size)
        val pick = eggForTap(taps, calmIndex)
        if (pick == LogoEgg.Supernova) taps = 0
        return pick
    }

    fun start(scope: CoroutineScope, egg: LogoEgg) {
        job?.cancel()
        job = scope.launch {
            current = egg
            progress.snapTo(0f)
            progress.animateTo(1f, tween(egg.durationMs, easing = LinearEasing))
            current = null
        }
    }

    /** [dx]/[dy]: noktadan parmağa vektör (logo birimi). Nokta yarı yolda sınırlı bir mesafeye kadar gider. */
    fun peekTo(dx: Float, dy: Float) {
        peekJob?.cancel()
        val len = sqrt(dx * dx + dy * dy)
        if (len < 0.01f) {
            lookX = 0f
            lookY = 0f
            return
        }
        val m = minOf(len * 0.55f, MAX_LOOK_UNITS)
        lookX = dx / len * m
        lookY = dy / len * m
    }

    fun releasePeek(scope: CoroutineScope) {
        peekJob?.cancel()
        val sx = lookX
        val sy = lookY
        if (sx == 0f && sy == 0f) return
        peekJob = scope.launch {
            val ax = Animatable(sx)
            val ay = Animatable(sy)
            launch { ax.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium)) { lookX = value } }
            ay.animateTo(0f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMedium)) { lookY = value }
            lookX = 0f
            lookY = 0f
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Çizim
// ---------------------------------------------------------------------------------------------------------------

private const val PI_F = PI.toFloat()

private fun ease(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return 1f - (1f - c) * (1f - c)
}

private fun bump(t: Float): Float = kotlin.math.sin(PI_F * t.coerceIn(0f, 1f))

private fun hash01(n: Int): Float {
    val h = (n * 73856093) xor (n * 19349663)
    return ((h and 0x7fffffff) % 1000) / 1000f
}

/** [q] 0..1: sürprizin ilerlemesi. Her sürpriz q=0 ve q=1'de tam (dinlenme) logodur. */
internal fun DrawScope.drawEgg(egg: LogoEgg, g: LogoGeometry, color: Color, q: Float) {
    when (egg) {
        LogoEgg.Boop -> drawBoop(g, color, q)
        LogoEgg.Blink -> drawBlink(g, color, q)
        LogoEgg.Hop -> drawHop(g, color, q)
        LogoEgg.Spin -> drawSpin(g, color, q)
        LogoEgg.Glitch -> drawGlitch(g, color, q)
        LogoEgg.Echo -> drawEcho(g, color, q)
        LogoEgg.Confetti -> drawConfetti(g, color, q)
        LogoEgg.Split -> drawSplit(g, color, q)
        LogoEgg.Scan -> drawScan(g, color, q, loop = false)
        LogoEgg.Supernova -> drawSupernova(g, color, q)
    }
}

private fun DrawScope.drawBoop(g: LogoGeometry, color: Color, q: Float) {
    val squeeze = 1f - 0.08f * bump(q)
    scale(squeeze, squeeze, pivot = g.center) {
        fullC(g, color)
        val press = if (q < 0.25f) 1f - 0.4f * smooth(q / 0.25f) else 1f + 0.6f * bump((q - 0.25f) / 0.75f) * (1f - q)
        if (q > 0.25f) {
            val t = (q - 0.25f) / 0.75f
            ring(g, g.rest, color, 5f + 22f * ease(t), 0.65f * (1f - t) * (1f - t), 2.2f)
        }
        dot(g, g.rest, color, scale = press, halo = 0.3f * bump(q))
    }
}

private fun DrawScope.drawBlink(g: LogoGeometry, color: Color, q: Float) {
    fullC(g, color)
    val s = kotlin.math.sin(2f * PI_F * q)
    val shut = s * s
    scale(1f, 1f - 0.9f * shut, pivot = g.rest) {
        dot(g, g.rest, color, halo = 0.12f * shut)
    }
}

private fun DrawScope.drawHop(g: LogoGeometry, color: Color, q: Float) {
    fullC(g, color)
    val air = (q / 0.8f).coerceIn(0f, 1f)
    val lift = 4f * air * (1f - air)
    val at = Offset(g.rest.x, g.rest.y - 22f * g.k * lift)
    val land = ((q - 0.8f) / 0.2f).coerceIn(0f, 1f)
    val sy = if (q < 0.8f) 1f + 0.22f * lift else 1f - 0.4f * bump(land)
    val sx = if (sy < 1f) 1f + (1f - sy) * 0.9f else 1f - (sy - 1f) * 0.5f
    // Boşlukta kalan soluk "gölge" nokta: nokta yerinden ayrılmış görünür.
    if (lift > 0.02f) ring(g, g.rest, color, 4f + 2f * (1f - lift), 0.35f * lift, 1.6f)
    scale(sx, sy, pivot = at) {
        dot(g, at, color, halo = 0.22f * lift)
    }
    if (q >= 0.8f) ring(g, g.rest, color, 5f + 14f * land, 0.55f * (1f - land), 1.8f)
}

private fun DrawScope.drawSpin(g: LogoGeometry, color: Color, q: Float) {
    val deg = 360f * smooth(q)
    val trail = 16f * bump(q)
    for (i in 3 downTo 1) {
        rotate(deg - trail * i, pivot = g.center) {
            arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * 0.16f * (4 - i) / 3f))
        }
    }
    rotate(deg, pivot = g.center) { fullC(g, color) }
    dot(g, g.rest, color, scale = 1f + 0.12f * bump(q), halo = 0.2f * bump(q))
}

private fun DrawScope.drawGlitch(g: LogoGeometry, color: Color, q: Float) {
    val step = (q * 16f).toInt()
    val env = 1f - q
    val bands = 6
    val bandH = size.height / bands
    for (b in 0 until bands) {
        val r = hash01(step * 31 + b * 17)
        val dx = if (r > 0.25f) (r - 0.5f) * 2f * 7f * g.k * env else 0f
        clipRect(top = b * bandH, bottom = (b + 1) * bandH) {
            translate(left = dx) {
                fullC(g, color)
                dot(g, g.rest, color)
            }
        }
    }
    if (env > 0.2f) {
        val side = if (step % 2 == 0) 1f else -1f
        translate(left = 3f * g.k * env * side) {
            arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * 0.35f))
        }
    }
}

private fun DrawScope.drawEcho(g: LogoGeometry, color: Color, q: Float) {
    for (i in 0..2) {
        val t = (q * 1.5f - i * 0.2f).coerceIn(0f, 1f)
        if (t <= 0f || t >= 1f) continue
        val s = 1f + 1.1f * ease(t)
        scale(s, s, pivot = g.center) {
            arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * 0.5f * (1f - t)), width = g.strokeWidth * 0.45f / s)
        }
    }
    fullC(g, color)
    dot(g, g.rest, color, scale = 1f + 0.2f * bump(q), halo = 0.2f * bump(q))
}

/** Yerel kısım: nokta sıkışır, bırakılınca sıçrar. Kıymıklar [FxKind.Confetti] ile ekran katmanında oynar. */
private fun DrawScope.drawConfetti(g: LogoGeometry, color: Color, q: Float) {
    fullC(g, color)
    val charge = 0.22f
    val press = if (q < charge) 1f - 0.45f * smooth(q / charge) else 1f + 0.7f * bump((q - charge) / (1f - charge)) * (1f - q)
    if (q < charge) ring(g, g.rest, color, 16f * (1f - q / charge) + 4f, 0.5f * (q / charge), 1.6f)
    if (q >= charge) {
        val t = (q - charge) / (1f - charge)
        ring(g, g.rest, color, 5f + 24f * ease(t), 0.7f * (1f - t) * (1f - t), 2.2f)
    }
    dot(g, g.rest, color, scale = press, halo = 0.3f * bump(q))
}

private fun DrawScope.drawSplit(g: LogoGeometry, color: Color, q: Float) {
    fullC(g, color)
    val spread = when {
        q < 0.3f -> smooth(q / 0.3f)
        q > 0.8f -> 1f - smooth((q - 0.8f) / 0.2f)
        else -> 1f
    }
    val travel = smooth(q) * g.length
    for (i in 0 until 3) {
        val d = (travel + spread * i * g.length / 3f) % g.length
        dot(g, g.position(d), color, scale = 1f - 0.25f * spread, halo = 0.25f * spread)
    }
    if (q > 0.85f) {
        val t = (q - 0.85f) / 0.15f
        ring(g, g.rest, color, 5f + 12f * t, 0.5f * (1f - t), 1.8f)
    }
}

/** Yerel kısım: sıkışma + parlama + halkalar. Şok dalgası ve kıvılcımlar [FxKind.Supernova] ile ekran katmanında oynar. */
private fun DrawScope.drawSupernova(g: LogoGeometry, color: Color, q: Float) {
    when {
        q < 0.28f -> {
            val t = q / 0.28f
            val shake = kotlin.math.sin(t * 40f) * t * 1.6f * g.k
            translate(left = shake) {
                arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * (1f - 0.5f * t)))
            }
            ring(g, g.rest, color, 4f + 18f * (1f - t), 0.5f * t, 1.6f)
            dot(g, g.rest, color, scale = 1f - 0.45f * smooth(t), halo = 0.5f * t)
        }

        q < 0.4f -> {
            val t = (q - 0.28f) / 0.12f
            drawCircle(color.copy(alpha = color.alpha * (1f - t) * 0.9f), radius = (10f + 26f * t) * g.k, center = g.rest)
            arc(g, g.cStart, g.cEnd, color, width = g.strokeWidth * (1f + 0.8f * (1f - t)))
            dot(g, g.rest, color, scale = 0.9f + 0.7f * (1f - t))
        }

        else -> {
            val t = (q - 0.4f) / 0.6f
            fullC(g, color.copy(alpha = color.alpha * smooth(t * 1.5f)))
            for (i in 0..2) {
                val tt = (t * 1.2f - i * 0.15f).coerceIn(0f, 1f)
                ring(g, g.rest, color, 5f + (20f + 14f * i) * ease(tt), 0.7f * (1f - tt) * (1f - tt), 2.2f)
            }
            dot(g, g.rest, color, scale = 1f + 0.5f * bump(minOf(t * 2f, 1f)) * (1f - t))
        }
    }
}
