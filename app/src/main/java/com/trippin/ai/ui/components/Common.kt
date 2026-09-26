package com.trippin.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.trippin.ai.ui.theme.Trip
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Screen header: back arrow, title, optional actions. Handles the status bar inset. */
@Composable
fun TopBar(title: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, background: Color = Trip.Paper, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().background(background).statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Trip.Ink) }
        } else Spacer(Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        actions()
    }
}

/** The standard bordered card. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    background: Color = Trip.Card,
    onClick: (() -> Unit)? = null,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(background).border(2.dp, Trip.Ink, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Kicker(text, color = Trip.Ink, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = Trip.Muted, textAlign = TextAlign.Center)
        action?.let { Spacer(Modifier.height(4.dp)); it() }
    }
}

fun avatarColor(uid: String): Color = Trip.AvatarColors[(uid.hashCode() and 0x7fffffff) % Trip.AvatarColors.size]

@Composable
fun Avatar(name: String, uid: String, size: Dp = 36.dp, modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(CircleShape).background(avatarColor(uid)).border(2.dp, Trip.Paper, CircleShape)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().split(' ').filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" },
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelMedium,
            color = Color.White, fontWeight = FontWeight.Bold,
        )
    }
}

/** Overlapping avatars "👤👤👤 +2", so a shared trip is recognisable at a glance. */
@Composable
fun AvatarStack(people: List<Pair<String, String>>, max: Int = 4, size: Dp = 32.dp, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "${people.size} travellers" }, verticalAlignment = Alignment.CenterVertically) {
        people.take(max).forEachIndexed { i, (uid, name) ->
            Avatar(name, uid, size, Modifier.offset(x = (-10 * i).dp))
        }
        if (people.size > max) {
            Text("+${people.size - max}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.offset(x = (-10 * max + 14).dp))
        }
    }
}

/** A horizontal bar showing a share of the group, with a label. */
@Composable
fun ShareBar(fraction: Float, color: Color = Trip.Signal, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { fraction.coerceIn(0f, 1f) },
        color = color, trackColor = Trip.PaperDeep, strokeCap = StrokeCap.Round,
        modifier = modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
    )
}

/** Single-choice segmented control with real radio semantics. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { o ->
            val on = o == selected
            Text(
                label(o),
                style = MaterialTheme.typography.titleMedium,
                color = if (on) Trip.Paper else Trip.Ink,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                    .background(if (on) Trip.Ink else Trip.Card)
                    .border(2.dp, Trip.Ink, RoundedCornerShape(14.dp))
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(o) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 12.dp, horizontal = 4.dp),
            )
        }
    }
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = false, enabled: Boolean = true, color: Color = Trip.Ink) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(2.dp, if (enabled) color else color.copy(alpha = 0.3f)),
        colors = if (filled) ButtonDefaults.outlinedButtonColors(containerColor = color, contentColor = Trip.Paper)
        else ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1) }
}

@Composable
fun TextInput(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onDone: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    visual: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    error: String? = null,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        keyboardOptions = if (onDone != null) keyboardOptions.copy(imeAction = ImeAction.Done) else keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }, onSearch = { onDone?.invoke() }),
        trailingIcon = trailing,
        visualTransformation = visual,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Trip.Ink, unfocusedBorderColor = Trip.Ink,
            focusedContainerColor = Trip.Card, unfocusedContainerColor = Trip.Card,
            focusedLabelColor = Trip.Ink, cursorColor = Trip.Ink,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

// ---------------- Formatting ----------------

private val shortDate = DateTimeFormatter.ofPattern("d MMM")
private val dayDate = DateTimeFormatter.ofPattern("EEE d MMM")

fun dateRange(start: LocalDate, days: Int): String {
    val end = start.plusDays((days - 1).toLong())
    return if (days == 1) start.format(dayDate) else "${start.format(shortDate)} – ${end.format(shortDate)}"
}

fun dayLabel(d: LocalDate): String = d.format(dayDate)

fun clock(minute: Int): String {
    val m = ((minute % (24 * 60)) + 24 * 60) % (24 * 60)
    return "%02d:%02d".format(m / 60, m % 60)
}

fun ago(millis: Long, now: Long = System.currentTimeMillis()): String {
    if (millis <= 0) return "just now"
    val d = Duration.ofMillis(now - millis)
    return when {
        d.toMinutes() < 1 -> "just now"
        d.toMinutes() < 60 -> "${d.toMinutes()} min ago"
        d.toHours() < 24 -> "${d.toHours()} h ago"
        d.toDays() < 7 -> "${d.toDays()} d ago"
        else -> Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(shortDate)
    }
}

fun countdown(untilMillis: Long, now: Long = System.currentTimeMillis()): String {
    val d = Duration.ofMillis(untilMillis - now)
    return when {
        d.isNegative -> "closed"
        d.toHours() < 1 -> "closes in ${d.toMinutes()} min"
        d.toHours() < 48 -> "closes in ${d.toHours()} h"
        else -> "closes in ${d.toDays()} days"
    }
}

fun daysUntil(start: LocalDate, today: LocalDate = LocalDate.now()): String {
    val n = java.time.temporal.ChronoUnit.DAYS.between(today, start)
    return when {
        n == 0L -> "Starts today"
        n == 1L -> "Tomorrow"
        n > 1 -> "In $n days"
        else -> ""
    }
}
