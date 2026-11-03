package com.kriptoncod.app.ui.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalMotionScale
import com.kriptoncod.app.ui.theme.Motion
import kotlin.math.max
import kotlin.math.roundToInt

// Serbest pencere çerçevesi (TabLayer'ın yerini alır). İçerik HER ZAMAN bileşimde kalır: küçültülmüş pencere ekran dışına
// (OFFSCREEN_PX) taşınır ve saydamdır; AI web oturumları, sayfalar ve gezinme durumu kaybolmaz. Çocuk sırası değişmez, üst üste
// sırası yalnız zIndex ile verilir.
//
// Sürükleme/boyutlandırma sırasında geçici dikdörtgen `live` durumunda tutulur ve YALNIZ yerleşim (offset/layout) ve çizim
// bloklarında okunur; içerik (AppRoot/WebView) yeniden composition'a girmez. Kalıcı durum bırakınca yazılır.

/** Sürüklemeyle paylaşılan geçici durum: yaslama önizlemesi (alan koordinatı, dp). Yalnız çizim bloğunda okunur. */
@Stable
class WindowDragHub {
    var snapPreview by mutableStateOf<WinGeom?>(null)
}

private val TITLE_HEIGHT = 36.dp
private val BUTTON_WIDTH = 40.dp
private val EDGE_HIT = 14.dp
private val CORNER_HIT = 24.dp
private val FRAME_RADIUS = 10.dp

/** Açılış / geri getirme animasyonunun başlangıç ölçeği: simgenin boyutuna yakın küçük bir başlangıç. */
private const val REVEAL_FROM_SCALE = 0.12f

/** Kapanışta pencerenin küçüldüğü ölçek ve aşağı kaydığı mesafe. */
private const val CLOSE_TO_SCALE = 0.82f
private val CLOSE_SLIDE = 64.dp

private class ResizeSession {
    var base = WinGeom(0f, 0f, 0f, 0f)
    var sum = Offset.Zero
}

/**
 * Bir pencerenin çerçevesi + içeriği. Başlık çubuğu: sol uygulama simgesi ve adı, sağ Küçült / Büyüt-Geri al / Kapat; başlığa çift
 * dokunuş büyütür/geri alır, başlıktan sürükleme taşır (büyütülmüş ya da yaslıyken önce geri alır), alan kenarına bırakınca yaslanır.
 * Normal moddaki pencerenin kenar ve köşelerinde 8 yönlü yeniden boyutlandırma tutamacı vardır.
 *
 * @param win pencerenin kalıcı durumu (yerleşim bloğunda okunur).
 * @param area masaüstü alanı (dp).
 * @param focused odaklı (en üstteki küçültülmemiş) pencere mi.
 * @param zOrder üst üste sırası (büyük = üstte).
 * @param closing kapanış animasyonu oynasın; bitince [onClosed] çağrılır.
 * @param revealFromIcon ilk görününce çubuk simgesinden büyüyerek açılsın.
 * @param iconCenter simgenin merkezi (alan koordinatı, piksel); küçültme/geri getirme/açılış animasyonu ona doğru/ondan olur.
 * @param highlight dosya bu pencerenin üstünde sürükleniyor (bırakma hedefi).
 */
