package com.trippin.feature.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.network.GeoPointDto
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

data class ProjectedPoint(val x: Float, val y: Float)

/**
 * Places real coordinates in a box, north up. Longitude is scaled by the cosine of the middle
 * latitude so a square block of streets stays square, and the whole route is scaled evenly and
 * centred, so distances between pins keep their true proportions.
 */
fun projectRoute(points: List<GeoPointDto>, width: Float, height: Float, inset: Float): List<ProjectedPoint> {
    if (points.isEmpty()) return emptyList()
    val meanLat = points.map { it.latitude }.average()
    val k = cos(Math.toRadians(meanLat))
    val xs = points.map { it.longitude * k }
    val ys = points.map { it.latitude }
    val minX = xs.min(); val maxX = xs.max()
    val minY = ys.min(); val maxY = ys.max()
    val spanX = maxX - minX
    val spanY = maxY - minY
    val usableW = width - 2 * inset
    val usableH = height - 2 * inset
    if (spanX <= 0.0 && spanY <= 0.0) return points.map { ProjectedPoint(width / 2, height / 2) }
    val scale = min(
        if (spanX > 0) usableW / spanX else Double.MAX_VALUE,
        if (spanY > 0) usableH / spanY else Double.MAX_VALUE
    )
    val offsetX = (width - spanX * scale) / 2
    val offsetY = (height - spanY * scale) / 2
    return points.indices.map { i ->
        ProjectedPoint(
            x = (offsetX + (xs[i] - minX) * scale).toFloat(),
            y = (offsetY + (maxY - ys[i]) * scale).toFloat()
        )
    }
}

/** Great-circle distance in kilometres. */
fun distanceKm(a: GeoPointDto, b: GeoPointDto): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(a.latitude)) * cos(Math.toRadians(b.latitude)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * r * asin(sqrt(h))
}

/** How far across the day's stops spread, corner to corner of the area they cover. */
fun spreadKm(points: List<GeoPointDto>): Double? {
    if (points.size < 2) return null
    val sw = GeoPointDto(points.minOf { it.latitude }, points.minOf { it.longitude })
    val ne = GeoPointDto(points.maxOf { it.latitude }, points.maxOf { it.longitude })
    return distanceKm(sw, ne)
}

fun spreadLabel(km: Double): String = when {
    km < 1.0 -> "About ${max(50, (km * 1000 / 50).toInt() * 50)} m across"
    else -> String.format(Locale.US, "About %.1f km across", km)
}

/**
 * The day's stops drawn where they really are relative to each other, numbered in visiting order and
 * joined in that order. The joining lines are straight, and the caption says so: real walking routes
 * follow streets, which is what the Maps button is for.
 */
@Composable
fun RouteSketch(points: List<GeoPointDto>, modifier: Modifier = Modifier, labels: List<String> = points.indices.map { "${it + 1}" }) {
    val colors = TrippinTheme.colors
    val measurer = rememberTextMeasurer()
    val numberStyle = TextStyle(fontSize = 12.sp, color = colors.onAccent, fontFamily = TrippinType.Label.fontFamily, fontWeight = TrippinType.Label.fontWeight)
    val spread = spreadKm(points)
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(240.dp)
                .clip(TrippinTheme.shapes.card)
                .background(colors.panelAlt)
                .semantics {
                    contentDescription = "Sketch of ${points.size} stops in visiting order" + (spread?.let { ". ${spreadLabel(it)}" } ?: "")
                }
        ) {
            val step = 32.dp.toPx()
            var gx = step
            while (gx < size.width) {
                drawLine(colors.hairline, Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += step
            }
            var gy = step
            while (gy < size.height) {
                drawLine(colors.hairline, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += step
            }

            val projected = projectRoute(points, size.width, size.height, inset = 28.dp.toPx())
            for (i in 0 until projected.size - 1) {
                val a = projected[i]; val b = projected[i + 1]
                drawLine(
                    color = colors.ink,
                    start = Offset(a.x, a.y),
                    end = Offset(b.x, b.y),
                    strokeWidth = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                )
            }
            val r = 12.dp.toPx()
            projected.forEachIndexed { i, p ->
                drawCircle(colors.paper, radius = r + 2.dp.toPx(), center = Offset(p.x, p.y))
                drawCircle(colors.accent, radius = r, center = Offset(p.x, p.y))
                drawCircle(colors.ink, radius = r, center = Offset(p.x, p.y), style = Stroke(width = 1.5.dp.toPx()))
                val label = measurer.measure(labels.getOrElse(i) { "${i + 1}" }, numberStyle)
                drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y - label.size.height / 2f))
            }
        }
        Text(
            listOfNotNull(spread?.let(::spreadLabel), "Straight lines join the stops in order; real routes follow the streets.").joinToString(". "),
            style = TrippinType.Caption,
            color = colors.inkMuted,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp)
        )
    }
}
