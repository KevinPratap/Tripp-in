package com.trippin.ai.ui.brand

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.theme.Trip
import kotlinx.coroutines.launch

private data class Page(val art: Art, val title: String, val body: String)

private val pages = listOf(
    Page(Art.ROUTE, "Plan the trip together", "Everyone adds places. Trippin turns them into a day by day route that fits how your group travels."),
    Page(Art.CARDS, "Swipe to decide", "Vote on places like cards. Swipe right to love one, left to pass, and see what the whole group agrees on."),
    Page(Art.SPLIT, "Split costs fairly", "Log what you spend and see exactly who owes whom, in each currency, with the fewest payments."),
)

/** Three quiet screens on first launch. Shown once, skippable at any point. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val state = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = state.currentPage == pages.lastIndex
    Column(Modifier.fillMaxSize().background(Trip.Paper).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            TrippinLockup(markSize = 28.dp)
            Spacer(Modifier.weight(1f))
            if (!last) TextButton(onClick = onDone) { Text("Skip", color = Trip.Muted, style = MaterialTheme.typography.labelLarge) }
        }
        HorizontalPager(state, Modifier.weight(1f).fillMaxWidth()) { i ->
            val p = pages[i]
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Illustration(p.art, size = 240.dp)
                Spacer(Modifier.height(28.dp))
                Text(p.title, style = MaterialTheme.typography.displaySmall, color = Trip.Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = Trip.Muted, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            pages.indices.forEach { i ->
                val on = i == state.currentPage
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(if (on) 22.dp else 6.dp)
                        .clip(RoundedCornerShape(999.dp)).background(if (on) Trip.Signal else Trip.Line),
                )
            }
        }
        SignalButton(if (last) "Get started" else "Continue", onClick = {
            if (last) onDone() else scope.launch { state.animateScrollToPage(state.currentPage + 1) }
        })
        Spacer(Modifier.height(20.dp))
    }
}
