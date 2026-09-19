package com.trippin.core.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

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
