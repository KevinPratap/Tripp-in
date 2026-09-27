package com.trippin.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// --------------------------------------------------------------------------------------------------
// Scaffolding
// --------------------------------------------------------------------------------------------------

/**
 * A page. Paints the paper background, and leaves the system bar insets to the top bar (which pads
 * itself under the status bar) and to the shell (which reserves the bottom bar), so a screen never
 * double counts an inset.
 */
@Composable
fun TrippinScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = modifier,
        containerColor = TrippinTheme.colors.paper,
        contentColor = TrippinTheme.colors.ink,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = topBar,
        content = content
    )
}

/**
 * The app's top bar. A title, an optional second line under it, an optional back affordance, and a
 * row of actions. It draws on the paper with a 2.5px ink rule under it rather than a Material shadow,
 * so it belongs to the same flat, bordered world as the cards below it.
 */
@Composable
fun TrippinTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color = TrippinTheme.colors.inkMuted,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    val colors = TrippinTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.paper)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                TrippinIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack
                )
                Spacer(Modifier.width(4.dp))
            } else {
                Spacer(Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = TrippinType.Title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = TrippinType.Caption,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) { actions() }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(colors.line)
        )
    }
}

// --------------------------------------------------------------------------------------------------
// Buttons
// --------------------------------------------------------------------------------------------------

/** The primary action. A filled, bordered, hard-shadowed stamp. */
@Composable
fun TrippinButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: ImageVector? = null,
    container: Color = TrippinTheme.colors.accent,
    onContainer: Color = TrippinTheme.colors.onAccent
) {
    val colors = TrippinTheme.colors
    val active = enabled && !loading
    TrippinSurface(
        modifier = modifier,
        tier = SurfaceTier.ACTION,
        shape = TrippinTheme.shapes.button,
        background = if (active) container else colors.panelAlt,
        fillWidth = true,
        enabled = active,
        onClick = { if (active) onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val content = if (active) onContainer else colors.inkMuted
            if (loading) {
                CircularProgressIndicator(
                    color = content,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
            } else if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = TrippinType.Label, color = content)
        }
    }
}

/** A secondary action: bordered, on the paper, no fill. */
@Composable
fun TrippinOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
    contentColor: Color = TrippinTheme.colors.accent
) {
    val colors = TrippinTheme.colors
    TrippinSurface(
        modifier = modifier,
        shape = TrippinTheme.shapes.button,
        background = colors.panel,
        borderColor = colors.controlEdge,
        fillWidth = true,
        enabled = enabled,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tint = if (enabled) contentColor else colors.inkMuted
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text = text, style = TrippinType.Label, color = tint)
        }
    }
}

/** A 44dp square tap target with an ink icon. The floor for every icon-only control. */
@Composable
fun TrippinIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TrippinTheme.colors.ink,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .then(if (enabled) Modifier else Modifier),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.IconButton(onClick = onClick, enabled = enabled) {
            Icon(icon, contentDescription = contentDescription, tint = if (enabled) tint else TrippinTheme.colors.inkMuted)
        }
    }
}

// --------------------------------------------------------------------------------------------------
// Chips + tabs
// --------------------------------------------------------------------------------------------------

/** A toggle chip. Selected fills accent; unselected is a bordered panel. */
@Composable
fun TrippinChoiceChip(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colors = TrippinTheme.colors
    TrippinSurface(
        modifier = modifier,
        shape = TrippinTheme.shapes.chip,
        background = if (selected) colors.accent else colors.panel,
        borderColor = if (selected) Color.Transparent else colors.controlEdge,
        fillWidth = false,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 40.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            val content = if (selected) colors.onAccent else colors.ink
            if (leadingIcon != null) {
                Icon(leadingIcon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text = text, style = TrippinType.Label, color = content)
        }
    }
}