@Composable
internal fun WindowFrame(
    title: String,
    info: AppInfo?,
    tint: Color,
    win: () -> WinState,
    area: WinArea,
    focused: Boolean,
    zOrder: Float,
    closing: Boolean,
    revealFromIcon: Boolean,
    iconCenter: () -> Offset?,
    highlight: Boolean,
    hub: WindowDragHub,
    onFocus: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMax: () -> Unit,
    onClose: () -> Unit,
    onMoveEnd: (WinGeom, SnapZone?) -> Unit,
    onResizeEnd: (WinGeom) -> Unit,
    onClosed: () -> Unit,
    content: @Composable () -> Unit,
) {
    val motion = LocalMotionScale.current
    val d = LocalDensity.current.density
    val scheme = MaterialTheme.colorScheme
    val k = KriptonTheme.colors

    val winNow by rememberUpdatedState(win)
    val areaNow by rememberUpdatedState(area)
    val dNow by rememberUpdatedState(d)
    val iconNow by rememberUpdatedState(iconCenter)
    val closingNow by rememberUpdatedState(closing)
    val onFocusNow by rememberUpdatedState(onFocus)
    val onToggleMaxNow by rememberUpdatedState(onToggleMax)
    val onMoveEndNow by rememberUpdatedState(onMoveEnd)
    val onResizeEndNow by rememberUpdatedState(onResizeEnd)
    val onClosedNow by rememberUpdatedState(onClosed)

    val state = win()
    val target = Windowing.rectOf(state, area)

    // Gösterilen dikdörtgen: sürüklerken `live`, değilse hedefe doğru süren animasyon (büyüt/geri al/yasla geçişleri).
    var live by remember { mutableStateOf<WinGeom?>(null) }
    var interacting by remember { mutableStateOf(false) }
    var animFrom by remember { mutableStateOf(target) }
    var animTo by remember { mutableStateOf(target) }
    val t = remember { Animatable(1f) }

    fun shown(): WinGeom = live ?: WinGeom.lerp(animFrom, animTo, t.value)

    LaunchedEffect(target) {
        if (target != animTo) {
            animFrom = shown()
            animTo = target
            t.snapTo(0f)
            t.animateTo(1f, motion.spec(Motion.MEDIUM, Motion.Emphasized))
        }
    }

    // Görünürlük: açılış / küçültme / geri getirme / kapanış. `presence` 0 = simgede (görünmez), 1 = yerinde.
    val shownWin = state.mode != WinMode.MINIMIZED && !closing
    var revealing by remember { mutableStateOf(revealFromIcon) }
    val presence = remember { Animatable(if (revealFromIcon || !shownWin) 0f else 1f) }
    val exit = remember { Animatable(0f) }

    LaunchedEffect(shownWin, closing) {
        when {
            closing -> {
                // Görünmeyen (küçültülmüş) pencereyi canlandırmadan kapat.
                if (presence.value > 0.02f) exit.animateTo(1f, motion.spec(Motion.MEDIUM, Motion.EmphasizedAccelerate))
                onClosedNow()
            }

            shownWin -> {
                val reveal = revealing
                presence.animateTo(
                    1f,
                    motion.spec(if (reveal) Motion.LONG else Motion.MEDIUM, if (reveal) Motion.EmphasizedDecelerate else Motion.Emphasized),
                )
                revealing = false
            }

            else -> {
                revealing = false
                presence.animateTo(0f, motion.spec(Motion.MEDIUM, Motion.EmphasizedAccelerate))
            }
        }
    }

    fun hidden(): Boolean = winNow().mode == WinMode.MINIMIZED && !closingNow && presence.value <= 0.001f

    // ---- Taşıma (başlık çubuğu) ----
    val moveModifier = Modifier
        .pointerInput(Unit) { detectTapGestures(onDoubleTap = { onToggleMaxNow() }) }
        .pointerInput(Unit) {
            var origin = WinGeom(0f, 0f, 0f, 0f)
            var pointer0 = Offset.Zero
            var sum = Offset.Zero
            var zone: SnapZone? = null
            var enabled = false
            detectDragGestures(
                onDragStart = { offset ->
                    val w = winNow()
                    val a = areaNow
                    // Dar alanda (telefon) büyütülmüş pencere sürüklenmez.
                    enabled = !(a.narrow && w.mode == WinMode.MAXIMIZED)
                    if (enabled) {
                        val cur = shown()
                        val pointerX = cur.x + offset.x / dNow
                        origin = Windowing.dragStartGeom(w, pointerX, a)
                        pointer0 = Offset(pointerX, cur.y + offset.y / dNow)
                        sum = Offset.Zero
                        zone = null
                        live = origin
                        interacting = true
                    }
                },
                onDragEnd = {
                    val g = live
                    val z = zone
                    hub.snapPreview = null
                    if (enabled && g != null) {
                        animFrom = g
                        animTo = g
                        live = null
                        interacting = false
                        onMoveEndNow(g, z)
                    }
                    enabled = false
                },
                onDragCancel = {
                    hub.snapPreview = null
                    if (enabled) {
                        live = null
                        interacting = false
                    }
                    enabled = false
                },
                onDrag = { change, amount ->
                    change.consume()
                    if (enabled) {
                        sum += amount / dNow
                        val a = areaNow
                        live = Windowing.moveGeom(origin, sum.x, sum.y, a)
                        val p = pointer0 + sum
                        zone = Windowing.snapZoneAt(p.x, p.y, a)
                        hub.snapPreview = zone?.let { Windowing.snapRect(it, a) }
                    }
                },
            )
        }

    // ---- Yeniden boyutlandırma ----
    val session = remember { ResizeSession() }

    fun resizeStart() {
        session.base = shown()
        session.sum = Offset.Zero
        live = session.base
        interacting = true
    }

    fun resizeUpdate(handle: ResizeHandle, deltaPx: Offset) {
        session.sum += deltaPx / dNow
        live = Windowing.resizeBy(session.base, handle, session.sum.x, session.sum.y, areaNow)
    }

    fun resizeEnd() {
        val g = live
        if (g != null) {
            animFrom = g
            animTo = g
        }
        live = null
        interacting = false
        if (g != null) onResizeEndNow(g)
    }

    fun resizeCancel() {
        live = null
        interacting = false
    }

    val maxed = state.mode == WinMode.MAXIMIZED
    val resizable = state.mode == WinMode.NORMAL && !closing
    val shape = RoundedCornerShape(if (maxed) 0.dp else FRAME_RADIUS)
    val borderColor = if (focused) k.nodeBorder else k.nodeBorder.copy(alpha = 0.5f)

    val frameModifier = Modifier
        .zIndex(zOrder)
        .offset {
            if (hidden()) {
                IntOffset(OFFSCREEN_PX, 0)
            } else {
                val g = shown()
                IntOffset((g.x * dNow).roundToInt(), (g.y * dNow).roundToInt())
            }
        }
        .layout { measurable, _ ->
            val g = shown()
            val w = max(0, (g.w * density).roundToInt())
            val h = max(0, (g.h * density).roundToInt())
            val placeable = measurable.measure(Constraints.fixed(w, h))
            layout(w, h) { placeable.place(0, 0) }
        }
        .graphicsLayer {
            val p = presence.value
            val e = exit.value
            when {
                e > 0f -> {
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val sc = 1f - (1f - CLOSE_TO_SCALE) * e
                    scaleX = sc
                    scaleY = sc
                    translationY = CLOSE_SLIDE.toPx() * e
                    alpha = p * (1f - e)
                }

                p < 1f -> {
                    // Küçültme/geri getirme/açılış: pencere çubuk simgesine doğru küçülür, simgeden büyür.
                    val g = shown()
                    val c = iconNow()
                    val gw = g.w * dNow
                    val gh = g.h * dNow
                    transformOrigin = if (c == null || gw <= 0f || gh <= 0f) {
                        TransformOrigin(0.5f, 1f)
                    } else {
                        TransformOrigin((c.x - g.x * dNow) / gw, (c.y - g.y * dNow) / gh)
                    }
                    val sc = REVEAL_FROM_SCALE + (1f - REVEAL_FROM_SCALE) * p
                    scaleX = sc
                    scaleY = sc
                    // Önce hızla belirir; büyürken tamamen opak olur.
                    alpha = (p * 2.5f).coerceIn(0f, 1f)
                }
            }
        }
        .then(if (maxed) Modifier else Modifier.shadow(if (focused) 18.dp else 6.dp, shape, clip = false))
        .clip(shape)
        .background(scheme.background)
        .then(if (maxed) Modifier else Modifier.border(1.dp, borderColor, shape))
        // Pencereye (içerik dahil) dokunmak onu öne alır; olay tüketilmez, içeriğe de ulaşır.
        .onAnyPress { onFocusNow() }

    Box(frameModifier) {
        Column(Modifier.fillMaxSize()) {
            WindowTitleBar(
                title = title,
                info = info,
                tint = tint,
                focused = focused,
                maxed = maxed,
                dragModifier = moveModifier,
                onMinimize = onMinimize,
                onToggleMax = onToggleMax,
                onClose = onClose,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                content()
                if (highlight) {
                    Box(Modifier.fillMaxSize().background(tint.copy(alpha = 0.16f)).border(3.dp, tint))
                }
                // İşlem sırasında içeriğin (WebView) olayları yutmaması için içeriğin üstüne saydam yakalayıcı konur.
                if (interacting) {
                    Box(
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                    )
                }
            }
        }
        if (resizable) {
            val start = { resizeStart() }
            val update = { h: ResizeHandle, delta: Offset -> resizeUpdate(h, delta) }
            val end = { resizeEnd() }
            val cancel = { resizeCancel() }
            // Kenar tutamaçları (üst kenar sağdaki düğmeleri örtmez); köşeler en üstte.
            ResizeZone(ResizeHandle.N, Modifier.align(Alignment.TopStart).padding(start = CORNER_HIT, end = CORNER_HIT + BUTTON_WIDTH * 3).fillMaxWidth().height(EDGE_HIT), start, update, end, cancel)
            ResizeZone(ResizeHandle.S, Modifier.align(Alignment.BottomStart).padding(horizontal = CORNER_HIT).fillMaxWidth().height(EDGE_HIT), start, update, end, cancel)
            ResizeZone(ResizeHandle.W, Modifier.align(Alignment.CenterStart).padding(vertical = CORNER_HIT).fillMaxHeight().width(EDGE_HIT), start, update, end, cancel)
            ResizeZone(ResizeHandle.E, Modifier.align(Alignment.CenterEnd).padding(top = TITLE_HEIGHT, bottom = CORNER_HIT).fillMaxHeight().width(EDGE_HIT), start, update, end, cancel)
            ResizeZone(ResizeHandle.NW, Modifier.align(Alignment.TopStart).size(CORNER_HIT), start, update, end, cancel)
            ResizeZone(ResizeHandle.NE, Modifier.align(Alignment.TopEnd).size(CORNER_HIT), start, update, end, cancel)
            ResizeZone(ResizeHandle.SW, Modifier.align(Alignment.BottomStart).size(CORNER_HIT), start, update, end, cancel)
            ResizeZone(ResizeHandle.SE, Modifier.align(Alignment.BottomEnd).size(CORNER_HIT), start, update, end, cancel)
        }
    }
}

