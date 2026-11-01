package com.trippin.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.icons.TripIcons
import com.trippin.ai.ui.theme.Trip

/** Big screen headline in sentence case, set in the display serif. */
@Composable
fun Headline(text: String, modifier: Modifier = Modifier, color: Color = Trip.Ink, accentLastChar: Boolean = false) {
    Text(text, style = MaterialTheme.typography.displayLarge, color = color, modifier = modifier)
}

/** Small section label above a heading or a block. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Trip.Muted) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/**
 * The main call to action: full width, 56dp, brand gradient. Pass a different [container]
 * to get a solid colour instead (for example on top of a coloured card).
 */
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
    val shape = RoundedCornerShape(999.dp)
    val useBrand = container == Trip.Ink
    val fg = if (useBrand) Color.White else content
    val active = enabled && !loading
    val alpha = if (active) 1f else 0.45f
    Box(
        modifier.fillMaxWidth().height(56.dp).clip(shape)
            .then(if (useBrand) Modifier.background(Trip.Brand, alpha = alpha) else Modifier.background(container.copy(alpha = alpha)))
            .then(if (useBrand) Modifier.border(1.dp, Trip.Gilt, shape) else Modifier)
            .clickable(enabled = active, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), color = fg, strokeWidth = 3.dp)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.labelLarge, color = fg)
                Spacer(Modifier.width(8.dp))
                Icon(TripIcons.Forward, contentDescription = null, tint = if (useBrand) Trip.Signal else fg, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, Trip.Ink.copy(alpha = 0.22f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Trip.Ink),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun Pill(text: String, background: Color, foreground: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Colour-coded risk from the Bayesian network. Colour AND words, never colour alone. */
@Composable
fun RiskPill(level: String, probability: Double, modifier: Modifier = Modifier) {
    val bg = when (level) {
        "Low" -> Trip.Mint
        "Medium" -> Trip.Amber
        else -> Trip.Danger
    }
    Pill("$level risk, ${(probability * 100).toInt()}%", bg, Color.White, modifier)
}

@Composable
fun NumberBadge(n: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.size(32.dp).clip(CircleShape).background(Trip.Brand),
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = MaterialTheme.typography.labelMedium, color = Color.White) }
}

@Composable
fun Stepper(value: Int, onChange: (Int) -> Unit, range: IntRange, label: String, modifier: Modifier = Modifier) {
    val hair = BorderStroke(1.dp, Trip.Ink.copy(alpha = 0.2f))
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Trip.Card)
            .border(hair, RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        OutlinedButton(
            onClick = { if (value > range.first) onChange(value - 1) },
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp),
            border = hair,
        ) { Text("-", style = MaterialTheme.typography.titleLarge, color = Trip.Ink) }
        Text("$value $label", style = MaterialTheme.typography.titleLarge)
        OutlinedButton(
            onClick = { if (value < range.last) onChange(value + 1) },
            shape = CircleShape,
            modifier = Modifier.size(44.dp),
            contentPadding = PaddingValues(0.dp),
            border = hair,
        ) { Text("+", style = MaterialTheme.typography.titleLarge, color = Trip.Ink) }
    }
}
