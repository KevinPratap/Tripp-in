package com.trippin.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


/**
 * The house components. The look is a printed comic panel: a solid ink border with a hard offset
 * shadow, never a soft Material elevation. Both are used across every screen, so changing them here
 * changes the whole app's feel at once.
 */
@Composable
fun TrippinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(modifier = modifier.fillMaxWidth().height(52.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(ComicInk, RoundedCornerShape(8.dp))
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(2.dp, ComicInk),
            colors = ButtonDefaults.buttonColors(
                containerColor = ComicRed,
                contentColor = PureWhite,
                disabledContainerColor = ComicRed.copy(alpha = 0.4f),
                disabledContentColor = PureWhite.copy(alpha = 0.8f)
            ),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = PureWhite
            )
        }
    }
}

@Composable
fun TrippinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(ComicInk, RoundedCornerShape(10.dp))
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(2.dp, ComicInk),
            colors = CardDefaults.cardColors(containerColor = ComicPanel),
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
            .background(ComicPanel, RoundedCornerShape(8.dp))
            .border(2.dp, ComicInk, RoundedCornerShape(8.dp))
            .padding(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = index == selectedIndex
                val pillBackground = if (isSelected) ComicInk else ComicPanel
                val pillTextColor = if (isSelected) ComicPaper else ComicInk

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
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }
    }
}

/**
 * High-contrast status badge reflecting deterministic verification reality.
 */
@Composable
fun TrippinStatusBadge(
    status: String,
    modifier: Modifier = Modifier
) {
    val isVerified = status.equals("VERIFIED", ignoreCase = true) || status.equals("READY", ignoreCase = true)
    val badgeColor = if (isVerified) ComicRed else ComicYellow
    val textColor = if (isVerified) ComicPaper else ComicInk

    Box(
        modifier = modifier
            .background(badgeColor, RoundedCornerShape(4.dp))
            .border(1.5.dp, ComicInk, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = status.uppercase(),
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/*
 * Motion. One easing for everything that settles, so the app reads as one thing instead of a set of
 * effects. Nothing here bounces, glows or floats, and nothing animates a figure that has not been
 * worked out yet: motion only ever reports something that actually happened.
 */
val TrippinSettle = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** A screen arriving: a short rise and fade. Small on purpose, it should read as "you are here". */
@Composable
fun ArriveOnEnter(
    modifier: Modifier = Modifier,
    delayMillis: Int = 0,
    content: @Composable () -> Unit
) {
    var arrived by remember { mutableStateOf(false) }
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
            .border(BorderStroke(2.dp, ComicRed), RoundedCornerShape(3.dp))
            .background(ComicPanel)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title.uppercase(),
            color = ComicRed,
            fontWeight = FontWeight.Black,
            fontSize = 13.sp,
            letterSpacing = 1.sp
        )
        subtitle?.let {
            Text(
                text = it.uppercase(),
                color = ComicRed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
        detail?.let {
            Text(
                text = it.uppercase(),
                color = ComicMuted,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
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


