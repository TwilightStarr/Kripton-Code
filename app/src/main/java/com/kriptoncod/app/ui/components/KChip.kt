package com.kriptoncod.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.LocalAnimationsEnabled
import com.kriptoncod.app.ui.theme.popOnSelect

/** Listeden seçim çipi (radyo davranışı). */
@Composable
fun KChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val k = KriptonTheme.colors
    val scheme = MaterialTheme.colorScheme
    // Seçim değişince renkler yumuşakça geçer, seçilen çip yaylanarak "zıplar".
    val spec = if (LocalAnimationsEnabled.current) tween<Color>(220) else snap<Color>()
    val fill by animateColorAsState(if (selected) k.nodeFill else scheme.surface, spec, label = "chipFill")
    val edge by animateColorAsState(if (selected) scheme.primary else k.nodeBorder, spec, label = "chipEdge")
    val content by animateColorAsState(if (selected) scheme.primary else scheme.onSurface, spec, label = "chipContent")
    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.popOnSelect(selected).semantics { role = Role.RadioButton },
        shape = MaterialTheme.shapes.small,
        color = fill,
        contentColor = content,
        border = BorderStroke(1.5.dp, edge),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Box(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
