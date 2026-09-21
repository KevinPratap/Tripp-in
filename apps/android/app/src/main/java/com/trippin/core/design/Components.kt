package com.trippin.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong


/**
 * The house components.
 *
 * Flat surfaces with a 2dp solid ink border and no shadow anywhere. Kevin chose the flat direction
 * on 2026-09-20 over the offset ink block: an edge is what defines a card or a button now, and the
 * weight comes from the type scale and from space instead. Never re-add a shadow here because a
 * screen looks empty. A flat screen needs more hierarchy, not a shadow.
 */
@Composable
fun TrippinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(modifier = modifier.fillMaxWidth().height(52.dp)) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, Ink),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentCrimson,
                contentColor = OnCrimson,
                disabledContainerColor = AccentCrimson.copy(alpha = 0.4f),
                disabledContentColor = OnCrimson.copy(alpha = 0.8f)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = text.uppercase(),
                style = TrippinType.Label,
                color = OnCrimson
            )
        }
    }
}

/**
 * The house chip: one choice a person makes, drawn the same way wherever it is asked for.
 *
 * Crimson when it is the selected one, panel and ink when it is not, on the same 2dp ink edge as
 * every other control, so a chip and a button read as one family. It is a selection and never an
 * action, and it is never the only place a value is stated: it always sits under its own label.
 * TripPlannerScreen carried its own private copy of this until 2026-09-21, which is two places the
 * same control could drift apart in.
 */
@Composable
fun TrippinChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .border(2.dp, Ink, RoundedCornerShape(4.dp))
            .background(if (selected) AccentCrimson else Panel, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = TrippinType.Caption,
            color = if (selected) OnCrimson else Ink
        )
    }
}

@Composable
fun TrippinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(2.dp, Ink),
            colors = CardDefaults.cardColors(containerColor = Panel),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
    }
}

/**
 * Editorial segmented control with hard ink border and tactile high-contrast active pill.
 */
@Composable
fun TrippinSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Panel, RoundedCornerShape(8.dp))
            .border(2.dp, Ink, RoundedCornerShape(8.dp))
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val pillBackground = if (isSelected) Ink else Panel
                val pillTextColor = if (isSelected) Paper else Ink

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(pillBackground, RoundedCornerShape(6.dp))
                        .clickable { onOptionSelected(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option,
                        color = pillTextColor,
                        style = TrippinType.Label
                    )
                }
            }
        }
    }
}

/**
 * High-contrast status badge reflecting deterministic verification reality.
 *
 * The colour is the meaning from design system section 1 and not a decoration: a confirmed state is
 * the ink green that says confirmed, and anything else is neutral, which says neither good nor bad.
 * Crimson is never used here, because crimson is what you tap and it never signals success.
 */
@Composable
fun TrippinStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val isVerified = status.equals("VERIFIED", ignoreCase = true) || status.equals("READY", ignoreCase = true)
    val badgeColor = if (isVerified) GoodInkSurface else NeutralInkSurface
    val textColor = if (isVerified) GoodInk else NeutralInk

    Box(
        modifier = modifier
            .background(badgeColor, RoundedCornerShape(4.dp))
            .border(1.5.dp, Ink, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = textColor,
            style = TrippinType.Caption
        )
    }
}

/*
 * Motion. One easing for everything that settles, so the app reads as one thing instead of a set of
 * effects. Nothing here bounces, glows or floats, and nothing animates a figure that has not been
 * worked out yet: motion only ever reports something that actually happened.
 */
val TrippinSettle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * A screen arriving: a short rise and fade. Small on purpose, it should read as "you are here".
 *
 * [delayMillis] is the 60ms per card stagger of design system section 4, so a list assembles instead
 * of appearing in one block.
 *
 * The "arrived" flag is saveable rather than plain remembered on purpose: inside a lazy list, a card
 * that scrolls out of the viewport is thrown away and composed again on the way back, and with a
 * plain remember it would replay its own arrival every time the user scrolled up. Motion reports
 * something that happened, and arriving at a card happens once.
 */
@Composable
fun ArriveOnEnter(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var arrived by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        arrived = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (arrived) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = TrippinSettle),
        label = "arriveAlpha"
    )
    val rise by animateDpAsState(
        targetValue = if (arrived) 0.dp else 12.dp,
        animationSpec = tween(durationMillis = 280, easing = TrippinSettle),
        label = "arriveRise"
    )
    Box(modifier = modifier.alpha(alpha).offset(y = rise)) { content() }
}

