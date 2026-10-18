package com.kriptoncod.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.pressScale

enum class NodeAccent { Blue, Green, Red }

/** Akış düğümü görünümlü kart: 1.5dp kenarlık, gölge yok. */
@Composable
fun NodeCard(
    modifier: Modifier = Modifier,
    accent: NodeAccent = NodeAccent.Blue,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val k = KriptonTheme.colors
    val (fill, border) = when (accent) {
        NodeAccent.Blue -> k.nodeFill to k.nodeBorder
        NodeAccent.Green -> k.arrowFill to k.arrow
        NodeAccent.Red -> k.dangerFill to k.danger
    }
    val shape = RoundedCornerShape(16.dp)
    val stroke = BorderStroke(1.5.dp, border)
    val inner: @Composable () -> Unit = { Column(Modifier.padding(16.dp), content = content) }
    if (onClick != null) {
        val interaction = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick,
            modifier = modifier.pressScale(interaction),
            interactionSource = interaction,
            shape = shape,
            color = fill,
            border = stroke,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = inner,
        )
    } else {
        Surface(
            modifier = modifier,
            shape = shape,
            color = fill,
            border = stroke,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            content = inner,
        )
    }
}
