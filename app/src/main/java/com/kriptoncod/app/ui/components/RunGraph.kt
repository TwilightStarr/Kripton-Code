package com.kriptoncod.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R
import com.kriptoncod.app.domain.GraphLayout
import com.kriptoncod.app.domain.GraphNodeUi
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.ui.theme.KriptonTheme

private const val MIN_SCALE = 0.2f
private const val MAX_SCALE = 3f
private const val ZOOM_STEP = 1.35f
private const val MIN_PER_ROW = 3
private const val MAX_PER_ROW = 10

/** Kaydırma/yakınlaştırma durumu: ekran pikseli cinsinden. İçerik sol-üst köşeden ölçeklenir. */
private class Viewport {
    var scale by mutableStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)

    /** Kullanıcı elle değiştirdiyse grafik büyürken görünüm kendiliğinden sıfırlanmaz. */
    var userMoved by mutableStateOf(false)
}

private fun clampAxis(value: Float, view: Float, content: Float, slackStart: Float, slackEnd: Float): Float {
    val lo = minOf(0f, view - content) - slackEnd
    val hi = maxOf(0f, view - content) + slackStart
    return value.coerceIn(lo, hi)
}

/**
 * Run'ın düğüm grafiği. Düğüm büyüklüğü, işi yapan yapay zekanın gücüyle orantılıdır; renk/kenarlık
 * işin durumunu gösterir. Düğüme dokununca [onSelect] çağrılır.
 *
 * Kullanım: tek parmakla sürükle, iki parmakla yakınlaştır/uzaklaştır; sağ üstteki düğmeler (+, −, Sığdır)
 * aynısını tek dokunuşla yapar. Satır başına düğüm sayısı ekran genişliğine göre seçilir, böylece grafik
 * ilk açılışta yatay kaydırma gerektirmeden ekrana sığar.
 *
 * @param bottomInset grafiğin altını örten panelin yüksekliği (ayrıntı paneli); seçilen düğüm bunun
 *   üstünde kalacak şekilde görünüm kaydırılır ve son satır paneli aşıp yukarı çekilebilir.
 */
