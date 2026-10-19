package com.kriptoncod.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.fx.FxKind
import com.kriptoncod.app.ui.fx.LocalFx
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import com.kriptoncod.app.ui.theme.LocalEggsEnabled
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Logonun "ruh hâli": uygulamanın o anki durumunu hareketle anlatır.
 *  - [Idle]: dinlenme; düğüm noktasından yavaşça yayılan bir "ping".
 *  - [Working]: iş sürüyor; [LogoAnimation] çeşitleri sırayla döner (yörünge, dalga, çizgi).
 *  - [Waiting]: duraklatıldı / kota bekleniyor; sönük çizgi ve yavaşça yanıp sönen nokta.
 *  - [Success]: tek seferlik; nokta yaylanarak sıçrar ve halka yayılır.
 *  - [Error]: tek seferlik sarsıntı; nokta hata renginde kalır.
 *  - [Intro]: açılış; altıgen çizilir, nokta yerine süzülür.
 *
 * Bunların üstüne, [KriptonCodLogo]'ya `interactive = true` verilince oynayan **sürprizler** ([LogoEgg]) gelir:
 * dokunma zinciri, uzun basma ve sürükleme. Hepsi yalnızca görseldir; hiçbir şey kazandırmaz.
 */
enum class LogoMood { Idle, Working, Waiting, Success, Error, Intro }

/** [LogoMood.Working] sırasında dönüşümlü oynayan çeşitler. */
enum class LogoAnimation(val lapMs: Int) {
    /** Noktanın arkasında kuyruk bırakarak altıgenin çevresinde dolanması (kuyruklu yıldız). */
    Orbit(2600),

    /** Düğüm noktasından dışa doğru yayılan halkalar (radar). */
    Ripple(2400),

    /** Çizginin kendini silip yeniden çizmesi; nokta ucunda gider. */
    Trace(3400),

    /** Altıgenin iki karşıt noktasında dolanan iki kuyruklu yıldız. */
    Twin(2800),

    /** Kalp atışı: çizgi ve nokta iki kez art arda atar. */
    Pulse(1800),

    /** Tarayıcı çizgisi logonun üzerinden aşağı yukarı süpürür (hologram). */
    Scan(3200),
}

/** Her çeşit [LAPS_PER_VARIANT] tur oynar, sonra sıradakine geçilir. */
private const val LAPS_PER_VARIANT = 2
internal fun variantFor(lap: Int): LogoAnimation = LogoAnimation.entries[(lap / LAPS_PER_VARIANT).mod(LogoAnimation.entries.size)]

private const val IDLE_MS = 3600
private const val WAITING_MS = 2600
private const val SUCCESS_MS = 1000
private const val ERROR_MS = 650
private const val INTRO_MS = 1500

