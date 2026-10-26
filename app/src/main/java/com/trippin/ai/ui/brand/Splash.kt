package com.trippin.ai.ui.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.ai.ui.theme.Display
import com.trippin.ai.ui.theme.Trip

/** First frame of the app: the mark draws itself, then the name fades in. One moment, nothing else. */
@Composable
fun SplashScreen() {
    val draw = remember { Animatable(0f) }
    val name = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        draw.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
        name.animateTo(1f, tween(350))
    }
    Box(Modifier.fillMaxSize().background(Trip.Paper), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TrippinMark(size = 96.dp, progress = draw.value)
            Text(
                "Trippin",
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontStyle = FontStyle.Italic, fontSize = 40.sp),
                color = Trip.Ink, modifier = Modifier.alpha(name.value),
            )
        }
    }
}
