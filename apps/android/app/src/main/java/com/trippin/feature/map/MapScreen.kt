package com.trippin.feature.map

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.LoadingBlock
import com.trippin.core.design.MessageState
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinSegmentedTabs
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.map.MapLeg
import com.trippin.core.map.MapPlace
import com.trippin.core.map.MapStop
import com.trippin.core.map.PlaceKind
import com.trippin.core.map.TrippinMap
import com.trippin.core.map.distanceLabel
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.ExplorePlaceDto
import com.trippin.core.network.GeoPointDto

@Composable
fun MapScreen(
    tripId: String,
    onBack: () -> Unit,
    onExploreAround: (GeoPointDto, String) -> Unit = { _, _ -> },
    viewModel: MapViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val days = state.days
    val destinationName = state.details?.trip?.destination

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = "Map",
                subtitle = destinationName?.substringBefore(',')?.let { "$it, on foot" } ?: "On foot",
                onBack = onBack
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBlock("Loading the route")
                days.isEmpty() -> MessageState(Icons.Default.Map, "No route yet", state.error ?: "This trip has no stops to map yet.")
                else -> DayMap(
                    state = state,
                    onSelectDay = viewModel::selectDay,
                    onRetry = viewModel::retryRoute,
                    onOpenRoute = { stops -> openRoute(context, stops) },
                    onExploreAround = onExploreAround
                )
            }
        }
    }
}