@Composable
fun RunGraph(
    nodes: List<GraphNodeUi>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    bottomInset: Dp = 0.dp,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current.density
    val vp = remember { Viewport() }

    BoxWithConstraints(modifier.clipToBounds()) {
        val viewW = constraints.maxWidth.toFloat()
        val viewH = constraints.maxHeight.toFloat()
        val insetPx = bottomInset.value * density
        val slack = 64f * density

        // Ekran genişliğine sığan satır başına düğüm sayısı (en az 3, en çok 10).
        val cellDp = GRAPH_MAX_SIZE + GRAPH_GAP
        val perRow = (((viewW / density) - 2 * GRAPH_PADDING + GRAPH_GAP) / cellDp).toInt()
            .coerceIn(MIN_PER_ROW, MAX_PER_ROW)
        val layout = remember(nodes, perRow) {
            GraphLayout.layout(
                nodes,
                maxPerRow = perRow,
                minSize = GRAPH_MIN_SIZE,
                maxSize = GRAPH_MAX_SIZE,
                gap = GRAPH_GAP,
                padding = GRAPH_PADDING,
            )
        }
        val byId = remember(layout) { layout.nodes.associateBy { it.node.id } }
        val contentW = layout.width * density
        val contentH = layout.height * density

        fun clamp(o: Offset, s: Float) = Offset(
            clampAxis(o.x, viewW, contentW * s, slack, slack),
            clampAxis(o.y, viewH, contentH * s, slack, slack + insetPx),
        )

        /** Başlangıç görünümü: genişliğe sığdır (büyütme yok), üstten başla; küçük grafik ortalanır. */
        fun applyStartView() {
            if (viewW <= 0f || contentW <= 0f) return
            val s = minOf(1f, viewW / contentW).coerceIn(MIN_SCALE, 1f)
            val x = (viewW - contentW * s) / 2f
            val y = if (contentH * s <= viewH - insetPx) (viewH - insetPx - contentH * s) / 2f else 0f
            vp.scale = s
            vp.offset = Offset(x, y)
        }

        /** Tüm grafiği tek bakışta göster. */
        fun applyFitAll() {
            if (viewW <= 0f || viewH <= 0f || contentW <= 0f || contentH <= 0f) return
            val s = minOf(viewW / contentW, (viewH - insetPx) / contentH).coerceIn(MIN_SCALE, 1.5f)
            vp.scale = s
            vp.offset = Offset((viewW - contentW * s) / 2f, ((viewH - insetPx) - contentH * s) / 2f)
            vp.userMoved = true
        }

        fun zoomAround(factor: Float, focus: Offset) {
            val newScale = (vp.scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            val f = newScale / vp.scale
            vp.offset = clamp(focus - (focus - vp.offset) * f, newScale)
            vp.scale = newScale
            vp.userMoved = true
        }

        // Grafik büyüyünce ya da pencere değişince, kullanıcı dokunmadıysa başlangıç görünümüne dön.
        LaunchedEffect(layout.width, layout.height, viewW, viewH) {
            if (!vp.userMoved) applyStartView()
        }

        // Seçilen düğüm ayrıntı panelinin altında kalıyorsa görünür alana getir.
        LaunchedEffect(selectedId, insetPx) {
            val p = selectedId?.let { byId[it] } ?: return@LaunchedEffect
            val sx = vp.offset.x + p.x * density * vp.scale
            val sy = vp.offset.y + p.y * density * vp.scale
            val visibleH = viewH - insetPx
            val margin = 48f * density
            if (sx < margin || sx > viewW - margin || sy < margin || sy > visibleH - margin) {
                vp.offset = clamp(
                    Offset(viewW / 2f - p.x * density * vp.scale, visibleH / 2f - p.y * density * vp.scale),
                    vp.scale,
                )
                vp.userMoved = true
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(viewW, viewH, contentW, contentH, insetPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        vp.userMoved = true
                        val newScale = (vp.scale * zoom).coerceIn(MIN_SCALE, MAX_SCALE)
                        val f = newScale / vp.scale
                        vp.offset = clamp(centroid - (centroid - vp.offset) * f + pan, newScale)
                        vp.scale = newScale
                    }
                },
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .size(layout.width.dp, layout.height.dp)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0f)
                        scaleX = vp.scale
                        scaleY = vp.scale
                        translationX = vp.offset.x
                        translationY = vp.offset.y
                    },
            ) {
                val edgeColor = k.nodeBorder
                Canvas(Modifier.fillMaxSize()) {
                    layout.edges.forEach { e ->
                        val a = byId[e.from]
                        val b = byId[e.to]
                        if (a != null && b != null) {
                            drawLine(
                                color = edgeColor,
                                start = Offset(a.x.dp.toPx(), a.y.dp.toPx()),
                                end = Offset(b.x.dp.toPx(), b.y.dp.toPx()),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                    }
                }
                layout.nodes.forEach { p ->
                    val state = p.node.state
                    val fill = when (state) {
                        JobState.DONE -> k.arrowFill
                        JobState.FAILED -> k.dangerFill
                        else -> k.nodeFill
                    }
                    val border = when (state) {
                        JobState.PENDING -> k.nodeBorder
                        JobState.RUNNING -> scheme.primary
                        JobState.WAITING -> scheme.tertiary
                        JobState.DONE -> k.arrow
                        JobState.FAILED -> k.danger
                    }
                    val selected = p.node.id == selectedId
                    val borderWidth = if (selected) {
                        4.dp
                    } else if (state == JobState.RUNNING) {
                        3.dp
                    } else {
                        2.dp
                    }
                    Box(
                        modifier = Modifier
                            .offset((p.x - p.size / 2f).dp, (p.y - p.size / 2f).dp)
                            .size(p.size.dp)
                            .pulseRing(state == JobState.RUNNING, scheme.primary)
                            .clip(CircleShape)
                            .background(fill)
                            .border(borderWidth, if (selected) scheme.primary else border, CircleShape)
                            .clickable { onSelect(p.node.id) }
                            .semantics { contentDescription = p.node.label },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            p.node.label,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = scheme.onSurface,
                        )
                    }
                }
            }
        }

        // Açıklama (sol üst) ve yakınlaştırma düğmeleri (sağ üst).
        Text(
            stringResource(R.string.run_graph_legend),
            style = MaterialTheme.typography.labelSmall,
            color = k.muted,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .widthIn(max = ((viewW / density) - 84f).coerceAtLeast(120f).dp)
                .clip(RoundedCornerShape(8.dp))
                .background(k.nodeFill.copy(alpha = 0.85f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
        Column(
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.End,
        ) {
            val center = Offset(viewW / 2f, (viewH - insetPx) / 2f)
            ZoomButton("+", stringResource(R.string.run_graph_zoom_in)) { zoomAround(ZOOM_STEP, center) }
            ZoomButton("−", stringResource(R.string.run_graph_zoom_out)) { zoomAround(1f / ZOOM_STEP, center) }
            ZoomButton(stringResource(R.string.run_graph_fit), stringResource(R.string.run_graph_fit_desc)) { applyFitAll() }
        }
    }
}

/** 48dp dokunma hedefi sağlayan küçük yuvarlak düğme. */
@Composable
private fun ZoomButton(label: String, description: String, onClick: () -> Unit) {
    val k = KriptonTheme.colors
    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(k.nodeFill)
            .border(1.5.dp, k.nodeBorder, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

internal const val GRAPH_MIN_SIZE = 48f
internal const val GRAPH_MAX_SIZE = 84f
internal const val GRAPH_GAP = 20f
internal const val GRAPH_PADDING = 16f

/** Çalışan düğümün çevresinden dışa doğru yayılıp sönen halka (animasyonlar kapalıysa çizilmez). */
@Composable
private fun Modifier.pulseRing(active: Boolean, color: Color): Modifier {
    if (!active || !LocalAnimationsEnabled.current) return this
    val phase = rememberInfiniteTransition(label = "nodePulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "nodePulsePhase",
    )
    return drawBehind {
        val t = phase.value
        drawCircle(
            color = color.copy(alpha = 0.55f * (1f - t)),
            radius = size.minDimension / 2f + 14.dp.toPx() * t,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}