/** Görünmez, yalnız dokunmayı yakalayan yeniden boyutlandırma bölgesi (görsel kenarlık pencerenin kendi ince çerçevesidir). */
@Composable
private fun ResizeZone(
    handle: ResizeHandle,
    modifier: Modifier,
    onStart: () -> Unit,
    onDelta: (ResizeHandle, Offset) -> Unit,
    onEnd: () -> Unit,
    onCancel: () -> Unit,
) {
    val startNow by rememberUpdatedState(onStart)
    val deltaNow by rememberUpdatedState(onDelta)
    val endNow by rememberUpdatedState(onEnd)
    val cancelNow by rememberUpdatedState(onCancel)
    Box(
        modifier.pointerInput(handle) {
            detectDragGestures(
                onDragStart = { startNow() },
                onDragEnd = { endNow() },
                onDragCancel = { cancelNow() },
                onDrag = { change, amount ->
                    change.consume()
                    deltaNow(handle, amount)
                },
            )
        },
    )
}

@Composable
private fun WindowTitleBar(
    title: String,
    info: AppInfo?,
    tint: Color,
    focused: Boolean,
    maxed: Boolean,
    dragModifier: Modifier,
    onMinimize: () -> Unit,
    onToggleMax: () -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val k = KriptonTheme.colors
    // Odaklı başlık belirgin (dolgu + uygulama renginde alt çizgi), pasif başlık soluk.
    val background = if (focused) k.nodeFill else scheme.surface
    val ink = if (focused) scheme.onSurface else k.muted
    val lineColor = if (focused) tint else k.nodeBorder
    val lineHeight = if (focused) 2.dp else 1.dp
    val minimizeText = stringResource(R.string.desktop_win_minimize)
    val maximizeText = stringResource(if (maxed) R.string.desktop_win_restore else R.string.desktop_win_maximize)
    val closeText = stringResource(R.string.desktop_win_close)
    Row(
        Modifier
            .fillMaxWidth()
            .height(TITLE_HEIGHT)
            .background(background)
            .drawBehind {
                val line = lineHeight.toPx()
                drawRect(lineColor, Offset(0f, size.height - line), Size(size.width, line))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).fillMaxHeight().then(dragModifier).padding(start = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (info != null) {
                Box(Modifier.alpha(if (focused) 1f else 0.6f)) { DesktopAppIcon(info, 18.dp) }
                Spacer(Modifier.width(8.dp))
            }
            Text(title, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TitleButton(minimizeText, onMinimize) { MinimizeGlyph(ink, 16.dp) }
        TitleButton(maximizeText, onToggleMax) { MaximizeGlyph(ink, restore = maxed, size = 14.dp) }
        TitleButton(closeText, onClose) { CloseGlyph(ink, 16.dp) }
    }
}

@Composable
private fun TitleButton(description: String, onClick: () -> Unit, glyph: @Composable () -> Unit) {
    Box(
        Modifier
            .width(BUTTON_WIDTH)
            .fillMaxHeight()
            .semantics { contentDescription = description }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { glyph() }
}

/** Yaslama önizlemesi: sürüklenen pencerenin bırakılınca oturacağı yer, yarı saydam. Yalnız çizim bloğunda okur; dokunmayı engellemez. */
@Composable
internal fun SnapPreview(hub: WindowDragHub, tint: Color, modifier: Modifier = Modifier) {
    val d = LocalDensity.current.density
    Box(
        modifier.fillMaxSize().drawBehind {
            val g = hub.snapPreview ?: return@drawBehind
            val inset = 4.dp.toPx()
            val topLeft = Offset(g.x * d + inset, g.y * d + inset)
            val size = Size(max(0f, g.w * d - 2 * inset), max(0f, g.h * d - 2 * inset))
            val radius = CornerRadius(FRAME_RADIUS.toPx())
            drawRoundRect(tint.copy(alpha = 0.18f), topLeft, size, radius)
            drawRoundRect(tint.copy(alpha = 0.65f), topLeft, size, radius, style = Stroke(2.dp.toPx()))
        },
    )
}

/** İlk dokunuşta (Initial geçişi) [onPress] çağrılır; olay TÜKETİLMEZ, alttaki içeriğe de ulaşır. Fare ve dokunmatik. */
private fun Modifier.onAnyPress(onPress: () -> Unit): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.type == PointerEventType.Press) onPress()
        }
    }
}
