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
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
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
            colors = trippinButtonColors(),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = text.uppercase(),
                style = TrippinType.Label
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

/*
 * A TrippinStatusBadge was declared here. It took a server status string and printed it uppercased,
 * with VERIFIED and READY as its good branch, and it had no caller anywhere in the app: Home was its
 * only one and Home is deleted. Plan section 8 item 9 deletes any READY or VERIFIED label that is not
 * derived from real state, and a component whose whole job is to print whichever status string it is
 * handed is exactly that, so it is deleted rather than left for a screen to pick up later. If a shared
 * status chip is wanted, it has to take a derived state and not a wire string, the way the Trips card's
 * own state chip does (Draft, Deciding, Locked, Finished, computed from the trip), and it has to name
 * its colour from the section 1 table this one already used.
 */

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

/** The whole number a grouped amount is made of: 1 to 3 digits, then comma groups whose last one is
 *  three digits. The groups before the last may be two digits, which is how a rupee amount is
 *  grouped (10,00,000), or three, which is how [formatStatedAmount] prints every currency
 *  (1,000,000). */
private val GROUPED_WHOLE = Regex("""\d{1,3}(,\d{2,3})*,\d{3}""")

/**
 * The read half of [formatStatedAmount]: the amount somebody typed into a money field, or null when
 * the field does not hold a number this app can read.
 *
 * It reads the grouping this app itself prints, so 1,500 and 10,00,000 are both numbers, which is
 * why the fields that call it are allowed to let a comma through. What it will not do is choose a
 * reading of an ambiguous figure: a comma group of two digits counts as a separator only when
 * another comma group follows it, and the last group has to be three digits, so 1500,50 is refused
 * rather than read as 150050, which would be a hundred times what was typed.
 */
internal fun parseStatedAmount(text: String): Double? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    trimmed.toDoubleOrNull()?.let { return it }
    if (trimmed.count { it == '.' } > 1) return null
    val wholePart = trimmed.substringBefore('.')
    val fraction = trimmed.substringAfter('.', missingDelimiterValue = "")
    if (fraction.contains(',') || !GROUPED_WHOLE.matches(wholePart)) return null
    val digits = wholePart.replace(",", "")
    return (if (fraction.isEmpty()) digits else "$digits.$fraction").toDoubleOrNull()
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

/**
 * The colours of the top bar this app draws.
 *
 * A Material top bar takes its container from the scheme's surface and its title, its back arrow and
 * its actions from the scheme's onSurface and onSurfaceVariant (material3's own TopAppBarSmallTokens),
 * so on a phone in dark mode every bar but the Trips tab drew Material's dark card with pale grey ink
 * inside an app that is parchment on every other surface, and even in light mode it drew a pure white
 * bar with Material's near-black on a cream page. The bar is part of the page and not a card, so it is
 * Paper with Ink on it, which is the rule the dialogs already follow with Panel. The dark palette
 * itself is a separate decision that belongs to Kevin and is untouched by this.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun trippinTopBarColors(): TopAppBarColors =
    TopAppBarDefaults.topAppBarColors(
        containerColor = Paper,
        scrolledContainerColor = Paper,
        navigationIconContentColor = Ink,
        titleContentColor = Ink,
        actionIconContentColor = Ink
    )

/**
 * The ink inside a popup menu, named once.
 *
 * A Material menu takes its plate from the scheme's surfaceContainer and each item's text and icons
 * from onSurface and onSurfaceVariant, with onSurface at 38 percent behind a disabled item
 * (material3's MenuTokens.ContainerColor, ListTokens.ListItemLabelTextColor and
 * ListItemLeadingIconColor, and the three disabled rows of ListTokens). Theme.kt maps five rows of the
 * scheme and leaves the rest at Material's own defaults, so the app's one menu drew Material's
 * lavender grey plate #F3EDF7 with #1D1B20 text and #49454F icons on a parchment page, and on a phone
 * in dark mode it drew #211F26 with #E6E0E9. A menu is a card that floats over the page, so it is
 * Panel with Ink on it, which is the pair every dialog in the app already declares. Disabled ink is
 * InkMuted, matching trippinFieldInk below.
 */
