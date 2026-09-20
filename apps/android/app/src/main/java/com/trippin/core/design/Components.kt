package com.trippin.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
                        fontSize = 11.sp,
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
            fontSize = 9.sp,
            letterSpacing = 0.5.sp
        )
    }
}


