package com.kriptoncod.app.ui.desktop

import android.view.DragEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateRectAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kriptoncod.app.R
import com.kriptoncod.app.ui.theme.KriptonTheme
import kotlin.math.roundToInt

/** Minimize edilmiş pencere ekran dışına taşınır (bileşimde kalır: web sayfası, gezinme durumu vb. kaybolmaz). */
private const val OFFSCREEN_PX = -30000

/**
 * Masaüstü penceresi: başlık çubuğu (sürükle = taşı, çift dokun = büyüt, düğmeler), içerik ve 8 yönlü yeniden boyutlandırma.
 * Konum/boyut [win]'den ve [area]'dan türetilir; sürüklerken anlık, aksi hâlde yumuşak geçişle uygulanır.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun WindowFrame(
    win: DesktopWindow,
    area: DSize,
    z: Float,
    focused: Boolean,
    tint: Color,
    title: String,
    animate: Boolean,
    /** Uygulama içi sürüklemede (dosya yöneticisinden) bu pencere hedef mi? */
    dropHover: Boolean,
    dropLabel: String,
    /** true: başka uygulamadan sistem sürükle-bırakı kabul edilir. */
    acceptSystemDrop: Boolean,
    onSystemDrop: (DragEvent) -> Boolean,
    onFocus: () -> Unit,
    onMinimize: () -> Unit,
    onToggleMax: () -> Unit,
    onClose: () -> Unit,
    /** (başlık içindeki dokunma noktası px, başlık genişliği px). */
    onMoveStart: (Offset, Float) -> Unit,
    /** dp cinsinden fark. */
    onMove: (Float, Float) -> Unit,
    onMoveEnd: () -> Unit,
    onResizeStart: () -> Unit,
    onResize: (Int, Float, Float) -> Unit,
    onResizeEnd: () -> Unit,
    icon: @Composable () -> Unit,
    titleActions: @Composable RowScope.() -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val d = density.density
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme

    val box = Desktop.boxOf(win, area)
    val rect by animateRectAsState(
        targetValue = Rect(box.x * d, box.y * d, box.right * d, box.bottom * d),
        animationSpec = if (animate) tween<Rect>(220, easing = FastOutSlowInEasing) else snap<Rect>(),
        label = "winRect",
    )
    val borderColor by animateColorAsState(if (focused) tint else k.nodeBorder, tween(160), label = "winBorder")
    val maximized = win.mode == WinMode.MAXIMIZED
    val shape = RoundedCornerShape(if (maximized) 0.dp else 12.dp)
    val minimized = win.minimized

    val focusNow by rememberUpdatedState(onFocus)
    val toggleMaxNow by rememberUpdatedState(onToggleMax)
    val moveStartNow by rememberUpdatedState(onMoveStart)
    val moveNow by rememberUpdatedState(onMove)
    val moveEndNow by rememberUpdatedState(onMoveEnd)
    val systemDropNow by rememberUpdatedState(onSystemDrop)
    var titleWidthPx by remember { mutableFloatStateOf(1f) }
    var systemHover by remember { mutableStateOf(false) }

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                systemHover = true
            }

            override fun onExited(event: DragAndDropEvent) {
                systemHover = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                systemHover = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                systemHover = false
                return systemDropNow(event.toAndroidDragEvent())
            }
        }
    }

    Box(
        Modifier
            .offset { IntOffset(rect.left.roundToInt() + (if (minimized) OFFSCREEN_PX else 0), rect.top.roundToInt()) }
            .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
            .zIndex(z)
            .alpha(if (minimized) 0f else 1f)
            // Pencerenin herhangi bir yerine dokunmak onu öne alır (dokunma tüketilmez; sayfa normal çalışır).
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val e = awaitPointerEvent(PointerEventPass.Initial)
                        if (e.changes.any { it.pressed && !it.previousPressed }) focusNow()
                    }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(scheme.surface),
        ) {
            // Başlık çubuğu
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(if (focused) tint.copy(alpha = 0.14f) else Color.Transparent)
                    .onSizeChanged { titleWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleMaxNow() }) }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { start -> moveStartNow(start, titleWidthPx) },
                            onDragEnd = { moveEndNow() },
                            onDragCancel = { moveEndNow() },
                        ) { change, drag ->
                            change.consume()
                            moveNow(drag.x / d, drag.y / d)
                        }
                    }
                    .padding(start = 10.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                titleActions()
                CaptionButton(onClick = onMinimize, description = stringResource(R.string.desktop_win_minimize)) {
                    MinimizeGlyph(scheme.onSurface)
                }
                CaptionButton(
                    onClick = onToggleMax,
                    description = stringResource(if (maximized) R.string.desktop_win_restore else R.string.desktop_win_maximize),
                ) { MaximizeGlyph(scheme.onSurface, restore = maximized) }
                CaptionButton(onClick = onClose, description = stringResource(R.string.desktop_win_close)) {
                    CloseGlyph(scheme.onSurface)
                }
            }
            HorizontalDivider(color = k.nodeBorder.copy(alpha = 0.5f))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(
                        if (acceptSystemDrop) {
                            Modifier.dragAndDropTarget(shouldStartDragAndDrop = { true }, target = dropTarget)
                        } else {
                            Modifier
                        },
                    ),
            ) {
                content()
                if (dropHover || systemHover) DropOverlay(dropLabel, tint)
            }
        }

        // Çerçeve çizgisi içeriğin ÜSTÜNDE (web sayfası kenarlığı örtmesin); dokunmayı etkilemez.
        Box(Modifier.fillMaxSize().border(if (focused) 2.dp else 1.5.dp, borderColor, shape))

        if (!maximized) {
            val edge = 6.dp
            val topCorner = 14.dp
            val bottomCorner = 28.dp
            // Kenarlar
            ResizeHandle(Modifier.align(Alignment.CenterStart).fillMaxHeight().padding(top = topCorner, bottom = bottomCorner).width(edge + 4.dp), Edge.LEFT, d, onResizeStart, onResize, onResizeEnd)
            ResizeHandle(Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = topCorner, bottom = bottomCorner).width(edge + 4.dp), Edge.RIGHT, d, onResizeStart, onResize, onResizeEnd)
            ResizeHandle(Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(horizontal = topCorner).height(edge), Edge.TOP, d, onResizeStart, onResize, onResizeEnd)
            ResizeHandle(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = bottomCorner).height(edge + 4.dp), Edge.BOTTOM, d, onResizeStart, onResize, onResizeEnd)
            // Köşeler
            ResizeHandle(Modifier.align(Alignment.TopStart).size(topCorner), Edge.LEFT or Edge.TOP, d, onResizeStart, onResize, onResizeEnd)
            ResizeHandle(Modifier.align(Alignment.TopEnd).size(topCorner), Edge.RIGHT or Edge.TOP, d, onResizeStart, onResize, onResizeEnd)
            ResizeHandle(Modifier.align(Alignment.BottomStart).size(bottomCorner), Edge.LEFT or Edge.BOTTOM, d, onResizeStart, onResize, onResizeEnd)
            ResizeHandle(
                Modifier.align(Alignment.BottomEnd).size(bottomCorner),
                Edge.RIGHT or Edge.BOTTOM,
                d,
                onResizeStart,
                onResize,
                onResizeEnd,
                grip = if (focused) tint else k.muted,
            )
        }
    }
}

