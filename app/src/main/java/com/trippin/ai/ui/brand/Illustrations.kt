package com.trippin.ai.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.theme.Trip

enum class Art { ROUTE, CARDS, SPLIT, SUITCASE, COMPASS }

/**
 * Line illustrations for onboarding and empty states. Same language as the logo: ink hairlines,
 * one gold accent, soft champagne fills. Drawn on a 200 unit square, so they scale cleanly.
 */
@Composable
fun Illustration(art: Art, modifier: Modifier = Modifier, size: Dp = 180.dp) {
    val ink = Trip.Ink
    val line = Trip.Muted
    Canvas(modifier.size(size)) {
        val k = this.size.width / 200f
        val st = Stroke(2.6f * k, cap = StrokeCap.Round)
        when (art) {
            Art.ROUTE -> route(k, ink, line, st)
            Art.CARDS -> cards(k, ink, st)
            Art.SPLIT -> split(k, ink, st)
            Art.SUITCASE -> suitcase(k, ink, st)
            Art.COMPASS -> compass(k, ink, line, st)
        }
    }
}

private fun DrawScope.dot(x: Float, y: Float, r: Float, k: Float, gold: Boolean, ink: Color) {
    if (gold) drawCircle(Trip.Gilt, r * k, Offset(x * k, y * k))
    else {
        drawCircle(Trip.Paper, r * k, Offset(x * k, y * k))
        drawCircle(ink, r * k, Offset(x * k, y * k), style = Stroke(2.6f * k))
    }
}

private fun DrawScope.route(k: Float, ink: Color, line: Color, st: Stroke) {
    drawCircle(Trip.Champagne.copy(alpha = 0.35f), 78f * k, Offset(100f * k, 100f * k))
    val p = Path().apply {
        moveTo(34f * k, 148f * k)
        cubicTo(70f * k, 150f * k, 60f * k, 96f * k, 100f * k, 98f * k)
        cubicTo(140f * k, 100f * k, 128f * k, 52f * k, 164f * k, 50f * k)
    }
    drawPath(p, line, style = Stroke(2.6f * k, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * k, 8f * k))))
    dot(34f, 148f, 7f, k, false, ink); dot(100f, 98f, 7f, k, false, ink); dot(164f, 50f, 9f, k, true, ink)
}

private fun DrawScope.cards(k: Float, ink: Color, st: Stroke) {
    fun card(angle: Float, dx: Float, fill: Color, front: Boolean) {
        rotate(angle, Offset(100f * k, 108f * k)) {
            val tl = Offset((58f + dx) * k, 46f * k)
            drawRoundRect(fill, tl, Size(84f * k, 118f * k), CornerRadius(12f * k))
            drawRoundRect(ink, tl, Size(84f * k, 118f * k), CornerRadius(12f * k), style = st)
            if (front) {
                drawCircle(Trip.Gilt, 11f * k, Offset((100f + dx) * k, 82f * k))
                val w = Path().apply {
                    moveTo((70f + dx) * k, 128f * k)
                    cubicTo((84f + dx) * k, 112f * k, (96f + dx) * k, 140f * k, (112f + dx) * k, 122f * k)
                    cubicTo((120f + dx) * k, 114f * k, (126f + dx) * k, 122f * k, (130f + dx) * k, 126f * k)
                }
                drawPath(w, ink, style = st)
            }
        }
    }
    card(-12f, -10f, Trip.PaperDeep, false)
    card(9f, 12f, Trip.Champagne.copy(alpha = 0.45f), false)
    card(0f, 0f, Trip.Card, true)
}

private fun DrawScope.split(k: Float, ink: Color, st: Stroke) {
    drawCircle(Trip.Champagne.copy(alpha = 0.55f), 46f * k, Offset(100f * k, 100f * k))
    drawCircle(ink, 46f * k, Offset(76f * k, 100f * k), style = st)
    drawCircle(ink, 46f * k, Offset(124f * k, 100f * k), style = st)
    dot(100f, 100f, 6f, k, true, ink)
    dot(46f, 56f, 3.5f, k, false, ink); dot(158f, 150f, 3.5f, k, false, ink)
}

private fun DrawScope.suitcase(k: Float, ink: Color, st: Stroke) {
    drawCircle(Trip.Champagne.copy(alpha = 0.35f), 78f * k, Offset(100f * k, 104f * k))
    drawRoundRect(Trip.Card, Offset(46f * k, 66f * k), Size(108f * k, 82f * k), CornerRadius(12f * k))
    drawRoundRect(ink, Offset(46f * k, 66f * k), Size(108f * k, 82f * k), CornerRadius(12f * k), style = st)
    val h = Path().apply {
        moveTo(80f * k, 66f * k); lineTo(80f * k, 54f * k); quadraticTo(80f * k, 48f * k, 86f * k, 48f * k)
        lineTo(114f * k, 48f * k); quadraticTo(120f * k, 48f * k, 120f * k, 54f * k); lineTo(120f * k, 66f * k)
    }
    drawPath(h, ink, style = st)
    drawLine(ink, Offset(46f * k, 104f * k), Offset(154f * k, 104f * k), st.width)
    drawRoundRect(Trip.Gilt, Offset(92f * k, 96f * k), Size(16f * k, 16f * k), CornerRadius(4f * k))
}

private fun DrawScope.compass(k: Float, ink: Color, line: Color, st: Stroke) {
    drawCircle(Trip.Champagne.copy(alpha = 0.35f), 78f * k, Offset(100f * k, 100f * k))
    drawCircle(ink, 56f * k, Offset(100f * k, 100f * k), style = st)
    for (i in 0 until 12) {
        rotate(i * 30f, Offset(100f * k, 100f * k)) {
            drawLine(line, Offset(100f * k, 48f * k), Offset(100f * k, (if (i % 3 == 0) 58f else 53f) * k), 2f * k)
        }
    }
    rotate(35f, Offset(100f * k, 100f * k)) {
        val n = Path().apply { moveTo(100f * k, 62f * k); lineTo(110f * k, 100f * k); lineTo(90f * k, 100f * k); close() }
        drawPath(n, Trip.Gilt)
        val s = Path().apply { moveTo(100f * k, 138f * k); lineTo(110f * k, 100f * k); lineTo(90f * k, 100f * k); close() }
        drawPath(s, ink)
    }
    drawCircle(Trip.Paper, 4f * k, Offset(100f * k, 100f * k))
}
