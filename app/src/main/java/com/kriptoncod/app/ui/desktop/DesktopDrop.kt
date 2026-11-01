package com.kriptoncod.app.ui.desktop

import android.view.DragEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * AI sekmesinin içeriği: başka bir uygulamadan (ör. çok pencereli/harici ekran kullanımında) sürüklenen dosyalar buraya
 * bırakılabilir; sürüklerken sekme vurgulanır. Dosyalar penceresinden sürükleme ayrıdır (alt çubuktaki AI öğesine bırakılır).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AiDropArea(
    label: String,
    tint: Color,
    onSystemDrop: (DragEvent) -> Boolean,
    content: @Composable () -> Unit,
) {
    val dropNow by rememberUpdatedState(onSystemDrop)
    var hover by remember { mutableStateOf(false) }
    val target = remember {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) {
                hover = true
            }

            override fun onExited(event: DragAndDropEvent) {
                hover = false
            }

            override fun onEnded(event: DragAndDropEvent) {
                hover = false
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                hover = false
                return dropNow(event.toAndroidDragEvent())
            }
        }
    }
    Box(Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { true }, target = target)) {
        content()
        if (hover) DropOverlay(label, tint)
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