/** Dinlenme noktasının altıgen merkezine uzaklığı (logo birimi, +x yönünde). */
internal const val REST_FROM_CENTER_UNITS = 22.5f

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
    /** true ise dokunma zinciri / uzun basma / sürükleme sürprizleri açılır (Ayarlar'dan kapatılabilir). */
    interactive: Boolean = false,
) {
    val description = stringResource(R.string.logo_description)
    val animationsOn = LocalAnimationsEnabled.current
    val eggsOn = interactive && animationsOn && LocalEggsEnabled.current
    val fx = LocalFx.current
    val scope = rememberCoroutineScope()

    // loop: 0..1 tekrarlayan tur ilerlemesi (Working/Idle/Waiting). once: 0..1 tek seferlik (Success/Error/Intro).
    val loop = remember { Animatable(0f) }
    val once = remember { Animatable(1f) }
    var lap by remember { mutableIntStateOf(0) }

    // Sürprizler: o an oynayan hareket + ilerlemesi; sürükleme "bakışı" (nokta parmağı izler).
    val egg = remember { LogoEggState() }
    var rootCenter by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val palette = listOf(color, KriptonTheme.colors.muted, color.copy(alpha = 0.7f))

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
                loop.animateTo(1f, tween(variantFor(lap).lapMs, easing = LinearEasing))
                loop.snapTo(0f)
                lap++
            }
            LogoMood.Idle -> while (true) {
                loop.animateTo(1f, tween(IDLE_MS, easing = LinearEasing))
                loop.snapTo(0f)
                lap++
            }
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

    val gestureModifier = if (eggsOn) {
        Modifier
            .onGloballyPositioned { rootCenter = it.boundsInRoot().center }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val pick = egg.nextTapEgg(System.currentTimeMillis())
                        egg.start(scope, pick)
                        pick.fxKind?.let { kind ->
                            scope.launch {
                                delay(pick.fxDelayMs.toLong())
                                fx.burst(fx.toLocal(rootCenter), kind, palette)
                            }
                        }
                    },
                    onLongPress = { egg.start(scope, LogoEgg.Scan) },
                )
            }
            .pointerInput(Unit) {
                // Yalnız yatay sürükleme: dikey kaydırma (ana ekran) logonun üstünden de çalışmaya devam eder.
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, _ ->
                        val k = size.width / 64f
                        val fingerX = (change.position.x - size.width / 2f) / k
                        val fingerY = (change.position.y - size.height / 2f) / k
                        egg.peekTo(fingerX - REST_FROM_CENTER_UNITS, fingerY)
                    },
                    onDragEnd = { egg.releasePeek(scope) },
                    onDragCancel = { egg.releasePeek(scope) },
                )
            }
    } else {
        Modifier
    }

    val drawModifier = modifier
        .size(logoSize)
        .semantics { contentDescription = description }
        .then(gestureModifier)
        .drawWithCache {
            val g = LogoGeometry(size)
            onDrawBehind {
                // Durum okumaları çizim içindedir: kare başına yalnız yeniden çizilir, yeniden oluşturulmaz.
                val p = loop.value
                val q = once.value
                val activeEgg = egg.current
                if (!animationsOn) {
                    drawRest(g, color, if (mood == LogoMood.Error) errorColor else color)
                } else if (activeEgg != null) {
                    drawEgg(activeEgg, g, color, egg.progress.value)
                } else when (mood) {
                    LogoMood.Idle -> drawIdle(g, color, p, blink = lap % 3 == 2, lookX = egg.lookX, lookY = egg.lookY)
                    LogoMood.Working -> when (variantFor(lap)) {
                        LogoAnimation.Orbit -> drawOrbit(g, color, p)
                        LogoAnimation.Ripple -> drawRipple(g, color, p)
                        LogoAnimation.Trace -> drawTrace(g, color, p)
                        LogoAnimation.Twin -> drawTwin(g, color, p)
                        LogoAnimation.Pulse -> drawPulse(g, color, p)
                        LogoAnimation.Scan -> drawScan(g, color, p, loop = true)
                    }
                    LogoMood.Waiting -> drawWaiting(g, color, p)
                    LogoMood.Success -> drawSuccess(g, color, q)
                    LogoMood.Error -> drawError(g, color, errorColor, q)
                    LogoMood.Intro -> drawIntro(g, color, q)
                }
            }
        }
    Box(drawModifier)
}

// ---------------------------------------------------------------------------------------------------------------
// Geometri
// ---------------------------------------------------------------------------------------------------------------

/** 108x108'lik başlatıcı ikonun ortadaki 22..86 penceresi (64 birim) tuvale ölçeklenir. */
internal class LogoGeometry(size: Size) {
    val k = size.minDimension / 64f
    private fun pt(x: Float, y: Float) = Offset((x - 22f) * k, (y - 22f) * k)