@Composable
fun trippinMenuItemColors(): MenuItemColors =
    MenuDefaults.itemColors(
        textColor = Ink,
        leadingIconColor = Ink,
        trailingIconColor = Ink,
        disabledTextColor = InkMuted,
        disabledLeadingIconColor = InkMuted,
        disabledTrailingIconColor = InkMuted
    )

/**
 * The ink on a plain text action this app draws.
 *
 * A Material text button takes its label from the scheme's primary and, when it cannot be tapped,
 * from onSurface at 38 percent (material3's own TextButtonTokens: LabelTextColor is Primary,
 * DisabledLabelTextColor is OnSurface with DisabledLabelTextOpacity 0.38, both read from the artifact
 * this app builds against). So every way out of a dialog in this app was painted in whatever the
 * scheme called primary rather than in a colour the app names, and a disabled one was painted in a
 * washed out ink at about a third of the contrast the design system asks for. The two helpers beside
 * this one already name InkMuted for ink that cannot be tapped, in a field and in a menu.
 *
 * AccentCrimson has three jobs and a low emphasis action is not one of them: a filled primary button,
 * the selected tab or chip, and a link. A way out of a dialog is none of those, so the ink is Ink,
 * which is the decision the planner's own Cancel action already made by hand. The committing action of
 * a dialog may pass the accent instead, which is why the colour is a parameter. The two container
 * slots are left alone on purpose: a text button's container is transparent in both states, which is
 * what it should be, so there is nothing there to name.
 */
@Composable
fun trippinTextButtonColors(contentColor: Color = Ink): ButtonColors =
    ButtonDefaults.textButtonColors(
        contentColor = contentColor,
        disabledContentColor = InkMuted
    )

/**
 * The four colours of a filled button this app draws.
 *
 * A Material filled button takes its container from the scheme's primary and its label from the
 * scheme's onPrimary, and, when it cannot be tapped, its container from onSurface at 12 percent and
 * its label from onSurface at 38 percent. That is material3's own FilledButtonTokens read out of the
 * artifact this app builds against rather than guessed: ContainerColor is Primary, LabelTextColor is
 * OnPrimary, DisabledContainerColor is OnSurface with DisabledContainerOpacity 0.12 and
 * DisabledLabelTextColor is OnSurface with DisabledLabelTextOpacity 0.38. Theme.kt maps seven rows of
 * the scheme and leaves the rest at Material's defaults, and a colours object that names only its
 * container keeps all three of the others, so of the eighteen filled buttons in this app the one
 * that named every slot was the house button and the other seventeen drew stock colours: on a phone
 * in dark mode the scheme's onPrimary is Material's dark purple #381E72, which put a purple label on
 * a crimson fill, and every button that could not be tapped drew two library greys that no screen in
 * this app had chosen.
 *
 * So all four are named here and nowhere else. The container is the caller's, defaulting to
 * AccentCrimson because the accent is the primary button's own colour, and the destructive confirm
 * passes DangerCrimson. The ink on it is OnCrimson, the token declared for the label, icon or spinner
 * on a filled crimson control. A button that cannot be tapped is neither good nor bad, which is the
 * meaning section 1 gives the NeutralInk pair on its own surface, the pair the Draft chip on Trips,
 * the Upcoming chip on Today and the venue plate already draw: a disabled button drains to that
 * neutral fill with neutral ink on it rather than carrying an accent it cannot honour. It
 * deliberately follows the table's pair rather than the InkMuted the three helpers beside this one
 * use, because those name the ink of a line of text that cannot be edited or tapped, and a filled
 * control is a surface.
 *
 * The ink inside a filled button is the button's own: a Text or Icon child takes it from
 * LocalContentColor, and that is what a spinner inside one must name too, since
 * CircularProgressIndicator defaults to the scheme's primary and would stay white on a fill that has
 * drained to neutral.
 */
@Composable
fun trippinButtonColors(container: Color = AccentCrimson): ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = container,
        contentColor = OnCrimson,
        disabledContainerColor = NeutralInkSurface,
        disabledContentColor = NeutralInk
    )

