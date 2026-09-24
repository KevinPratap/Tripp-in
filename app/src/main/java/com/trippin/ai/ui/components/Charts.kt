package com.trippin.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.theme.Trip
import kotlin.math.max

/** Two lines over generations: best route cost (thick ink) and population average (signal). */
@Composable
fun EvolutionChart(best: List<Double>, average: List<Double>, modifier: Modifier = Modifier) {
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            if (best.size < 2) return@Canvas
            val top = max(best.max(), average.maxOrNull() ?: 0.0)
            val bottom = best.min() * 0.95
            fun point(i: Int, v: Double, n: Int) = Offset(
                x = size.width * i / (n - 1).toFloat(),
                y = size.height * (1f - ((v - bottom) / (top - bottom).coerceAtLeast(1e-9)).toFloat()),
            )
            fun line(values: List<Double>, color: Color, width: Float) {
                val path = Path()
                values.forEachIndexed { i, v -> val p = point(i, v, values.size); if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
            }
            for (g in 0..4) {
                val y = size.height * g / 4f
                drawLine(Trip.Ink.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
            }
            if (average.size >= 2) line(average, Trip.Signal, 4f)
            line(best, Trip.Ink, 8f)
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(Trip.Ink, "Best route")
            Legend(Trip.Signal, "Population average")
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Trip.Muted)
    }
}

/** A labelled probability bar, 0–100%. */
@Composable
fun ProbabilityBar(label: String, p: Double, color: Color = Trip.Ink, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text("${"%.1f".format(p * 100)}%", style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Trip.PaperDeep)) {
            Box(Modifier.fillMaxWidth(p.toFloat().coerceIn(0f, 1f)).height(14.dp).clip(RoundedCornerShape(7.dp)).background(color))
        }
    }
}

/**
 * Fuzzy output: each term's membership curve (thin), the clipped-and-aggregated shape (filled),
 * and the centroid as a vertical signal line.
 */
@Composable
fun FuzzyChart(
    terms: List<List<Pair<Double, Double>>>,
    aggregated: List<Pair<Double, Double>>,
    centroid: Double,
    range: ClosedFloatingPointRange<Double>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.fillMaxWidth().height(170.dp)) {
        val span = range.endInclusive - range.start
        fun x(v: Double) = (size.width * ((v - range.start) / span)).toFloat()
        fun y(mu: Double) = (size.height * (1 - mu)).toFloat()
        if (aggregated.isNotEmpty()) {
            val fill = Path().apply {
                moveTo(x(aggregated.first().first), size.height)
                aggregated.forEach { (v, mu) -> lineTo(x(v), y(mu)) }
                lineTo(x(aggregated.last().first), size.height)
                close()
            }
            drawPath(fill, Trip.Signal.copy(alpha = 0.55f))
        }
        terms.forEach { curve ->
            val p = Path()
            curve.forEachIndexed { i, (v, mu) -> if (i == 0) p.moveTo(x(v), y(mu)) else p.lineTo(x(v), y(mu)) }
            drawPath(p, Trip.Ink, style = Stroke(width = 4f))
        }
        drawLine(Trip.Cobalt, Offset(x(centroid), 0f), Offset(x(centroid), size.height), strokeWidth = 6f)
    }
}

/** Q-table heatmap: rows = states, columns = categories; colour runs cobalt (bad) → paper → signal (good). */
@Composable
fun QHeatmap(table: List<List<Double>>, rowLabels: List<String>, colLabels: List<String>, modifier: Modifier = Modifier) {
    val maxAbs = table.flatten().maxOfOrNull { kotlin.math.abs(it) }?.takeIf { it > 0 } ?: 1.0
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(Modifier.width(64.dp))
            colLabels.forEach { Text(it, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), maxLines = 1) }
        }
        table.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(rowLabels.getOrElse(r) { "$r" }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp), maxLines = 1)
                row.forEach { v ->
                    val t = (v / maxAbs).toFloat()
                    val c = if (t >= 0) lerp(Trip.Card, Trip.Signal, t) else lerp(Trip.Card, Trip.Cobalt, -t)
                    Box(Modifier.weight(1f).aspectRatio(1.4f).clip(RoundedCornerShape(4.dp)).background(c))
                }
            }
        }
    }
}