    /** Altıgenin merkezi (tuvalin ortası). */
    val center = Offset(32f * k, 32f * k)

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

internal fun smooth(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

internal fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

internal fun DrawScope.arc(g: LogoGeometry, from: Float, to: Float, color: Color, width: Float = g.strokeWidth, cap: StrokeCap = StrokeCap.Round) {
    if (to - from <= 0.01f || color.alpha <= 0f) return
    drawPath(g.segment(from, to), color, style = Stroke(width = width, cap = cap, join = StrokeJoin.Round))
}

/** [from] negatifse altıgenin başlangıcını aşan kısım sondan devam eder (kuyruk dinlenme noktasını geçerken). */
internal fun DrawScope.arcWrapped(g: LogoGeometry, from: Float, to: Float, color: Color, width: Float) {
    if (from >= 0f) {
        arc(g, from, to, color, width, StrokeCap.Butt)
    } else {
        arc(g, g.length + from, g.length, color, width, StrokeCap.Butt)
        arc(g, 0f, to, color, width, StrokeCap.Butt)
    }
}

internal fun DrawScope.fullC(g: LogoGeometry, color: Color) = arc(g, g.cStart, g.cEnd, color)

internal fun DrawScope.dot(g: LogoGeometry, at: Offset, color: Color, scale: Float = 1f, halo: Float = 0f) {
    if (halo > 0f) drawCircle(color.copy(alpha = color.alpha * halo), radius = 9.5f * g.k * scale, center = at)
    drawCircle(color, radius = 4.5f * g.k * scale, center = at)
}

internal fun DrawScope.ring(g: LogoGeometry, at: Offset, color: Color, radiusUnits: Float, alpha: Float, widthUnits: Float = 1.6f) {
    if (alpha <= 0f) return
    drawCircle(color.copy(alpha = color.alpha * alpha), radius = radiusUnits * g.k, center = at, style = Stroke(width = widthUnits * g.k))
}

// ---------------------------------------------------------------------------------------------------------------
// Ruh hâlleri
// ---------------------------------------------------------------------------------------------------------------

internal fun DrawScope.drawRest(g: LogoGeometry, color: Color, dotColor: Color) {
    fullC(g, color)
    dot(g, g.rest, dotColor)
}

/**
 * Dinlenme: nokta yerinde, ondan yavaşça yayılıp sönen tek halka. [blink] true ise tur ortasında nokta göz kırpar;
 * [lookX]/[lookY] (logo birimi) sürükleme sırasında noktanın parmağa doğru kaymasıdır ve ince bir bağ çizgisi çizilir.
 */
private fun DrawScope.drawIdle(g: LogoGeometry, color: Color, p: Float, blink: Boolean, lookX: Float, lookY: Float) {
    fullC(g, color)
    val at = Offset(g.rest.x + lookX * g.k, g.rest.y + lookY * g.k)
    if (lookX != 0f || lookY != 0f) {
        drawLine(color.copy(alpha = color.alpha * 0.4f), g.rest, at, strokeWidth = 2.4f * g.k, cap = StrokeCap.Round)
    }
    val e = 1f - (1f - p) * (1f - p)
    ring(g, g.rest, color, radiusUnits = 5f + 11f * e, alpha = 0.32f * (1f - p) * (1f - p))
    val closed = if (blink && p in 0.40f..0.56f) sin(PI.toFloat() * (p - 0.40f) / 0.16f) else 0f
    scale(1f, 1f - 0.85f * closed, pivot = at) {
        dot(g, at, color, scale = 1f + 0.08f * sin(PI.toFloat() * p))
    }
}

/** [head] mesafesindeki başın arkasında parlayan, incelen bir kuyruk. */
private fun DrawScope.comet(g: LogoGeometry, color: Color, head: Float) {
    val tail = g.length * 0.32f
    val n = 18
    for (i in 0 until n) {
        val a = head - tail * (1f - i / n.toFloat())
        val b = head - tail * (1f - (i + 1) / n.toFloat())
        val t = (i + 1) / n.toFloat()
        arcWrapped(g, a, b, color.copy(alpha = color.alpha * t * t * 0.95f), g.strokeWidth * (0.5f + 0.5f * t))
    }
}

/** Yörünge: soluk altıgen üzerinde, kuyruğu parlayan bir nokta dolanır. */
private fun DrawScope.drawOrbit(g: LogoGeometry, color: Color, p: Float) {
    arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * 0.28f))
    val head = p * g.length
    comet(g, color, head)
    dot(g, g.position(head), color, scale = 1.05f, halo = 0.2f)
}

/** İkiz yörünge: karşıt iki nokta aynı anda dolanır. */
private fun DrawScope.drawTwin(g: LogoGeometry, color: Color, p: Float) {
    arc(g, g.cStart, g.cEnd, color.copy(alpha = color.alpha * 0.28f))
    val head = p * g.length
    val other = (head + g.length / 2f) % g.length
    comet(g, color, head)
    comet(g, color, other)
    dot(g, g.position(head), color, scale = 1.0f, halo = 0.2f)
    dot(g, g.position(other), color, scale = 0.85f, halo = 0.15f)
}

private fun beat(p: Float, from: Float, to: Float): Float =
    if (p < from || p > to) 0f else sin(PI.toFloat() * (p - from) / (to - from))

/** Kalp atışı: iki vuruş; çizgi kalınlaşır, nokta şişer, halka yayılır. */
private fun DrawScope.drawPulse(g: LogoGeometry, color: Color, p: Float) {
    val b = maxOf(beat(p, 0f, 0.2f), 0.7f * beat(p, 0.24f, 0.44f))
    arc(g, g.cStart, g.cEnd, color, width = g.strokeWidth * (1f + 0.22f * b))
    ring(g, g.rest, color, radiusUnits = 5f + 15f * b, alpha = 0.55f * b, widthUnits = 2f)
    dot(g, g.rest, color, scale = 1f + 0.32f * b, halo = 0.25f * b)
}

/**
 * Hologram taraması. [loop] true: soluk logonun üstünde aydınlık bir dilim aşağı yukarı süpürür (Çalışıyor çeşidi).
 * false: çizgi bir kez yukarıdan aşağı iner, geçtiği yeri aydınlatır; sonunda logo tam parlak oturur (sürpriz).
 */