/**
 * The ink of an outlined button this app draws.
 *
 * A Material outlined button takes its label from the scheme's primary and, when it cannot be tapped,
 * from onSurface at 38 percent, which is material3's own OutlinedButtonTokens read out of the artifact
 * this app builds against rather than guessed: LabelTextColor is Primary, DisabledLabelTextColor is
 * OnSurface with DisabledLabelTextOpacity 0.38. Theme.kt maps five rows of the scheme, so in light
 * mode that primary is OceanBlue and therefore the accent, and on a phone in dark mode it is
 * OceanBlueDark #BE123C, a hue no screen chose, which is the same defect the filled and text buttons
 * carried until trippinButtonColors and trippinTextButtonColors.
 *
 * The ink is Ink, and the accent may be passed where the outlined control really is the one
 * interactive thing on its row, which is the Plan stop card's Take me there. InkMuted is the ink that
 * cannot be tapped, matching the two helpers beside this one. The two container slots are left alone
 * on purpose for the same reason a text button's are: an outlined button's container is transparent
 * in both states, so there is nothing there to name.
 */
@Composable
fun trippinOutlinedButtonColors(contentColor: Color = Ink): ButtonColors =
    ButtonDefaults.outlinedButtonColors(
        contentColor = contentColor,
        disabledContentColor = InkMuted
    )

/**
 * The edge of an outlined button this app draws, which is the other half of the same control.
 *
 * A Material outlined button draws its own border when the caller passes none: 1dp of the scheme's
 * outline row (ButtonDefaults.outlinedButtonBorder, colour OutlinedButtonTokens.OutlineColor and width
 * OutlineWidth 1.0dp). Theme.kt maps five rows and leaves outline at Material's own value, so the Plan
 * stop card's Take me there drew a #79747E grey hairline on a page whose every other control edge is
 * ink. The other two outlined buttons, on You, draw their own 1.5dp ink border with a Modifier, which
 * leaves the library's grey one nested underneath the ink one, hidden rather than gone; naming it here
 * is what removes it, and it is a no-op for those two in every theme this app can currently draw
 * because the stroke they already had is the stroke this hands them, in the same shape, at the same
 * width, through the same Modifier.border that Material itself applies.
 *
 * The width is the caller's because the app's control edges are not all one weight: 1.5dp is the
 * dominant recipe and is the default, and the Plan stop card passes 1dp to match the copy address
 * button beside it in the same row.
 */
fun trippinOutlinedButtonBorder(width: Dp = 1.5.dp): BorderStroke = BorderStroke(width, Ink)

/**
 * The plate and the arc of the pull to refresh control this app draws.
 *
 * A PullToRefreshBox draws material3's own indicator when the caller passes none, and that indicator
 * takes its plate from PullToRefreshDefaults.containerColor, which the sources this app builds
 * against define as the scheme's surfaceContainerHigh, and its arc from
 * PullToRefreshDefaults.indicatorColor, which they define as onSurfaceVariant (PullToRefresh.kt, the
 * two getters at :419 and :423, and the Indicator at :434). Theme.kt maps five rows of the scheme and
 * leaves both of those rows at Material's own values, so pulling down on a parchment page dragged a
 * lavender grey plate #ECE6F0 with a #49454F grey arc on it, which is wrong on a phone in light mode
 * as well as in dark mode and is the last control in the app that drew a colour no screen named.
 *
 * The plate is Panel, the card surface the app's one menu and every dialog already float on. The arc
 * is AccentCrimson, which is the colour of every other progress indicator in the app: the load
 * spinners on Plan, Today, Trips and Sign in all pass it, and both indicators on the generating
 * readout do too. The indicator's own elevation stays the library's Level 2, which is the same
 * question as the menu's shadow and is Kevin's call rather than this helper's.
 *
 * The state is the caller's and it has to be the same object the caller hands to PullToRefreshBox,
 * because the indicator follows the finger through it while PullToRefreshBox creates its own when
 * none is passed. So each screen that pulls to refresh hoists rememberPullToRefreshState() once and
 * passes it twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxScope.TrippinRefreshIndicator(
    state: PullToRefreshState,
    isRefreshing: Boolean
) {
    PullToRefreshDefaults.Indicator(
        state = state,
        isRefreshing = isRefreshing,
        modifier = Modifier.align(Alignment.TopCenter),
        containerColor = Panel,
        color = AccentCrimson
    )
}


