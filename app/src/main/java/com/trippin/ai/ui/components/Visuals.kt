package com.trippin.ai.ui.components

import androidx.compose.foundation.Canvas
import com.trippin.ai.ui.icons.TripIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.model.Category

/** Gradient pairs used for cards that have no photo. Chosen by a stable hash so a card never changes colour. */
private val gradientPairs = listOf(
    Color(0xFF3A4A68) to Color(0xFF1D2433),
    Color(0xFF2F5A4B) to Color(0xFF16302A),
    Color(0xFF7A5A3A) to Color(0xFF3B2A1A),
    Color(0xFF6B3A48) to Color(0xFF331B24),
    Color(0xFF4B4468) to Color(0xFF211D33),
    Color(0xFF2F5560) to Color(0xFF142A31),
)

private fun pairFor(seed: String): Pair<Color, Color> = gradientPairs[(seed.hashCode() and 0x7fffffff) % gradientPairs.size]

/** Deterministic gradient for a trip or an idea, so cards without a real photo still look finished. */
fun seedBrush(seed: String): Brush {
    val (a, b) = pairFor(seed)
    return Brush.linearGradient(listOf(a, b))
}

fun seedColor(seed: String): Color = pairFor(seed).first

fun categoryIcon(c: Category?): ImageVector = when (c) {
    Category.MUSEUM -> TripIcons.Museum
    Category.FOOD -> TripIcons.Restaurant
    Category.PARK -> TripIcons.Park
    Category.LANDMARK -> TripIcons.Place
    Category.SHOPPING -> TripIcons.ShoppingBag
    Category.NIGHTLIFE -> TripIcons.Nightlife
    null -> TripIcons.Explore
}

fun categoryLabel(c: Category?): String = when (c) {
    Category.MUSEUM -> "Museum"
    Category.FOOD -> "Food"
    Category.PARK -> "Park"
    Category.LANDMARK -> "Landmark"
    Category.SHOPPING -> "Shopping"
    Category.NIGHTLIFE -> "Nightlife"
    null -> "Destination"
}

/**
 * Story-style circle: a gradient ring around a coloured disc with initials, and a label underneath.
 * [active] draws the brand gradient ring; otherwise the ring is a quiet hairline.
 */
@Composable
fun StoryRing(label: String, seed: String, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 68.dp) {
    Column(modifier.width(size + 8.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(size), contentAlignment = Alignment.Center) {
            val line = Trip.Line
            Canvas(Modifier.size(size)) {
                val stroke = 3.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
                if (active) {
                    drawArc(brush = Brush.sweepGradient(listOf(Color(0xFFEBD9AE), Color(0xFFB08848), Color(0xFFD9B878), Color(0xFFEBD9AE))), startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                } else {
                    drawArc(color = line, startAngle = 0f, sweepAngle = 360f, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(stroke))
                }
            }
            Box(Modifier.size(size - 12.dp).clip(CircleShape).background(seedBrush(seed)), contentAlignment = Alignment.Center) {
                Text(
                    label.trim().take(1).uppercase().ifEmpty { "?" },
                    style = MaterialTheme.typography.titleLarge, color = Color.White,
                )
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = Trip.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}
