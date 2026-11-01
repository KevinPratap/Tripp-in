package com.trippin.core.design

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Placeholders shaped like the content that is coming.
 *
 * Every screen used to wait behind a spinner and the words "Loading your trips", which tells the
 * traveller nothing except that something is happening, and drops them into a different layout when it
 * finishes. A skeleton in the shape of the real rows says how much is coming and lands without a jump.
 *
 * A skeleton is never a claim about content: the blocks carry no text and no counts, and the shapes are
 * the layout's, not a guess at what will fill them.
 */

/**
 * One placeholder block.
 *
 * It breathes between two opacities rather than sweeping a highlight across itself: a shimmer reads as
 * a thing in its own right, and this should read as an absence. With animations turned off it simply
 * sits still.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    height: Dp = 14.dp,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    val colors = TrippinTheme.colors
    val alpha = if (rememberAnimationsEnabled()) {
        val transition = rememberInfiniteTransition(label = "skeleton")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = TrippinMotion.Easing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "skeleton-alpha"
        )
        value
    } else {
        0.7f
    }

    Box(
        modifier = modifier
            .height(height)
            .background(colors.panelAlt.copy(alpha = alpha), shape)
    )
}

/**
 * The Trips screen and anything else that is a column of cards, while it loads.
 *
 * One announcement for the whole block, so a screen reader says the screen is loading once instead of
 * reading out a dozen meaningless boxes.
 */
@Composable
fun CardListSkeleton(
    modifier: Modifier = Modifier,
    rows: Int = 3,
    label: String = "Loading"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        repeat(rows) { index ->
            TrippinCard {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        SkeletonBlock(
                            modifier = Modifier.size(46.dp),
                            height = 46.dp,
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            // The first row is widest, so the block reads as a heading and a detail line
                            // rather than as a stack of identical bars.
                            SkeletonBlock(modifier = Modifier.fillMaxWidth(if (index == 0) 0.62f else 0.44f), height = 17.dp)
                            Spacer(Modifier.height(8.dp))
                            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.80f), height = 12.dp)
                        }
                        Spacer(Modifier.width(12.dp))
                        SkeletonBlock(modifier = Modifier.width(58.dp), height = 20.dp, shape = RoundedCornerShape(7.dp))
                    }
                }
            }
        }
    }
}

/** A plan's day, while it loads: a column of stops, each a time and two lines. */
@Composable
fun PlanDaySkeleton(
    modifier: Modifier = Modifier,
    stops: Int = 3,
    label: String = "Loading the plan"
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f), height = 19.dp)
        repeat(stops) {
            TrippinCard {
                Row(Modifier.padding(14.dp)) {
                    Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
                        SkeletonBlock(modifier = Modifier.width(38.dp), height = 14.dp)
                        Spacer(Modifier.height(6.dp))
                        SkeletonBlock(modifier = Modifier.width(30.dp), height = 11.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.66f), height = 16.dp)
                        Spacer(Modifier.height(7.dp))
                        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.45f), height = 12.dp)
                        Spacer(Modifier.height(10.dp))
                        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.84f), height = 11.dp)
                    }
                }
            }
        }
    }
}
