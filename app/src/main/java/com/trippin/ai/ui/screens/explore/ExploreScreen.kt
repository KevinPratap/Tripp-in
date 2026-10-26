package com.trippin.ai.ui.screens.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Destination
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.seedBrush
import com.trippin.ai.ui.theme.Trip

/** Curated starting points. Only the name and coordinates are stated; nothing about ratings, prices or photos is made up. */
private val ideas = listOf(
    Destination("Lisbon", "Portugal", 38.7223, -9.1393),
    Destination("Kyoto", "Japan", 35.0116, 135.7681),
    Destination("Mexico City", "Mexico", 19.4326, -99.1332),
    Destination("Istanbul", "Turkey", 41.0082, 28.9784),
    Destination("Cape Town", "South Africa", -33.9249, 18.4241),
    Destination("Rome", "Italy", 41.9028, 12.4964),
    Destination("Bangkok", "Thailand", 13.7563, 100.5018),
    Destination("Reykjavik", "Iceland", 64.1466, -21.9426),
    Destination("Marrakech", "Morocco", 31.6295, -7.9811),
    Destination("Vancouver", "Canada", 49.2827, -123.1207),
)

/** A browse screen for people who do not know where to go yet. Tapping one starts a trip with it filled in. */
@Composable
fun ExploreScreen(container: AppContainer, onPick: (Destination) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize().background(Trip.Paper),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Kicker("Explore")
                Headline("Where to next?", accentLastChar = true)
                Text("Pick a city to start a trip with it filled in, then invite your group to swipe on places.", style = MaterialTheme.typography.bodyLarge, color = Trip.Muted)
            }
        }
        items(ideas, key = { it.name }) { d ->
            Box(
                Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(20.dp)).background(seedBrush(d.name)),
            ) {
                Panel(Modifier.align(Alignment.BottomStart).padding(10.dp), background = Color(0x66000000), onClick = { onPick(d) }, padding = 10.dp) {
                    Column {
                        Text(d.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(d.country.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = Color(0xCCFFFFFF))
                    }
                }
            }
        }
    }
}
