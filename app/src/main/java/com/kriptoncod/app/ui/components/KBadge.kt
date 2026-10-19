package com.kriptoncod.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.ui.theme.KriptonTheme

/** Küçük etiket rozeti ("Önerilen", "Ücretsiz" vb.): 1.5dp kenarlık, gölge yok. */
@Composable
fun KBadge(text: String, modifier: Modifier = Modifier, accent: NodeAccent = NodeAccent.Blue) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    val fill = when (accent) {
        NodeAccent.Blue -> k.nodeFill
        NodeAccent.Green -> k.arrowFill
        NodeAccent.Red -> k.dangerFill
    }
    val border = when (accent) {
        NodeAccent.Blue -> k.nodeBorder
        NodeAccent.Green -> k.arrow
        NodeAccent.Red -> k.danger
    }
    val content = when (accent) {
        NodeAccent.Blue -> scheme.primary
        NodeAccent.Green -> k.arrow
        NodeAccent.Red -> k.danger
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = fill,
        contentColor = content,
        border = BorderStroke(1.5.dp, border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
    }
}
