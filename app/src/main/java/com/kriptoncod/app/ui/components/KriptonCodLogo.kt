package com.kriptoncod.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Logonun "ruh hâli": uygulamanın o anki durumunu sade bir hareketle anlatır.
 *  - [Idle]: dinlenme; hareketsiz.
 *  - [Working]: iş sürüyor; soluk altıgen üzerinde kuyruklu bir nokta dolanır.
 *  - [Waiting]: duraklatıldı / kota bekleniyor; sönük çizgi ve yavaşça yanıp sönen nokta.
 *  - [Success]: tek seferlik; nokta yaylanarak sıçrar ve halka yayılır.
 *  - [Error]: tek seferlik sarsıntı; nokta hata renginde kalır.
 *  - [Intro]: açılış; altıgen çizilir, nokta yerine süzülür.
 */
enum class LogoMood { Idle, Working, Waiting, Success, Error, Intro }

/** Halka yarıçapı (yarım çizgi kalınlığı dahil) bu değeri aşmaz: düğümün sağında 16.5 birim boşluk var. */
private const val MAX_RING = 15f

private const val ORBIT_MS = 2600
private const val WAITING_MS = 2600
private const val SUCCESS_MS = 1000
private const val ERROR_MS = 650
private const val INTRO_MS = 1500

/**
 * Tek renkli, tek çizgili logo: sağ kenarı açık bir altıgen ("C" harfi) ve açıklıkta bir düğüm noktası.
 * Başlatıcı ikonuyla (res/drawable/ic_launcher_foreground.xml) aynı geometridir; renk aktif temadan gelir.
 *
 * Animasyonlar kapalıysa (Ayarlar veya sistem) logo hareketsizdir; yalnız [LogoMood.Error] noktası hata renginde kalır.
 * Bir hareket bittiğinde logo her zaman dinlenme konumuna oturur; hiçbir zaman yarım kalmış görünmez.
 */
@Composable
fun KriptonCodLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    /** Eski kısayol: true ise [LogoMood.Working]. [mood] verilirse o kullanılır. */
    busy: Boolean = false,
    mood: LogoMood = if (busy) LogoMood.Working else LogoMood.Idle,
    errorColor: Color = KriptonTheme.colors.danger,
) {
    val description = stringResource(R.string.logo_description)
    val animationsOn = LocalAnimationsEnabled.current

    // loop: 0..1 tekrarlayan tur ilerlemesi (Working/Waiting). once: 0..1 tek seferlik (Success/Error/Intro).
    val loop = remember { Animatable(0f) }
    val once = remember { Animatable(1f) }

    LaunchedEffect(mood, animationsOn) {
        if (!animationsOn) {
            loop.snapTo(0f)
            once.snapTo(1f)
            return@LaunchedEffect
        }
        // Önceki hareket yarıdaysa kısa sürede dinlenme konumuna getir (sıçrama olmasın).
        if (loop.value > 0f && loop.value < 1f) loop.animateTo(1f, tween(250, easing = LinearEasing))
        loop.snapTo(0f)
        when (mood) {
            LogoMood.Working -> while (true) {
                loop.animateTo(1f, tween(ORBIT_MS, easing = LinearEasing))
                loop.snapTo(0f)
            }

            LogoMood.Idle -> Unit

            LogoMood.Waiting -> while (true) {
                loop.animateTo(1f, tween(WAITING_MS, easing = LinearEasing))
                loop.snapTo(0f)
            }

            LogoMood.Success -> {
                once.snapTo(0f)
                once.animateTo(1f, tween(SUCCESS_MS, easing = LinearEasing))
            }

            LogoMood.Error -> {
                once.snapTo(0f)
                once.animateTo(1f, tween(ERROR_MS, easing = LinearEasing))
            }

            LogoMood.Intro -> {
                once.snapTo(0f)
                once.animateTo(1f, tween(INTRO_MS, easing = LinearEasing))
            }
        }
    }

    val drawModifier = modifier
        .size(logoSize)
        .semantics { contentDescription = description }
        .drawWithCache {
            val g = LogoGeometry(size)
            onDrawBehind {
                // Durum okumaları çizim içindedir: kare başına yalnız yeniden çizilir, yeniden oluşturulmaz.
                val p = loop.value
                val q = once.value
                if (!animationsOn) {
                    drawRest(g, color, if (mood == LogoMood.Error) errorColor else color)
                } else {
                    when (mood) {
                        LogoMood.Idle -> drawRest(g, color, color)

                        LogoMood.Working -> drawOrbit(g, color, p)

                        LogoMood.Waiting -> drawWaiting(g, color, p)

                        LogoMood.Success -> drawSuccess(g, color, q)

                        LogoMood.Error -> drawError(g, color, errorColor, q)

                        LogoMood.Intro -> drawIntro(g, color, q)
                    }
                }
            }
        }
    Box(drawModifier)
}

