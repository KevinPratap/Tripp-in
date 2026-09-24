package com.trippin.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.theme.Trip

/** Big poster headline. Uppercases for the bold look. */
@Composable
fun Headline(text: String, modifier: Modifier = Modifier, color: Color = Trip.Ink, accentLastChar: Boolean = false) {
    if (accentLastChar && text.isNotEmpty()) {
        Row(modifier) {
            Text(text.dropLast(1).uppercase(), style = MaterialTheme.typography.displayLarge, color = color)
            Text(text.takeLast(1), style = MaterialTheme.typography.displayLarge, color = Trip.Signal)
        }
    } else {
        Text(text.uppercase(), style = MaterialTheme.typography.displayLarge, color = color, modifier = modifier)
    }
}

@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Trip.Muted) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** The main call to action: full width, 60dp, ink with a signal arrow. */
@Composable
fun SignalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    container: Color = Trip.Ink,
    content: Color = Trip.Paper,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.7f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = content, strokeWidth = 3.dp)
        } else {
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Trip.Signal)
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(2.dp, Trip.Ink),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Trip.Ink),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun Pill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Colour-coded risk from the Bayesian network. Colour AND words, never colour alone. */
@Composable
fun RiskPill(level: String, probability: Double, modifier: Modifier = Modifier) {
    val (bg, fg) = when (level) {
        "Low" -> Trip.Mint to Color.White
        "Medium" -> Trip.Amber to Color.White
        else -> Color(0xFFB3261E) to Color.White
    }
    Pill("${level.uppercase()} RISK · ${(probability * 100).toInt()}%", bg, fg, modifier)
}

@Composable
fun NumberBadge(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(34.dp).clip(CircleShape).background(Trip.Signal),
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = MaterialTheme.typography.labelMedium, color = Trip.Ink) }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .border(2.dp, Trip.Ink, RoundedCornerShape(18.dp))
            .background(Trip.Card)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = { if (value > range.first) onChange(value - 1) },
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            border = BorderStroke(2.dp, Trip.Ink),
        ) { Text("−", style = MaterialTheme.typography.titleLarge, color = Trip.Ink) }
        Text("$value $label", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
            onClick = { if (value < range.last) onChange(value + 1) },
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            border = BorderStroke(2.dp, Trip.Ink),
        ) { Text("+", style = MaterialTheme.typography.titleLarge, color = Trip.Ink) }
    }
}
