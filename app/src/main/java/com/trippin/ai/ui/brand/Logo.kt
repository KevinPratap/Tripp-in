package com.trippin.ai.ui.brand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.ai.ui.theme.Display
import com.trippin.ai.ui.theme.Trip

/**
 * The Trippin mark: a T whose crossbar is a flight path, ending in a gold dot (the destination).
 * Drawn on a 108 unit grid, the same grid as the launcher icon, so the two always match.
 * [progress] (0 to 1) draws it in: the arc first, then the stem, then the dot.
 */
@Composable
fun TrippinMark(size: Dp, modifier: Modifier = Modifier, ink: Color = Trip.Ink, progress: Float = 1f) {
    Canvas(modifier.size(size)) {
        val k = this.size.width / 108f
        val w = 6.5f * k
        fun seg(path: Path, from: Float, to: Float): Path {
            val m = PathMeasure().apply { setPath(path, false) }
            val out = Path()
            val len = m.length
            m.getSegment(len * from, len * to, out, true)
            return out
        }
        val arc = Path().apply {
            moveTo(24f * k, 40f * k)
            cubicTo(44f * k, 26f * k, 66f * k, 26f * k, 84f * k, 40f * k)
        }
        val stem = Path().apply { moveTo(54f * k, 29.5f * k); lineTo(54f * k, 82f * k) }
        val a = (progress / 0.5f).coerceIn(0f, 1f)
        val s = ((progress - 0.4f) / 0.4f).coerceIn(0f, 1f)
        val d = ((progress - 0.8f) / 0.2f).coerceIn(0f, 1f)
        if (a > 0f) drawPath(seg(arc, 0f, a), ink, style = Stroke(w, cap = StrokeCap.Round))
        if (s > 0f) drawPath(seg(stem, 0f, s), ink, style = Stroke(w, cap = StrokeCap.Round))
        if (d > 0f) drawCircle(Trip.Gilt, radius = 6.5f * k * d, center = Offset(84f * k, 40f * k))
    }
}

/** Mark plus the name, set in the display italic. */
@Composable
fun TrippinLockup(markSize: Dp = 36.dp, modifier: Modifier = Modifier, ink: Color = Trip.Ink) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        TrippinMark(markSize, ink = ink)
        Text(
            "Trippin",
            style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = (markSize.value * 0.9f).sp),
            color = ink,
        )
    }
}
