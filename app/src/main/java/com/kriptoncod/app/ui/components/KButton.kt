package com.kriptoncod.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.ui.theme.KriptonTheme
import com.kriptoncod.app.ui.theme.pressScale

enum class KButtonStyle { Primary, Outline, Danger }

/** Gölgesiz, 1.5dp kenarlıklı düğme. */
@Composable
fun KButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: KButtonStyle = KButtonStyle.Primary,
    enabled: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val k = KriptonTheme.colors
    val container: Color
    val content: Color
    val border: Color
    when (style) {
        KButtonStyle.Primary -> {
            container = scheme.primary
            content = scheme.onPrimary
            border = scheme.primary
        }

        KButtonStyle.Outline -> {
            container = Color.Transparent
            content = scheme.primary
            border = k.nodeBorder
        }

        KButtonStyle.Danger -> {
            container = Color.Transparent
            content = k.danger
            border = k.danger
        }
    }
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp).pressScale(interaction),
        interactionSource = interaction,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        elevation = null,
        border = BorderStroke(1.5.dp, if (enabled) border else border.copy(alpha = 0.38f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = Color.Transparent,
            disabledContentColor = k.muted.copy(alpha = 0.6f),
        ),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }
}
