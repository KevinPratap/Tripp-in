package com.trippin.core.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The one raised surface in the app: a face with a thick ink border and a hard offset shadow behind
 * it, the tactile 4px drop shadow the design system fixes for every Tripp'in surface. It is drawn,
 * not elevated: there is no blur and no Material tonal lift, so a card in light mode is white paper
 * with a black shadow and in dark mode a carbon panel with the same hard offset.
 *
 * The shadow sits in reserved space (the surface pads its own bottom and end by [shadow]), so a card
 * in a list never bleeds its shadow onto the card below it. When [onClick] is set the face presses
 * down onto its shadow, the way a physical stamp would, instead of rippling.
 */
@Composable
fun TrippinSurface(
    modifier: Modifier = Modifier,
    shape: Shape = TrippinTheme.shapes.card,
    background: Color = TrippinTheme.colors.panel,
    borderColor: Color = TrippinTheme.colors.line,
    borderWidth: Dp = 2.5.dp,
    shadow: Dp = 4.dp,
    fillWidth: Boolean = true,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
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

/** Shorthand: the default card shape, filling its width. */
@Composable
fun TrippinCard(
    modifier: Modifier = Modifier,
    background: Color = TrippinTheme.colors.panel,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    TrippinSurface(
        modifier = modifier,
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