/**
 * The trip stamp. Flat, one accent, and built from the trip's own facts (where and when) rather than
 * a logo, so it can never claim something the plan did not do. It lands once, with the same settle
 * every time it appears.
 */
@Composable
fun TrippinStamp(
    title: String,
    subtitle: String? = null,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    var landed by remember(title) { mutableStateOf(false) }
    LaunchedEffect(title) { landed = true }
    val scale by animateFloatAsState(
        targetValue = if (landed) 1f else 1.18f,
        animationSpec = tween(durationMillis = 600, easing = TrippinSettle),
        label = "stampScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (landed) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = TrippinSettle),
        label = "stampAlpha"
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .rotate(-1.5f)
            .border(BorderStroke(2.dp, AccentCrimson), RoundedCornerShape(3.dp))
            .background(Panel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = AccentCrimson,
            style = TrippinType.Label
        )
        subtitle?.let {
            Text(
                text = it.uppercase(),
                color = AccentCrimson,
                style = TrippinType.Caption
            )
        }
        detail?.let {
            Text(
                text = it.uppercase(),
                color = InkMuted,
                style = TrippinType.Caption
            )
        }
    }
}

/**
 * Applies the stamp settle (600ms, the same one TrippinStamp uses) to any content. Used where a
 * screen has its own stamp artwork and only needs the landing, so the motion stays one behaviour
 * instead of a re-implementation per screen.
 */
@Composable
fun StampLanding(
    delayMillis: Int = 0,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var landed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (delayMillis > 0) kotlinx.coroutines.delay(delayMillis.toLong())
        landed = true
    }
    val scale by animateFloatAsState(
        targetValue = if (landed) 1f else 1.18f,
        animationSpec = tween(durationMillis = 600, easing = TrippinSettle),
        label = "stampLandingScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (landed) 1f else 0f,
        animationSpec = tween(durationMillis = 600, easing = TrippinSettle),
        label = "stampLandingAlpha"
    )
    Box(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            this.alpha = alpha
        }
    ) { content() }
}

/**
 * The only haptic this app uses: one light physical acknowledgement that something committed.
 * LongPress is the commit-level haptic; TextHandleMove belongs on a text cursor, not a button.
 */
@Composable
fun rememberCommitHaptic(): () -> Unit {
    val haptic = LocalHapticFeedback.current
    return remember(haptic) { { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
}

/**
 * The tick: the shortest motion in the set, 120ms from 0.96 back to 1, on the control that
 * committed. Design system section 4 puts it on a committed action and names a mark visited, a vote
 * cast and a field saved, so pass a counter the action increments rather than a boolean, which is
 * what keeps the motion tied to the commit and off the screen's arrival:
 *
 *   var commits by remember { mutableIntStateOf(0) }
 *   val commitHaptic = rememberCommitHaptic()
 *   ...
 *   modifier = Modifier.tickOnCommit(commits)
 *   onClick = { commitHaptic(); commits++ }
 *
 * Nothing loops, nothing bounces and the figure is never animated before it is known: the control
 * snaps to 0.96 and settles back to 1 on the one easing curve the app uses.
 */
@Composable
fun Modifier.tickOnCommit(commits: Int): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(commits) {
        if (commits > 0) {
            scale.snapTo(0.96f)
            scale.animateTo(1f, animationSpec = tween(durationMillis = 120, easing = TrippinSettle))
        }
    }
    return graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * The plate a venue gets when no real photograph of it exists.
 *
 * Design system section 5: a photograph is shown only when it belongs to that venue, and when none
 * exists nothing is substituted. So instead of a stand-in image this draws the venue's own initials
 * large in ink, with the venue's own category underneath as a caption, on the neutral surface the
 * Trips row already uses for its own no-photo mark. Both facts come off the wire and neither is
 * invented: the initials from the venue's name, the category from the type the place was recorded
 * as. One plate on every surface is the point: a stop with a photograph and a stop without one read
 * as one card design rather than a card and a gap.
 *
 * [height] is passed in so the plate matches the photograph it replaces on that screen, and the
 * initials scale with it. Under 100dp the caption is dropped and only the monogram is drawn, because
 * a 54dp thumbnail has no room for a category line and a squeezed one reads as clipped text.
 */
@Composable
fun PlacePlate(
    title: String,
    category: String?,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val initials = placeInitials(title)
    val caption = category?.trim()?.takeIf { it.isNotBlank() }?.uppercase()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(NeutralInkSurface),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = initials,
            color = Ink,
            style = if (height >= 100.dp) {
                TrippinType.Display.copy(letterSpacing = 4.sp)
            } else {
                TrippinType.Heading.copy(letterSpacing = 1.sp)
            }
        )
        if (caption != null && height >= 100.dp) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = caption,
                color = InkMuted,
                style = TrippinType.Caption.copy(letterSpacing = 1.sp)
            )
        }
    }
}

