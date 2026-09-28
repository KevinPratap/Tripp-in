package com.trippin.core.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.IntOffset

/**
 * The app's motion, in one place.
 *
 * A single easing curve carries every transition, the one the design system fixes for every Tripp'in
 * surface: `(0.22, 1, 0.36, 1)`, an ease that starts fast and settles softly. Nothing here counts a
 * figure up before it is known and nothing bounces a price into place; motion is used to make a state
 * change legible, never to decorate a number the app has not verified.
 */
object TrippinMotion {
    /** The shared easing curve. Used by every tween in the app. */
    val Easing: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

    const val DurationFast = 180
    const val DurationMedium = 320
    const val DurationSlow = 480

    fun <T> fast(): FiniteAnimationSpec<T> = tween(DurationFast, easing = Easing)
    fun <T> medium(): FiniteAnimationSpec<T> = tween(DurationMedium, easing = Easing)
    fun <T> slow(): FiniteAnimationSpec<T> = tween(DurationSlow, easing = Easing)

    /** A gentle, non-bouncy spring for offsets, so a card slide never overshoots into a wobble. */
    fun offsetSpring(): FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.9f,
        stiffness = 320f
    )
}

/**
 * True when the device is playing animations. "Remove animations" in the accessibility settings sets
 * the animator scale to zero, and every transition in the app turns into a plain cut when it does.
 */
@Composable
fun rememberAnimationsEnabled(): Boolean {
    if (LocalInspectionMode.current) return false
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        }.getOrDefault(true)
    }
}
