package com.trippin.core.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

/** Header ink and cream are fixed, not themed: text on a photograph needs light type in both themes. */
internal val HeaderInk = Color(0xFF18181B)
internal val HeaderCream = Color(0xFFFAF8F5)
internal val HeaderCreamMuted = Color(0xFFD9D2C7)

/**
 * The place, full width. A real photograph of it when the server found one (its own encyclopaedia
 * image, credited), otherwise an ink plate that names it in its own letters. Never a stand-in picture.
 */
@Composable
fun PlaceBackdrop(
    photoUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {}
) {
    Box(modifier.background(HeaderInk)) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to HeaderInk.copy(alpha = 0.55f),
                        0.3f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to HeaderInk.copy(alpha = 0.88f)
                    )
                )
            )
        }
        content()
    }
}

/** The credit a photograph carries, bottom right. Nothing when the source is not one the app knows. */
@Composable
fun BoxScope.PhotoCredit(photoUrl: String?, modifier: Modifier = Modifier) {
    val credit = photoUrl?.let(::photoCredit) ?: return
    Text(
        text = "Photo: $credit",
        style = TrippinType.Caption,
        color = HeaderCreamMuted,
        modifier = modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 10.dp)
    )
}

/**
 * The top of a place's screen: backdrop, a row of actions over it, and the place's name set large at
 * the bottom with an eyebrow above and anything else (a status, a date) below.
 */
@Composable
fun PhotoHeader(
    title: String,
    photoUrl: String?,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    eyebrow: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    below: @Composable () -> Unit = {}
) {
    PlaceBackdrop(
        photoUrl = photoUrl,
        contentDescription = "Photo of $title",
        modifier = modifier.fillMaxWidth().height(height)
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!eyebrow.isNullOrBlank()) {
                Text(eyebrow.uppercase(), style = TrippinType.Eyebrow, color = HeaderCreamMuted)
            }
            Text(
                text = title,
                style = TrippinType.Display.copy(fontSize = 52.sp, lineHeight = 54.sp),
                color = HeaderCream,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            below()
        }
        PhotoCredit(photoUrl, Modifier.padding(bottom = 4.dp))
    }
}

/** An icon button that sits on a photograph: a translucent ink disc so it reads on any picture. */
@Composable
fun HeaderIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(HeaderInk.copy(alpha = 0.45f))
            .clickableTab(onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = HeaderCream, modifier = Modifier.size(22.dp))
    }
}

/** A small square of a place: its photograph, or its initial on ink. */
@Composable
fun PlaceThumb(name: String, photoUrl: String?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(12.dp)).background(HeaderInk),
        contentAlignment = Alignment.Center
    ) {
        if (!photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.trim().take(1).uppercase().ifBlank { "?" },
                style = TrippinType.Title,
                color = HeaderCream
            )
        }
    }
}