/** Başlık çubuğu düğmesi (38dp dokunma alanı). */
@Composable
private fun CaptionButton(onClick: () -> Unit, description: String, glyph: @Composable () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(38.dp).semantics { contentDescription = description }) { glyph() }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier,
    edges: Int,
    density: Float,
    onStart: () -> Unit,
    onResize: (Int, Float, Float) -> Unit,
    onEnd: () -> Unit,
    grip: Color? = null,
) {
    val startNow by rememberUpdatedState(onStart)
    val resizeNow by rememberUpdatedState(onResize)
    val endNow by rememberUpdatedState(onEnd)
    val desc = stringResource(R.string.desktop_win_resize)
    Box(
        modifier
            .semantics { contentDescription = desc }
            .pointerInput(edges) {
                detectDragGestures(
                    onDragStart = { startNow() },
                    onDragEnd = { endNow() },
                    onDragCancel = { endNow() },
                ) { change, drag ->
                    change.consume()
                    resizeNow(edges, drag.x / density, drag.y / density)
                }
            },
        contentAlignment = Alignment.BottomEnd,
    ) {
        if (grip != null) Box(Modifier.padding(4.dp)) { GripGlyph(grip) }
    }
}

@Composable
private fun DropOverlay(label: String, tint: Color) {
    Box(
        Modifier
            .fillMaxSize()
            .background(tint.copy(alpha = 0.20f))
            .border(3.dp, tint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(24.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

/** Sürüklerken bırakılırsa pencerenin yerleşeceği bölgeyi gösteren yarı saydam önizleme. */
@Composable
internal fun SnapPreview(zone: DBox, tint: Color) {
    val density = LocalDensity.current
    val d = density.density
    Box(
        Modifier
            .offset { IntOffset((zone.x * d).roundToInt(), (zone.y * d).roundToInt()) }
            .size(with(density) { (zone.w * d).toDp() }, with(density) { (zone.h * d).toDp() })
            .padding(4.dp)
            .background(tint.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .border(2.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(12.dp)),
    )
}
