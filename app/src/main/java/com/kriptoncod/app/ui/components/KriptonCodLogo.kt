package com.kriptoncod.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kriptoncod.app.R

/**
 * Tek renkli, tek çizgili logo: sağ kenarı açık bir altıgen ("C" harfi) ve açıklıkta bir düğüm noktası.
 * Başlatıcı ikonuyla (res/drawable/ic_launcher_foreground.xml) aynı geometridir; renk aktif temadan gelir.
 */
@Composable
fun KriptonCodLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 96.dp,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    val description = stringResource(R.string.logo_description)
    Canvas(modifier = modifier.size(logoSize).semantics { contentDescription = description }) {
        // 108x108'lik başlatıcı ikonun ortadaki 22..86 penceresi (64 birim) tuvale ölçeklenir.
        val k = size.minDimension / 64f
        val ox = -22f * k
        val oy = -22f * k
        fun p(x: Float, y: Float) = Offset(ox + x * k, oy + y * k)

        val path = Path().apply {
            val a = p(76.5f, 41f)
            moveTo(a.x, a.y)
            listOf(p(54f, 28f), p(31.5f, 41f), p(31.5f, 67f), p(54f, 80f), p(76.5f, 67f)).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(width = 9f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(color, radius = 4.5f * k, center = p(76.5f, 54f))
    }
}