// ---------------------------------------------------------------------------------------------------------------
// Geometri
// ---------------------------------------------------------------------------------------------------------------

/**
 * 108x108'lik başlatıcı ikonun ortadaki 15..93 penceresi (78 birim) tuvale ölçeklenir. Pencere, altıgenin çevresinde
 * halkalara pay bırakır: düğüm noktasından en yakın kenara [MAX_RING] birim kalır, halkalar kutudan taşmaz.
 */
private class LogoGeometry(size: Size) {
    val k = size.minDimension / 78f
    private fun pt(x: Float, y: Float) = Offset((x - 15f) * k, (y - 15f) * k)

    /** Düğüm noktasının dinlenme konumu (sağ kenarın ortası). */
    val rest = pt(76.5f, 54f)
    val strokeWidth = 9f * k

    /** Kapalı altıgen; mesafe 0 = [rest], saat yönünün tersine (ikon çizgisiyle aynı yön). */
    private val loop = Path().apply {
        moveTo(rest.x, rest.y)
        listOf(pt(76.5f, 41f), pt(54f, 28f), pt(31.5f, 41f), pt(31.5f, 67f), pt(54f, 80f), pt(76.5f, 67f))
            .forEach { lineTo(it.x, it.y) }
        close()
    }
    private val measure = PathMeasure().apply { setPath(loop, false) }
    val length = measure.length

    /** Açık "C" çizgisi, kapalı altıgenin [length/12, 11·length/12] aralığıdır (sağ kenarın ortası boş). */
    val cStart = length / 12f
    val cEnd = length * 11f / 12f

    private val seg = Path()

    fun position(distance: Float): Offset = measure.getPosition(distance.coerceIn(0f, length - 0.01f))

    fun segment(from: Float, to: Float): Path {
        seg.reset()
        measure.getSegment(from.coerceAtLeast(0f), to.coerceAtMost(length), seg, true)
        return seg
    }
}

private fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun DrawScope.arc(g: LogoGeometry, from: Float, to: Float, color: Color, width: Float = g.strokeWidth, cap: StrokeCap = StrokeCap.Round) {
    if (to - from <= 0.01f || color.alpha <= 0f) return
    drawPath(g.segment(from, to), color, style = Stroke(width = width, cap = cap, join = StrokeJoin.Round))
}

/** [from] negatifse altıgenin başlangıcını aşan kısım sondan devam eder (kuyruk dinlenme noktasını geçerken). */
private fun DrawScope.arcWrapped(g: LogoGeometry, from: Float, to: Float, color: Color, width: Float) {
    if (from >= 0f) {
        arc(g, from, to, color, width, StrokeCap.Butt)
    } else {
        arc(g, g.length + from, g.length, color, width, StrokeCap.Butt)
        arc(g, 0f, to, color, width, StrokeCap.Butt)
    }
}

private fun DrawScope.fullC(g: LogoGeometry, color: Color) = arc(g, g.cStart, g.cEnd, color)

private fun DrawScope.dot(g: LogoGeometry, at: Offset, color: Color, scale: Float = 1f, halo: Float = 0f) {
    if (halo > 0f) drawCircle(color.copy(alpha = color.alpha * halo), radius = 9.5f * g.k * scale, center = at)
    drawCircle(color, radius = 4.5f * g.k * scale, center = at)
}