/**
 * At most two letters, taken from the venue's own name. Connector words are skipped so Catacombs of
 * Paris reads CP and not CO. A one word name keeps its first letter.
 */
internal fun placeInitials(title: String): String {
    val words = title.split(Regex("[^\\p{L}]+"))
        .filter { it.isNotBlank() && it.lowercase() !in PLATE_SKIP_WORDS }
    return words.take(2).map { it.first().uppercaseChar() }.joinToString("")
}

/** Words a venue name uses to join its own nouns. They carry no letter worth printing. */
private val PLATE_SKIP_WORDS = setOf(
    "of", "the", "and", "a", "an", "de", "du", "des", "la", "le", "les", "di", "del",
    "el", "los", "las", "y", "et", "da", "do", "van", "von"
)

/**
 * A whole amount with the currency it was handed, and no symbol at all when it was handed none.
 *
 * Money is a claim about a real price, so this is one rule for the whole app rather than one rule
 * per screen. The symbol prints only for a code the caller actually stated, an unrecognised code
 * prints its own letters, and no code at all prints the bare number, which is the honest answer
 * when nobody has said what the number is in. Three screens carried their own copy of this until
 * 2026-09-21, which is three places the rule could drift apart without anyone noticing.
 */
fun formatStatedAmount(value: Double, currency: String?): String {
    val grouped = NumberFormat.getIntegerInstance(Locale.US).format(value.roundToLong())
    val code = currency?.trim()?.uppercase()
    val prefix = when (code) {
        null, "" -> ""
        "INR" -> "₹"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "USD" -> "$"
        else -> "$code "
    }
    return "$prefix$grouped"
}

/**
 * Whether an address names a street, which is the only thing that makes it usable on foot.
 *
 * An address is either navigable or it is not printed as one, so this is one rule for the whole app
 * rather than one rule per screen. The audit found two kinds of unusable address: `東京都, 東京都,
 * 日本`, where the prefecture is repeated and nothing else is there, and `13, 台東区, 東京都, 日本`,
 * a bare number in a ward. Neither is walkable, and both sat next to a button offering to navigate.
 * A street-level address carries a number or a named street of its own, and it does not repeat one
 * of its parts.
 *
 * It lived as a private helper on the Plan screen until 2026-09-21, and the Today hero printed the
 * raw field with a copy action beside it, which is the same defect on the screen a traveller opens
 * on the day. Both screens call this now.
 */
internal fun isStreetLevelAddress(address: String?): Boolean {
    if (address.isNullOrBlank()) return false
    val parts = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return false

    val repeatsItself = parts
        .groupingBy { it.lowercase() }
        .eachCount()
        .any { it.value > 1 }
    if (repeatsItself) return false

    return parts.any { part -> part.any { it.isDigit() } }
}

/**
 * The ink in a field this app draws.
 *
 * A Material text field takes its text, label, cursor and placeholder colours from the system
 * scheme, and this app is parchment in every theme. So a field that named only its own border and
 * left the ink to the scheme drew stock dark-mode colours: on a phone in dark mode the sign-in field
 * put near-white text into the white box the app had already pinned, which made what a person typed
 * invisible, and every other field drew grey on a cream page. The dark scheme is a separate decision
 * that belongs to Kevin; what a field this app draws is not, and it is ink on the surface the screen
 * around it already declares.
 *
 * One function, beside formatStatedAmount and isStreetLevelAddress, so a field's ink is decided once
 * rather than once per screen. Only the ink is named. The container is whatever the screen around the
 * field declares, transparent by default, and Panel for the one field that sits on the page rather
 * than inside a dialog.
 */
@Composable
fun trippinFieldInk(container: Color = Color.Transparent): TextFieldColors =
    OutlinedTextFieldDefaults.colors(
        focusedTextColor = Ink,
        unfocusedTextColor = Ink,
        disabledTextColor = InkMuted,
        cursorColor = AccentCrimson,
        focusedContainerColor = container,
        unfocusedContainerColor = container,
        disabledContainerColor = container,
        focusedLabelColor = InkMuted,
        unfocusedLabelColor = InkMuted,
        disabledLabelColor = InkMuted,
        focusedPlaceholderColor = InkMuted,
        unfocusedPlaceholderColor = InkMuted,
        disabledPlaceholderColor = InkMuted
    )


