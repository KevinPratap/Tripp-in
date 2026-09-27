package com.trippin.core.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * A haptic tick for a committed action: marking a stop visited, locking a plan, swiping to a new day.
 * Returns a function so a screen can fire it from an event handler rather than on composition.
 */
@Composable
fun rememberCommitHaptic(): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return { haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
}

/**
 * A one-shot pulse: whenever [commitCount] increases the element springs up and settles, so the
 * motion runs on the action itself and not on every recomposition of an already-marked element.
 */
@Composable
fun Modifier.tickOnCommit(commitCount: Int): Modifier {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(commitCount) {
        if (commitCount > 0) {
            scale.animateTo(1.14f, tween(120, easing = TrippinMotion.Easing))
            scale.animateTo(1f, tween(240, easing = TrippinMotion.Easing))
        }
    }
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}