@Composable
private fun DayMap(
    state: MapUiState,
    onSelectDay: (Int) -> Unit,
    onRetry: () -> Unit,
    onOpenRoute: (List<GeoPointDto>) -> Unit,
    onExploreAround: (GeoPointDto, String) -> Unit
) {
    val colors = TrippinTheme.colors
    val days = state.days
    val day = days.getOrNull(state.selectedDay) ?: days.first()
    val activities = day.activities
    // Each pin carries the stop's own number in the day, even when a stop without coordinates is skipped.
    val numbered = activities.mapIndexedNotNull { i, a -> a.point()?.let { Triple(i + 1, a, it) } }
    val route = state.route
    var selectedId by remember(state.selectedDay) { mutableStateOf<String?>(null) }
    var kindFilter by remember(state.selectedDay) { mutableStateOf<PlaceKind?>(null) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val along = route?.along.orEmpty().filter { kindFilter == null || PlaceKind.of(it.kind) == kindFilter }
    val selected = along.firstOrNull { it.id == selectedId }

    val legs = route?.legs.orEmpty().map { leg ->
        MapLeg(leg.coordinates.mapNotNull { c -> if (c.size >= 2) GeoPointDto(c[0], c[1]) else null }, straight = leg.source != "OSRM")
    }.ifEmpty {
        numbered.zipWithNext().map { (a, b) -> MapLeg(listOf(a.third, b.third), straight = true) }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            TrippinMap(
                modifier = Modifier.fillMaxSize(),
                stops = numbered.map { MapStop(it.third, "${it.first}") },
                legs = legs,
                places = along.map { MapPlace(it.id, GeoPointDto(it.latitude, it.longitude), PlaceKind.of(it.kind).color) },
                selectedPlaceId = selectedId,
                focus = selected?.let { GeoPointDto(it.latitude, it.longitude) },
                fitKey = "${state.selectedDay}:${numbered.size}:${route != null}",
                onPlaceTap = { id -> selectedId = id; if (id != null) tab = 0 }
            )
            if (days.size > 1) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    days.forEachIndexed { i, d ->
                        Box(Modifier.shadow(3.dp, RoundedCornerShape(50))) {
                            TrippinChoiceChip("Day ${d.dayIndex}", i == state.selectedDay) { onSelectDay(i) }
                        }
                    }
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .shadow(12.dp, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .background(colors.paper, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .padding(top = 14.dp, bottom = 12.dp)
        ) {
            WalkSummary(day.dayIndex, numbered.size, route, state.routeLoading, state.routeFailed, onRetry)
            Spacer(Modifier.height(12.dp))
            TrippinSegmentedTabs(
                options = listOf("On the way" + (route?.along?.size?.takeIf { it > 0 }?.let { " ($it)" } ?: ""), "Stops"),
                selectedIndex = tab,
                onOptionSelected = { tab = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))
            AnimatedContent(targetState = tab, label = "map panel") { t ->
                if (t == 0) {
                    AlongPanel(
                        all = route?.along.orEmpty(),
                        shown = along,
                        loading = state.routeLoading,
                        kindFilter = kindFilter,
                        onKind = { kindFilter = if (kindFilter == it) null else it; selectedId = null },
                        selectedId = selectedId,
                        onSelect = { selectedId = if (selectedId == it) null else it }
                    )
                } else {
                    StopsPanel(numbered.map { it.first to it.second }, route?.legs.orEmpty().map { it.durationMinutes }) { a ->
                        a.point()?.let { onExploreAround(it, a.title) }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            TrippinButton(
                text = if (numbered.size >= 2) "Walk this day in Maps" else "Open in Maps",
                onClick = { onOpenRoute(numbered.map { it.third }) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                enabled = numbered.isNotEmpty(),
                leadingIcon = Icons.Default.Directions
            )
        }
    }
}

@Composable
private fun WalkSummary(
    dayIndex: Int,
    stops: Int,
    route: com.trippin.core.network.ExploreRouteDto?,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit
) {
    val colors = TrippinTheme.colors
    val legs = route?.legs.orEmpty()
    val routed = legs.isNotEmpty() && legs.all { it.source == "OSRM" }
    val meters = legs.sumOf { it.distanceMeters }
    val minutes = legs.mapNotNull { it.durationMinutes }.sum()
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(40.dp).background(colors.accent.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = colors.accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("DAY $dayIndex · $stops STOPS", style = TrippinType.Eyebrow, color = colors.inkMuted)
            val line = when {
                loading -> "Tracing the streets between stops"
                failed -> "Could not load the walking route"
                routed -> "${distanceLabel(meters)} on foot, about $minutes min of walking"
                legs.isNotEmpty() -> "${distanceLabel(meters)} as the crow flies; street routes were unavailable"
                stops < 2 -> "One stop on the map today"
                else -> "Straight lines between stops"
            }
            Text(line, style = TrippinType.Heading, color = colors.ink, maxLines = 2)
        }
        if (failed) {
            Text(
                "Retry",
                style = TrippinType.Label,
                color = colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRetry).padding(12.dp)
            )
        }
    }
}

@Composable
private fun AlongPanel(
    all: List<ExplorePlaceDto>,
    shown: List<ExplorePlaceDto>,
    loading: Boolean,
    kindFilter: PlaceKind?,
    onKind: (PlaceKind) -> Unit,
    selectedId: String?,
    onSelect: (String) -> Unit
) {
    val colors = TrippinTheme.colors
    Column(Modifier.heightIn(min = 150.dp)) {
        val kinds = all.map { PlaceKind.of(it.kind) }.distinct().sortedBy { it.ordinal }
        if (kinds.size > 1) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                kinds.forEach { k ->
                    TrippinChoiceChip(k.plural, kindFilter == k, leadingIcon = k.icon) { onKind(k) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        when {
            loading && all.isEmpty() -> Text(
                "Finding cafes, bakeries and sights along the way",
                style = TrippinType.Body,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            all.isEmpty() -> Text(
                "Nothing named on OpenStreetMap right beside these walks.",
                style = TrippinType.Body,
                color = colors.inkMuted,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            )
            else -> {
                val listState = rememberLazyListState()
                LaunchedEffect(selectedId) {
                    val i = shown.indexOfFirst { it.id == selectedId }
                    if (i >= 0) listState.animateScrollToItem(i)
                }
                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(shown, key = { it.id }) { p ->
                        PlaceCard(p, selected = p.id == selectedId, subtitle = alongLine(p)) { onSelect(p.id) }
                    }
                }
            }
        }
    }
}

private fun alongLine(p: ExplorePlaceDto): String {
    val leg = p.legIndex?.let { "Between stops ${it + 1} and ${it + 2}" }
    val off = p.offRouteMeters?.let { if (it <= 15) "on the route" else "${distanceLabel(it)} off the route" }
    return listOfNotNull(leg, off).joinToString(", ")
}

/** A place from OpenStreetMap as a card: its kind in colour, its name, where it is, its listed hours. */
@Composable
internal fun PlaceCard(place: ExplorePlaceDto, selected: Boolean, subtitle: String, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    val kind = PlaceKind.of(place.kind)
    Column(
        Modifier
            .width(236.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.panelRaised else colors.panel)
            .border(if (selected) 2.5.dp else 1.dp, if (selected) colors.ink else colors.hairline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(kind.color, CircleShape), contentAlignment = Alignment.Center) {
                Icon(kind.icon, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                (place.cuisine?.replaceFirstChar { it.uppercase() } ?: kind.label).uppercase(),
                style = TrippinType.Eyebrow,
                color = kind.color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(place.name, style = TrippinType.Heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(subtitle, style = TrippinType.Caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
            place.openingHours?.let { "Hours on OpenStreetMap: $it" } ?: "No hours listed",
            style = TrippinType.Caption,
            color = colors.inkMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StopsPanel(stops: List<Pair<Int, ActivityDto>>, legMinutes: List<Int?>, onExplore: (ActivityDto) -> Unit) {
    val colors = TrippinTheme.colors
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.heightIn(min = 150.dp)
    ) {
        items(stops.size) { i ->
            val (number, a) = stops[i]
            Column(
                Modifier
                    .width(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.panel)
                    .border(1.dp, colors.hairline, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(28.dp).background(colors.accent, CircleShape), contentAlignment = Alignment.Center) {
                        Text("$number", style = TrippinType.Label, color = colors.onAccent)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("${a.startTime.take(5)} to ${a.endTime.take(5)}", style = TrippinType.Caption, color = colors.inkMuted)
                }
                Spacer(Modifier.height(8.dp))
                Text(a.title, style = TrippinType.Heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val walkIn = if (i > 0) legMinutes.getOrNull(i - 1) else null
                Text(
                    walkIn?.let { "$it min walk from stop ${stops[i - 1].first}" } ?: if (i == 0) "First stop of the day" else "Walk time unknown",
                    style = TrippinType.Caption,
                    color = colors.inkMuted
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "What else is around",
                    style = TrippinType.Label,
                    color = colors.accent,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onExplore(a) }.padding(vertical = 6.dp)
                )
            }
        }
    }
}

private fun openRoute(context: Context, points: List<GeoPointDto>) {
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
