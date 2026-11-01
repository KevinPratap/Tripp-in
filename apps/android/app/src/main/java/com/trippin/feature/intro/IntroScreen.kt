package com.trippin.feature.intro

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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.clickableTab
import kotlinx.coroutines.launch

private data class IntroPage(val number: String, val title: String, val body: String, val proof: List<Pair<String, String>>)

/**
 * Three short pages before sign-in, shown once. They say what is different about the app in plain
 * words and show the kind of label a traveller will actually see, instead of promising anything.
 */
private val pages = listOf(
    IntroPage(
        number = "01",
        title = "Plans you can check",
        body = "Every stop is checked against real opening hours and real travel times. Next to each fact is where it came from.",
        proof = listOf("Opening hours" to "OSM", "Walk from the last stop" to "OSRM", "Forecast" to "Open-Meteo")
    ),
    IntroPage(
        number = "02",
        title = "The whole trip in one place",
        body = "Today's stops, the map, the budget and the group all live inside the trip, so the next thing is always one tap away.",
        proof = listOf("Now" to "The stop you are at", "Next" to "What comes after it")
    ),
    IntroPage(
        number = "03",
        title = "Plan it together",
        body = "Share a view-only link, vote on the stops, log what you spend and see who owes whom.",
        proof = listOf("Each stop" to "Keep or skip", "Settle up" to "Who pays whom")
    )
)

@Composable
fun IntroScreen(onDone: () -> Unit) {
    val colors = TrippinTheme.colors
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pagerState.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tripp'in", style = TrippinType.Title, color = colors.ink)
            if (!last) {
                Text(
                    "Skip",
                    style = TrippinType.Label,
                    color = colors.inkMuted,
                    modifier = Modifier.clickableTab(onDone).padding(horizontal = 12.dp, vertical = 13.dp)
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(page.number, style = TrippinType.Display.copy(fontSize = 72.sp, lineHeight = 72.sp), color = colors.accent)
                Spacer(Modifier.height(16.dp))
                Text(page.title, style = TrippinType.Display.copy(fontSize = 40.sp, lineHeight = 44.sp), color = colors.ink)
                Spacer(Modifier.height(14.dp))
                Text(page.body, style = TrippinType.Body.copy(fontSize = 17.sp, lineHeight = 25.sp), color = colors.inkMuted)
                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    page.proof.forEach { (label, value) -> ProofRow(label, value) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp)
                .semantics { contentDescription = "Page ${pagerState.currentPage + 1} of ${pages.size}" },
            horizontalArrangement = Arrangement.Center
        ) {
            pages.indices.forEach { i ->
                val on = i == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (on) 22.dp else 8.dp, height = 8.dp)
                        .background(if (on) colors.ink else colors.hairline, CircleShape)
                )
            }
        }

        Box(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            TrippinButton(
                text = if (last) "Get started" else "Next",
                onClick = {
                    if (last) onDone() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            )
        }
    }
}

/** A small sample of the labels the app shows, so the promise has a concrete shape. */
@Composable
private fun ProofRow(label: String, value: String) {
    val colors = TrippinTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = TrippinType.Label, color = colors.ink, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.background(colors.panelAlt, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(value, style = TrippinType.Caption, color = colors.ink)
        }
    }
}