/** A two-or-more way segmented control. One filled segment, the rest bordered. */
@Composable
fun TrippinSegmentedTabs(
    options: List<String>,
    selectedIndex: Int,
    onOptionSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TrippinTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.hairline, TrippinTheme.shapes.button)
            .background(colors.panelAlt, TrippinTheme.shapes.button)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .background(
                        color = if (selected) colors.accent else Color.Transparent,
                        shape = RoundedCornerShape(9.dp)
                    )
                    .then(
                        Modifier.clickableNoRipple { onOptionSelected(index) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = TrippinType.Label,
                    color = if (selected) colors.onAccent else colors.ink
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// Fields
// --------------------------------------------------------------------------------------------------

@Composable
fun trippinTextFieldColors(): TextFieldColors {
    val colors = TrippinTheme.colors
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = colors.ink,
        unfocusedTextColor = colors.ink,
        disabledTextColor = colors.inkMuted,
        cursorColor = colors.accent,
        focusedBorderColor = colors.accent,
        unfocusedBorderColor = colors.controlEdge,
        disabledBorderColor = colors.hairline,
        focusedContainerColor = colors.panel,
        unfocusedContainerColor = colors.panel,
        disabledContainerColor = colors.panelAlt,
        focusedLabelColor = colors.accent,
        unfocusedLabelColor = colors.inkMuted,
        focusedPlaceholderColor = colors.inkMuted,
        unfocusedPlaceholderColor = colors.inkMuted,
        focusedLeadingIconColor = colors.ink,
        unfocusedLeadingIconColor = colors.inkMuted,
        focusedTrailingIconColor = colors.ink,
        unfocusedTrailingIconColor = colors.inkMuted
    )
}

/** A text field in the app's ink, 16sp so iOS-style auto zoom never triggers on a phone keyboard. */
@Composable
fun TrippinTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        singleLine = singleLine,
        // 16sp keeps a phone keyboard from auto zooming into the field (accessibility invariant).
        textStyle = TrippinType.Body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
        label = label?.let { { Text(it, style = TrippinType.Caption) } },
        placeholder = placeholder?.let { { Text(it, style = TrippinType.Body, color = TrippinTheme.colors.inkMuted) } },
        leadingIcon = leadingIcon?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = TrippinTheme.shapes.field,
        colors = trippinTextFieldColors()
    )
}

// --------------------------------------------------------------------------------------------------
// Labels, pills, states
// --------------------------------------------------------------------------------------------------

/** A small uppercase section label above a group of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = TrippinType.Eyebrow,
        color = TrippinTheme.colors.inkMuted,
        modifier = modifier
    )
}

enum class PillTone { ACCENT, GOOD, WARN, NEUTRAL, DANGER }

/** A tinted status pill: VERIFIED, DRAFT, LOCKED, OVER BUDGET. Tone carries it, not an ink border. */
@Composable
fun StatusPill(text: String, tone: PillTone, modifier: Modifier = Modifier) {
    val colors = TrippinTheme.colors
    val (fg, bg) = when (tone) {
        PillTone.ACCENT -> colors.onAccent to colors.accent
        PillTone.GOOD -> colors.good to colors.goodSurface
        PillTone.WARN -> colors.warn to colors.warnSurface
        PillTone.NEUTRAL -> colors.neutral to colors.neutralSurface
        PillTone.DANGER -> colors.onAccent to colors.danger
    }
    Box(
        modifier = modifier
            .background(bg, TrippinTheme.shapes.badge)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text = text.uppercase(), style = TrippinType.Eyebrow, color = fg)
    }
}

/** A centered loading block with a label under a crimson spinner. */
@Composable
fun LoadingBlock(label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = TrippinTheme.colors.accent)
            Spacer(Modifier.height(16.dp))
            Text(label, style = TrippinType.Body, color = TrippinTheme.colors.inkMuted)
        }
    }
}

/** A full-screen empty or error state: an icon, a headline, a line, and an optional action. */
@Composable
fun MessageState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    titleColor: Color = TrippinTheme.colors.ink
) {
    val colors = TrippinTheme.colors
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .border(2.5.dp, colors.line, RoundedCornerShape(18.dp))
                    .background(colors.panel, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text(title, style = TrippinType.Title, color = titleColor)
            Spacer(Modifier.height(8.dp))
            Text(
                body,
                style = TrippinType.Body,
                color = colors.inkMuted,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(20.dp))
                TrippinButton(text = actionLabel, onClick = onAction, modifier = Modifier.width(200.dp))
            }
        }
    }
}

/**
 * The stand-in for a venue with no real photograph. The app never shows a stock image as a venue
 * photo, so this plate names the place in its own letters and its category instead, on a bordered
 * neutral surface.
 */
@Composable
fun PlacePlate(
    title: String,
    category: String,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 150.dp
) {
    val colors = TrippinTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(colors.neutralSurface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = TrippinType.Heading,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (category.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = category.replace('_', ' ').uppercase(),
                    style = TrippinType.Caption,
                    color = colors.inkMuted
                )
            }
        }
    }
}

// --------------------------------------------------------------------------------------------------
// Small helpers
// --------------------------------------------------------------------------------------------------

/** A click with no ripple, for segments and rows that carry their own selected look. */
@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick
    )
}
