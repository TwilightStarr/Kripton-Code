package com.kriptoncod.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
 * Düz, tek renkli logo: yuvarlatılmış altıgen (küp silueti) içinde kalın, sağı açık "C"
 * ve açıklıkta küçük bir düğüm noktası. Renkler aktif temadan gelir; gradyan/parlama yok.
 */
@Composable
fun KriptonCodLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 96.dp,
    shapeColor: Color = MaterialTheme.colorScheme.primary,
    markColor: Color = MaterialTheme.colorScheme.onPrimary,
) {
    val description = stringResource(R.string.logo_description)
    Canvas(modifier = modifier.size(logoSize).semantics { contentDescription = description }) {
        val s = this.size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val r = s * 0.44f

        // Köşeleri yuvarlatmak için aynı renkte ince yuvarlak birleşimli çizgi eklenir.
        val hex = Path().apply {
            moveTo(c.x, c.y - r)
            lineTo(c.x + 0.866f * r, c.y - 0.5f * r)
            lineTo(c.x + 0.866f * r, c.y + 0.5f * r)
            lineTo(c.x, c.y + r)
            lineTo(c.x - 0.866f * r, c.y + 0.5f * r)
            lineTo(c.x - 0.866f * r, c.y - 0.5f * r)
            close()
        }
        drawPath(hex, shapeColor)
        drawPath(hex, shapeColor, style = Stroke(width = r * 0.14f, join = StrokeJoin.Round))

        // "C": başlangıç 40°, 280° tarar; sağ taraf açık kalır.
        val cr = r * 0.393f
        drawArc(
            color = markColor, startAngle = 40f, sweepAngle = 280f, useCenter = false,
            topLeft = Offset(c.x - cr, c.y - cr), size = Size(cr * 2f, cr * 2f),
            style = Stroke(width = r * 0.232f, cap = StrokeCap.Round),
        )
        drawCircle(color = markColor, radius = r * 0.086f, center = Offset(c.x + r * 0.41f, c.y))
    }
}
