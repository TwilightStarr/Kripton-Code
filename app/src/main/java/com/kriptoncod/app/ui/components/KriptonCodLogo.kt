package com.kriptoncod.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
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

/** Kripton küpü + ortada kalın, sağı açık "C" (mavi→camgöbeği gradyan, hafif parlama). */
@Composable
fun KriptonCodLogo(modifier: Modifier = Modifier, logoSize: Dp = 96.dp) {
    val description = stringResource(R.string.logo_description)
    Canvas(modifier = modifier.size(logoSize).semantics { contentDescription = description }) {
        val s = this.size.minDimension
        val c = Offset(s / 2f, s / 2f)
        val r = s * 0.44f
        fun pt(dx: Float, dy: Float) = Offset(c.x + dx * r, c.y + dy * r)
        fun face(vararg p: Offset) = Path().apply {
            moveTo(p[0].x, p[0].y)
            for (i in 1 until p.size) lineTo(p[i].x, p[i].y)
            close()
        }

        val top = pt(0f, -1f)
        val ur = pt(0.866f, -0.5f)
        val lr = pt(0.866f, 0.5f)
        val bottom = pt(0f, 1f)
        val ll = pt(-0.866f, 0.5f)
        val ul = pt(-0.866f, -0.5f)

        drawPath(face(top, ur, c, ul), Color(0xFF3B6FE8))
        drawPath(face(ul, c, bottom, ll), Color(0xFF1E3FA8))
        drawPath(face(c, ur, lr, bottom), Color(0xFF2A55CC))

        val edge = Color(0xFF8FB4FF).copy(alpha = 0.7f)
        val edgeStyle = Stroke(width = s * 0.012f, join = StrokeJoin.Round)
        drawPath(face(top, ur, lr, bottom, ll, ul), edge, style = edgeStyle)
        drawPath(Path().apply {
            moveTo(ul.x, ul.y); lineTo(c.x, c.y); lineTo(ur.x, ur.y)
            moveTo(c.x, c.y); lineTo(bottom.x, bottom.y)
        }, edge, style = edgeStyle)

        // "C": başlangıç 40°, 280° tarar; sağ taraf açık kalır.
        val cr = r * 0.5f
        val w = r * 0.28f
        val topLeft = Offset(c.x - cr, c.y - cr)
        val arc = Size(cr * 2f, cr * 2f)
        drawArc(
            color = Color(0xFF0A1230).copy(alpha = 0.7f), startAngle = 40f, sweepAngle = 280f,
            useCenter = false, topLeft = topLeft, size = arc,
            style = Stroke(width = w * 1.5f, cap = StrokeCap.Round),
        )
        for (i in 1..3) {
            drawArc(
                color = Color(0xFF22D3EE).copy(alpha = 0.10f), startAngle = 40f, sweepAngle = 280f,
                useCenter = false, topLeft = topLeft, size = arc,
                style = Stroke(width = w + i * s * 0.03f, cap = StrokeCap.Round),
            )
        }
        drawArc(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF60A5FA), Color(0xFF22D3EE)),
                start = Offset(c.x - cr, c.y - cr),
                end = Offset(c.x + cr, c.y + cr),
            ),
            startAngle = 40f, sweepAngle = 280f, useCenter = false,
            topLeft = topLeft, size = arc,
            style = Stroke(width = w, cap = StrokeCap.Round),
        )
    }
}