private fun DrawScope.ring(g: LogoGeometry, at: Offset, color: Color, radiusUnits: Float, alpha: Float, widthUnits: Float = 1.6f) {
    if (alpha <= 0f) return
    val r = radiusUnits.coerceAtMost(MAX_RING - widthUnits / 2f)
    drawCircle(color.copy(alpha = color.alpha * alpha), radius = r * g.k, center = at, style = Stroke(width = widthUnits * g.k))
}

// ---------------------------------------------------------------------------------------------------------------
// Ruh hâlleri
// ---------------------------------------------------------------------------------------------------------------

private fun DrawScope.drawRest(g: LogoGeometry, color: Color, dotColor: Color) {
    fullC(g, color)
    dot(g, g.rest, dotColor)
}

/** Yörünge: soluk altıgen üzerinde, kuyruğu parlayan bir nokta dolanır. */
private fun DrawScope.drawOrbit(g: LogoGeometry, color: Color, p: Float) {
    arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * 0.28f))
    val head = p * g.length
    val tail = g.length * 0.32f
    val n = 18
    for (i in 0 until n) {
        val a = head - tail * (1f - i / n.toFloat())
        val b = head - tail * (1f - (i + 1) / n.toFloat())
        val t = (i + 1) / n.toFloat()
        arcWrapped(g, a, b, color.copy(alpha = color.alpha * t * t * 0.95f), g.strokeWidth * (0.5f + 0.5f * t))
    }
    dot(g, g.position(head), color, scale = 1.05f, halo = 0.2f)
}

/** Bekleme: sönük çizgi, yavaşça "nefes alan" nokta. */
private fun DrawScope.drawWaiting(g: LogoGeometry, color: Color, p: Float) {
    arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * 0.5f))
    val breath = 0.5f - 0.5f * cos(2f * PI.toFloat() * p)
    dot(g, g.rest, color.copy(alpha = color.alpha * (0.3f + 0.7f * breath)), scale = 0.9f + 0.1f * breath)
}

/** Başarı: nokta yaylanarak sıçrar, ondan genişleyen halka yayılır. */
private fun DrawScope.drawSuccess(g: LogoGeometry, color: Color, q: Float) {
    fullC(g, color)
    val burst = ((q - 0.1f) / 0.9f).coerceIn(0f, 1f)
    val e = 1f - (1f - burst) * (1f - burst)
    ring(g, g.rest, color, radiusUnits = 5f + 9.5f * e, alpha = 0.7f * (1f - burst), widthUnits = 2.4f)
    val pop = 1f + 0.6f * sin(PI.toFloat() * (q * 1.7f).coerceAtMost(1f)) * (1f - q)
    dot(g, g.rest, color, scale = pop, halo = 0.25f * (1f - q))
}

/** Hata: kısa yatay sarsıntı; nokta hata renginde kalır. */
private fun DrawScope.drawError(g: LogoGeometry, color: Color, errorColor: Color, q: Float) {
    val dx = sin(q * 6f * 2f * PI.toFloat()) * (1f - q) * 2.6f * g.k
    translate(left = dx) {
        fullC(g, color)
        ring(g, g.rest, errorColor, radiusUnits = 5f + 8f * q, alpha = 0.5f * (1f - q))
        dot(g, g.rest, errorColor)
    }
}

/** Açılış: altıgen çizilir, nokta çizginin ucunda ilerler, sonunda yerine süzülüp sıçrar. */
private fun DrawScope.drawIntro(g: LogoGeometry, color: Color, q: Float) {
    val draw = smooth(q / 0.6f)
    val end = lerpF(g.cStart, g.cEnd, draw)
    arc(g, g.cStart, end, color)
    when {
        q < 0.6f -> dot(g, g.position(end), color, halo = 0.25f)

        q < 0.8f -> dot(g, g.position(lerpF(g.cEnd, g.length, smooth((q - 0.6f) / 0.2f))), color, halo = 0.2f)

        else -> {
            val t = (q - 0.8f) / 0.2f
            ring(g, g.rest, color, radiusUnits = 5f + 9.5f * t, alpha = 0.6f * (1f - t), widthUnits = 2.2f)
            dot(g, g.rest, color, scale = 1f + 0.5f * sin(PI.toFloat() * t.coerceIn(0f, 1f)) * (1f - t))
        }
    }
}