internal fun DrawScope.drawScan(g: LogoGeometry, color: Color, p: Float, loop: Boolean) {
    val w = size.width
    val h = size.height
    val band = 12f * g.k
    if (loop) {
        val y = h * (if (p < 0.5f) smooth(p / 0.5f) else 1f - smooth((p - 0.5f) / 0.5f))
        val dim = color.copy(alpha = color.alpha * 0.32f)
        fullC(g, dim)
        dot(g, g.rest, dim)
        clipRect(top = y - band, bottom = y + band) {
            fullC(g, color)
            dot(g, g.rest, color)
        }
        scanLine(color, y, w, g.k, band)
    } else {
        val sweep = smooth((p / 0.6f).coerceIn(0f, 1f))
        val y = h * sweep
        if (p < 0.6f) {
            val dim = color.copy(alpha = color.alpha * 0.3f)
            fullC(g, dim)
            dot(g, g.rest, dim)
            clipRect(bottom = y) {
                fullC(g, color)
                dot(g, g.rest, color)
            }
            scanLine(color, y, w, g.k, band)
        } else {
            val t = (p - 0.6f) / 0.4f
            fullC(g, color)
            ring(g, g.rest, color, radiusUnits = 5f + 16f * t, alpha = 0.55f * (1f - t), widthUnits = 2f)
            dot(g, g.rest, color, scale = 1f + 0.25f * sin(PI.toFloat() * t.coerceIn(0f, 1f)))
        }
    }
}

private fun DrawScope.scanLine(color: Color, y: Float, w: Float, k: Float, band: Float) {
    drawRect(
        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = color.alpha * 0.28f), Color.Transparent),
            startY = y - band,
            endY = y + band,
        ),
        topLeft = Offset(0f, y - band),
        size = Size(w, band * 2f),
    )
    drawLine(color.copy(alpha = color.alpha * 0.9f), Offset(0f, y), Offset(w, y), strokeWidth = 1.6f * k, cap = StrokeCap.Round)
}

/** Dalga: çizgi sabit, düğüm noktasından ard arda iki halka yayılır. */
private fun DrawScope.drawRipple(g: LogoGeometry, color: Color, p: Float) {
    fullC(g, color)
    for (i in 0..1) {
        val q = (p + i * 0.5f) % 1f
        val e = 1f - (1f - q) * (1f - q)
        val fade = 1f - q
        ring(g, g.rest, color, radiusUnits = 5f + 17f * e, alpha = 0.6f * fade * fade, widthUnits = 2f)
    }
    dot(g, g.rest, color, scale = 1f + 0.14f * sin(4f * PI.toFloat() * p).coerceAtLeast(0f))
}

/** Çizgi çizimi: [from] çizginin görünen başlangıcı, [dot] noktanın altıgen üzerindeki mesafesi. */
internal class TraceFrame(val from: Float, val dot: Float)

/**
 * [p] 0..1 turunu çizgi durumuna çevirir (mesafeler [a, b] = C çizgisinin başı/sonu):
 * 0-5% nokta C'nin başına gider, 5-40% çizgi baştan silinir, 40-80% sondan geriye çizilir,
 * 80-95% nokta dinlenme konumuna döner, kalanı bekler. p=0 ve p=1'de logo tamdır.
 */
internal fun traceFrame(p: Float, a: Float, b: Float): TraceFrame = when {
    p < 0.05f -> TraceFrame(a, lerpF(0f, a, smooth(p / 0.05f)))
    p < 0.40f -> lerpF(a, b, smooth((p - 0.05f) / 0.35f)).let { TraceFrame(it, it) }
    p < 0.80f -> lerpF(b, a, smooth((p - 0.40f) / 0.40f)).let { TraceFrame(it, it) }
    p < 0.95f -> TraceFrame(a, lerpF(a, 0f, smooth((p - 0.80f) / 0.15f)))
    else -> TraceFrame(a, 0f)
}

private fun DrawScope.drawTrace(g: LogoGeometry, color: Color, p: Float) {
    val f = traceFrame(p, g.cStart, g.cEnd)
    arc(g, f.from, g.cEnd, color)
    dot(g, g.position(f.dot), color, halo = 0.18f)
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
    ring(g, g.rest, color, radiusUnits = 5f + 24f * e, alpha = 0.7f * (1f - burst), widthUnits = 2.4f)
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
            ring(g, g.rest, color, radiusUnits = 5f + 18f * t, alpha = 0.6f * (1f - t), widthUnits = 2.2f)
            dot(g, g.rest, color, scale = 1f + 0.5f * sin(PI.toFloat() * t.coerceIn(0f, 1f)) * (1f - t))
        }
    }
}
