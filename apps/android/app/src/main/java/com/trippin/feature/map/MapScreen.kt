package com.trippin.feature.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.LoadingBlock
import com.trippin.core.design.MessageState
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.GeoPointDto
import java.util.Locale

@Composable
fun MapScreen(
    tripId: String,
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    val days = state.details?.itinerary?.days.orEmpty()
    var selectedDay by remember(days.size) { mutableIntStateOf(0) }
    val destinationName = state.details?.trip?.destination

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = "Map",
                subtitle = destinationName?.substringBefore(',')?.let { "$it, stop by stop" } ?: "Stop by stop",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBlock("Loading the route")
                days.isEmpty() -> MessageState(Icons.Default.Map, "No route yet", state.error ?: "This trip has no stops to map yet.")
                else -> {
                    val day = days.getOrNull(selectedDay) ?: days.first()
                    val stops = day.activities
                    val mappable = stops.filter { it.hasCoordinates() }
                    Column(Modifier.fillMaxSize()) {
                        if (days.size > 1) {
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                days.forEachIndexed { i, d ->
                                    TrippinChoiceChip("Day ${d.dayIndex}", i == selectedDay) { selectedDay = i }
                                }
                            }
                        }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Each pin carries the stop's own number from the list below, even when a stop
                            // without coordinates is missing from the sketch.
                            val numbered = stops.mapIndexedNotNull { i, a ->
                                a.place?.location?.takeIf { it.hasCoordinates() }?.let { (i + 1) to it }
                            }
                            if (numbered.isNotEmpty()) {
                                item {
                                    RouteSketch(
                                        points = numbered.map { it.second },
                                        labels = numbered.map { "${it.first}" },
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                            }
                            items(stops.size) { i -> RouteRow(i + 1, stops[i], last = i == stops.lastIndex) }
                        }
                        Box(Modifier.background(colors.paper).padding(16.dp)) {
                            TrippinButton(
                                text = if (mappable.size >= 2) "Open this day's route in Maps" else "Open in Maps",
                                onClick = { openRoute(context, mappable) },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = mappable.isNotEmpty(),
                                leadingIcon = Icons.Default.Directions
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteRow(index: Int, activity: ActivityDto, last: Boolean) {
    val colors = TrippinTheme.colors
    Row(Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
            Box(
                Modifier.size(28.dp).background(colors.accent, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$index", style = TrippinType.Label, color = colors.onAccent)
            }
            if (!last) {
                Box(Modifier.width(2.dp).height(40.dp).background(colors.line))
            }
        }
        Spacer(Modifier.width(12.dp))
        TrippinCard(modifier = Modifier.padding(bottom = 8.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(activity.title, style = TrippinType.Label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${activity.startTime.take(5)} to ${activity.endTime.take(5)}", style = TrippinType.Caption, color = colors.inkMuted)
                if (!activity.place?.location.hasCoordinates()) {
                    Text("Not on the sketch: no coordinates in the map data", style = TrippinType.Caption, color = colors.inkMuted)
                }
            }
        }
    }
}

private fun GeoPointDto?.hasCoordinates(): Boolean =
    this != null && (latitude != 0.0 || longitude != 0.0)

private fun ActivityDto.hasCoordinates(): Boolean = place?.location.hasCoordinates()

private fun openRoute(context: android.content.Context, stops: List<ActivityDto>) {
    val points = stops.mapNotNull { it.place?.location }.filter { it.latitude != 0.0 || it.longitude != 0.0 }
    if (points.isEmpty()) return
    val uri = if (points.size == 1) {
        val p = points.first()
        Uri.parse("https://www.google.com/maps/search/?api=1&query=${p.latitude},${p.longitude}")
    } else {
        val destination = points.last()
        val origin = points.first()
        val waypoints = points.drop(1).dropLast(1).joinToString("|") { "${it.latitude},${it.longitude}" }
        val base = StringBuilder("https://www.google.com/maps/dir/?api=1")
        base.append("&origin=${origin.latitude},${origin.longitude}")
        base.append("&destination=${destination.latitude},${destination.longitude}")
        if (waypoints.isNotEmpty()) base.append("&waypoints=").append(Uri.encode(waypoints))
        base.append("&travelmode=walking")
        Uri.parse(base.toString())
    }
    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
}
