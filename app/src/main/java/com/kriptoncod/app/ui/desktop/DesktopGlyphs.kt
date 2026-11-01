package com.kriptoncod.app.ui.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Çekirdek simge kümesinde (material-icons-core) olmayan masaüstü simgeleri; Canvas ile çizilir.

@Composable
fun AppsGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val r = s * 0.085f
        for (row in 0..2) for (col in 0..2) {
            drawCircle(tint, r, Offset(s * (0.2f + 0.3f * col), s * (0.2f + 0.3f * row)))
        }
    }
}

@Composable
fun MinimizeGlyph(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        drawLine(tint, Offset(s * 0.18f, s * 0.78f), Offset(s * 0.82f, s * 0.78f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** [restore] true: iki üst üste kare (geri al); false: tek kare (büyüt). */
@Composable
fun MaximizeGlyph(tint: Color, restore: Boolean, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val stroke = Stroke(width = 1.8.dp.toPx())
        val s = this.size.minDimension
        if (!restore) {
            drawRect(tint, topLeft = Offset(s * 0.16f, s * 0.16f), size = Size(s * 0.68f, s * 0.68f), style = stroke)
        } else {
            drawRect(tint, topLeft = Offset(s * 0.3f, s * 0.1f), size = Size(s * 0.6f, s * 0.6f), style = stroke)
            drawRect(tint, topLeft = Offset(s * 0.1f, s * 0.3f), size = Size(s * 0.6f, s * 0.6f), style = stroke)
        }
    }
}

@Composable
fun CloseGlyph(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = 1.8.dp.toPx()
        drawLine(tint, Offset(s * 0.2f, s * 0.2f), Offset(s * 0.8f, s * 0.8f), strokeWidth = w, cap = StrokeCap.Round)
        drawLine(tint, Offset(s * 0.8f, s * 0.2f), Offset(s * 0.2f, s * 0.8f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

@Composable
fun FolderGlyph(tint: Color, size: Dp = 24.dp, filled: Boolean = true) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val path = Path().apply {
            moveTo(s * 0.08f, s * 0.26f)
            lineTo(s * 0.38f, s * 0.26f)
            lineTo(s * 0.48f, s * 0.38f)
            lineTo(s * 0.92f, s * 0.38f)
            lineTo(s * 0.92f, s * 0.80f)
            lineTo(s * 0.08f, s * 0.80f)
            close()
        }
        drawPath(path, tint, style = if (filled) Fill else Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round))
    }
}

@Composable
fun FileGlyph(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = 1.6.dp.toPx(), join = StrokeJoin.Round, cap = StrokeCap.Round)
        val path = Path().apply {
            moveTo(s * 0.22f, s * 0.10f)
            lineTo(s * 0.62f, s * 0.10f)
            lineTo(s * 0.80f, s * 0.28f)
            lineTo(s * 0.80f, s * 0.90f)
            lineTo(s * 0.22f, s * 0.90f)
            close()
        }
        drawPath(path, tint, style = stroke)
        drawLine(tint, Offset(s * 0.62f, s * 0.10f), Offset(s * 0.62f, s * 0.28f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
        drawLine(tint, Offset(s * 0.62f, s * 0.28f), Offset(s * 0.80f, s * 0.28f), strokeWidth = 1.6.dp.toPx(), cap = StrokeCap.Round)
    }
}

/** Yan yana (bölünmüş ekran) simgesi: ortası bölünmüş çerçeve. */
@Composable
fun SplitGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = 1.7.dp.toPx()
        drawRoundRect(tint, Offset(s * 0.1f, s * 0.2f), Size(s * 0.8f, s * 0.6f), CornerRadius(s * 0.08f), style = Stroke(width = w))
        drawLine(tint, Offset(s * 0.5f, s * 0.2f), Offset(s * 0.5f, s * 0.8f), strokeWidth = w)
    }
}

/** Tam ekran simgesi: dört köşe işareti. */
@Composable
fun FullscreenGlyph(tint: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = 1.8.dp.toPx()
        val a = 0.16f
        val b = 0.38f
        fun corner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(tint, Offset(s * x, s * y), Offset(s * (x + dx), s * y), strokeWidth = w, cap = StrokeCap.Round)
            drawLine(tint, Offset(s * x, s * y), Offset(s * x, s * (y + dy)), strokeWidth = w, cap = StrokeCap.Round)
        }
        corner(a, a, b - a, b - a)
        corner(1f - a, a, -(b - a), b - a)
        corner(a, 1f - a, b - a, -(b - a))
        corner(1f - a, 1f - a, -(b - a), -(b - a))
    }
}

/** Masaüstü (monitör) simgesi. */
@Composable
fun MonitorGlyph(tint: Color, size: Dp = 24.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = 1.7.dp.toPx()
        drawRoundRect(tint, Offset(s * 0.1f, s * 0.15f), Size(s * 0.8f, s * 0.52f), CornerRadius(s * 0.07f), style = Stroke(width = w))
        drawLine(tint, Offset(s * 0.5f, s * 0.67f), Offset(s * 0.5f, s * 0.82f), strokeWidth = w)
        drawLine(tint, Offset(s * 0.3f, s * 0.84f), Offset(s * 0.7f, s * 0.84f), strokeWidth = w, cap = StrokeCap.Round)
    }
}

/** Pencere köşesi tutamacı: üç çapraz çizgi. */
@Composable
fun GripGlyph(tint: Color, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = 1.6.dp.toPx()
        for (f in listOf(0.4f, 0.65f, 0.9f)) {
            drawLine(tint, Offset(s * f, s), Offset(s, s * f), strokeWidth = w, cap = StrokeCap.Round)
        }
    }
}

/** Raptiye simgesi: [filled] true ise dolu (sabitlenmiş), değilse çizgili. */
@Composable
fun PinGlyph(tint: Color, filled: Boolean, size: Dp = 16.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val w = 1.6.dp.toPx()
        val head = Path().apply {
            moveTo(s * 0.34f, s * 0.12f)
            lineTo(s * 0.66f, s * 0.12f)
            lineTo(s * 0.62f, s * 0.50f)
            lineTo(s * 0.78f, s * 0.62f)
            lineTo(s * 0.22f, s * 0.62f)
            lineTo(s * 0.38f, s * 0.50f)
            close()
        }
        drawPath(head, tint, style = if (filled) Fill else Stroke(width = w, join = StrokeJoin.Round))
        drawLine(tint, Offset(s * 0.5f, s * 0.62f), Offset(s * 0.5f, s * 0.92f), strokeWidth = w, cap = StrokeCap.Round)
    }
}
