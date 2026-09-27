package com.trippin.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * How much a surface is meant to stand out.
 *
 * Every surface in the app used to be the same: a 2.5dp edge of full ink and a hard 4dp offset shadow,
 * on cards, buttons, chips, fields and tabs alike. With nothing quieter than anything else there was no
 * hierarchy, and a screen read as one flat wall. These three tiers are the fix, and a screen picks one
 * per element rather than getting the loudest by default.
 */
enum class SurfaceTier {
    /** The default. A hairline edge, no shadow: a list row, a form card, a chip, a field. */
    FLAT,

    /** The one thing on a screen that leads. A hairline plus a soft shadow, or in dark mode a lift. */
    RAISED,

    /**
     * The primary action, and nothing else. Keeps the hard offset shadow in ink, which is the app's own
     * signature: it survives here precisely because here it means something.
     */
    ACTION
}

/**
 * A surface, drawn at one of three [SurfaceTier]s.
 *
 * [SurfaceTier.ACTION] keeps the original treatment: a face over a hard offset shadow, in space the
 * surface reserves by padding its own end and bottom, so it never bleeds onto what is below it, and it
 * presses down onto that shadow on touch instead of rippling. The quieter two tiers reserve nothing and
 * sit flush, which is also what lets a list of cards keep an even rhythm: the old asymmetric padding
 * pushed every card 4dp off its own column.
 */
@Composable
fun TrippinSurface(
    modifier: Modifier = Modifier,
    tier: SurfaceTier = SurfaceTier.FLAT,
    shape: Shape = TrippinTheme.shapes.card,
    background: Color = if (tier == SurfaceTier.RAISED) {
        TrippinTheme.colors.panelRaised
    } else {
        TrippinTheme.colors.panel
    },
    borderColor: Color = if (tier == SurfaceTier.ACTION) {
        TrippinTheme.colors.line
    } else {
        TrippinTheme.colors.hairline
    },
    borderWidth: Dp = if (tier == SurfaceTier.ACTION) 2.dp else 1.dp,
    shadow: Dp = if (tier == SurfaceTier.ACTION) 3.dp else 0.dp,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    if (tier != SurfaceTier.ACTION) {
        QuietSurface(
            modifier = modifier,
            tier = tier,
            shape = shape,
            background = background,
            borderColor = borderColor,
            borderWidth = borderWidth,
            fillWidth = fillWidth,
            enabled = enabled,
            onClick = onClick,
            content = content
        )
        return
    }
    val shadowColor = TrippinTheme.colors.shadow
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    // How far the face has travelled toward its shadow. Pressed closes most of the gap so the card
    // reads as pushed in; released springs it back.
    val sink by animateFloatAsState(
        targetValue = if (pressed && onClick != null && enabled) 1f else 0f,
        animationSpec = TrippinMotion.fast(),
        label = "surface-sink"
    )

    Box(modifier = modifier.padding(end = shadow, bottom = shadow)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = shadow, y = shadow)
                .clip(shape)
                .background(shadowColor)
        )

        val faceOffset = shadow * sink
        val faceModifier = Modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .offset(x = faceOffset, y = faceOffset)
            .clip(shape)
            .background(background)
            .border(borderWidth, borderColor, shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        onClick = onClick
                    )
                } else Modifier
            )

        Box(modifier = faceModifier) { content() }
    }
}

/**
 * The flat and raised tiers. No offset shadow, so nothing is reserved and the surface sits flush in its
 * column; a raised one carries a real soft shadow, which on a dark page is close to invisible, so there
 * the lift comes from [TrippinColors.panelRaised] being a step lighter instead.
 */
@Composable
private fun QuietSurface(
    modifier: Modifier,
    tier: SurfaceTier,
    shape: Shape,
    background: Color,
    borderColor: Color,
    borderWidth: Dp,
    fillWidth: Boolean,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    val colors = TrippinTheme.colors
    val base = modifier
        .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
        .then(
            if (tier == SurfaceTier.RAISED) {
                Modifier.shadow(
                    elevation = 10.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = colors.shadowSoft,
                    spotColor = colors.shadowSoft
                )
            } else Modifier
        )
        .clip(shape)
        .background(background)
        .border(borderWidth, borderColor, shape)
        .then(
            if (onClick != null) {
                Modifier.clickable(enabled = enabled, onClick = onClick)
            } else Modifier
        )

    Box(modifier = base) { content() }
}

/** Shorthand: the default card shape, filling its width. Quiet unless asked to lead. */
@Composable
fun TrippinCard(
    modifier: Modifier = Modifier,
    tier: SurfaceTier = SurfaceTier.FLAT,
    background: Color = if (tier == SurfaceTier.RAISED) {
        TrippinTheme.colors.panelRaised
    } else {
        TrippinTheme.colors.panel
    },
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    TrippinSurface(
        modifier = modifier,
        tier = tier,
        shape = TrippinTheme.shapes.card,
        background = background,
        onClick = onClick,
        content = content
    )
}

/** A subtle press-scale for controls that are not raised surfaces (icon buttons, small taps). */
fun Modifier.pressScale(pressed: Boolean, min: Float = 0.94f): Modifier = this.then(
    Modifier.graphicsLayer {
        val s = if (pressed) min else 1f
        scaleX = s
        scaleY = s
    }
)

/** A ripple-free tap for a bar tab or a segment that already carries its own selected look. */
@Composable
fun Modifier.clickableTab(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.then(
        Modifier.clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        )
    )
}
