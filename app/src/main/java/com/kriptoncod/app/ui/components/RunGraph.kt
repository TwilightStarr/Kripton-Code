package com.kriptoncod.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.domain.GraphLayout
import com.kriptoncod.app.domain.GraphNodeUi
import com.kriptoncod.app.domain.JobState
import com.kriptoncod.app.ui.theme.KriptonTheme

/**
 * Run'ın düğüm grafiği. Düğüm büyüklüğü, işi yapan yapay zekanın gücüyle orantılıdır;
 * renk/kenarlık işin durumunu gösterir. Düğüme dokununca [onSelect] çağrılır.
 */
@Composable
fun RunGraph(
    nodes: List<GraphNodeUi>,
    selectedId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = remember(nodes) { GraphLayout.layout(nodes) }
    val byId = remember(layout) { layout.nodes.associateBy { it.node.id } }
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val edgeColor = k.nodeBorder

    Box(
        modifier
            .horizontalScroll(rememberScrollState())
            .verticalScroll(rememberScrollState()),
    ) {
        Box(Modifier.size(layout.width.dp, layout.height.dp)) {
            Canvas(Modifier.matchParentSize()) {
                layout.edges.forEach { e ->
                    val a = byId[e.from]
                    val b = byId[e.to]
                    if (a != null && b != null) {
                        drawLine(
                            color = edgeColor,
                            start = Offset(a.x.dp.toPx(), a.y.dp.toPx()),
                            end = Offset(b.x.dp.toPx(), b.y.dp.toPx()),
                            strokeWidth = 1.5.dp.toPx(),
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
                val width = if (selected) 4.dp else if (state == JobState.RUNNING) 3.dp else 1.5.dp
                Box(
                    modifier = Modifier
                        .offset((p.x - p.size / 2f).dp, (p.y - p.size / 2f).dp)
                        .size(p.size.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .border(width, if (selected) scheme.primary else border, CircleShape)
                        .clickable { onSelect(p.node.id) }
                        .semantics { contentDescription = p.node.label },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(p.node.label, style = MaterialTheme.typography.labelMedium, color = scheme.onSurface)
                }
            }
        }
    }
}
