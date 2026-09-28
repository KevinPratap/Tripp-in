package com.trippin.feature.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.CardListSkeleton
import com.trippin.core.design.HeaderCream
import com.trippin.core.design.HeaderCreamMuted
import com.trippin.core.design.PhotoCredit
import com.trippin.core.design.PillTone
import com.trippin.core.design.PlaceBackdrop
import com.trippin.core.design.PlaceThumb
import com.trippin.core.design.SectionLabel
import com.trippin.core.design.StatusPill
import com.trippin.core.design.SurfaceTier
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinOutlineButton
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.clickableTab
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.SavedTripSummaryDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val headerDate = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.US)
private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenTrip: (String) -> Unit,
    onOpenToday: (String) -> Unit,
    onOpenPlan: (String) -> Unit,
    onStartPlanner: () -> Unit,
    onStartPlannerFor: (String) -> Unit = { onStartPlanner() },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize().background(colors.paper)
    ) {
        when {
            state.loading -> CardListSkeleton(label = "Loading your trips", modifier = Modifier.statusBarsPadding())
            !state.hasTrips -> FirstTrip(onStartPlanner, onStartPlannerFor)
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                item { Greeting(state) }

                val featured = state.featured
                if (featured != null) {
                    item {
                        FeaturedCard(
                            featured = featured,
                            state = state,
                            onOpenTrip = { onOpenTrip(featured.trip.id) },
                            onOpenToday = { onOpenToday(featured.trip.id) },
                            onOpenPlan = { onOpenPlan(featured.trip.id) }
                        )
                    }
                }

                if (state.upcoming.isNotEmpty()) {
                    item { SectionLabel("Coming up", Modifier.padding(horizontal = 4.dp)) }
                    items(state.upcoming, key = { it.id }) { trip ->
                        UpcomingRow(trip = trip, today = state.now.toLocalDate(), onClick = { onOpenTrip(trip.id) })
                    }
                }

                item {
                    if (featured == null) {
                        TrippinButton(text = "Plan a new trip", onClick = onStartPlanner, leadingIcon = Icons.Default.Add)
                    } else {
                        TrippinOutlineButton(
                            text = "Plan a new trip",
                            onClick = onStartPlanner,
                            leadingIcon = Icons.Default.Add,
                            contentColor = colors.ink,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Greeting(state: HomeUiState) {
    val colors = TrippinTheme.colors
    val hour = state.now.hour
    val greeting = when {
        hour < 5 -> "Good evening"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
    Column(
        Modifier.statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(state.now.format(headerDate).uppercase(), style = TrippinType.Eyebrow, color = colors.inkMuted)
        Text(greeting, style = TrippinType.Display, color = colors.ink)
        if (state.error != null) {
            Text("Offline. Showing what your phone has.", style = TrippinType.Caption, color = colors.warn)
        }
    }
}

@Composable
private fun FeaturedCard(
    featured: Featured,
    state: HomeUiState,
    onOpenTrip: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenPlan: () -> Unit
) {
    val colors = TrippinTheme.colors
    val trip = featured.trip
    val eyebrow = when (featured) {
        is Featured.Live -> "Live now · Day ${featured.dayNumber} of ${featured.window.dayCount}"
        is Featured.Upcoming -> when (featured.daysUntil) {
            1L -> "Tomorrow"
            else -> "In ${featured.daysUntil} days"
        } + " · " + "${featured.window.start.format(shortDate)} to ${featured.window.end.format(shortDate)}"
    }

    TrippinCard(tier = SurfaceTier.RAISED) {
        Column {
            PlaceBackdrop(
                photoUrl = trip.heroImageUrl,
                contentDescription = "Photo of ${trip.destinationName}",
                modifier = Modifier.fillMaxWidth().height(190.dp)
            ) {
                Column(
                    Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (featured is Featured.Live) {
                            Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(eyebrow.uppercase(), style = TrippinType.Eyebrow, color = HeaderCreamMuted)
                    }
                    Text(
                        cityName(trip.destinationName),
                        style = TrippinType.Display.copy(fontSize = 46.sp, lineHeight = 48.sp),
                        color = HeaderCream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PhotoCredit(trip.heroImageUrl)
            }

            Column(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (featured) {
                    is Featured.Live -> LiveBody(state, onOpenPlan)
                    is Featured.Upcoming -> Text(
                        text = listOfNotNull(
                            trip.dayCount.takeIf { it > 0 }?.let { "$it days" },
                            trip.stopCount.takeIf { it > 0 }?.let { "$it stops planned" }
                        ).joinToString(" · ").ifBlank { "The plan is not built yet." },
                        style = TrippinType.Body,
                        color = colors.inkMuted
                    )
                }
                if (featured is Featured.Live) {
                    TrippinButton(text = "Open today", onClick = onOpenToday)
                } else {
                    TrippinButton(text = "Open the trip", onClick = onOpenTrip)
                }
                if (featured is Featured.Live) {
                    Text(
                        "Trip overview",
                        style = TrippinType.Label,
                        color = colors.ink,
                        modifier = Modifier.align(Alignment.CenterHorizontally).clickableTab(onOpenTrip).padding(horizontal = 16.dp, vertical = 13.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveBody(state: HomeUiState, onOpenPlan: () -> Unit) {
    val colors = TrippinTheme.colors
    val now = state.nowStop
    val next = state.nextStop
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (now != null) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("NOW", style = TrippinType.Eyebrow, color = colors.accent)
                Text(now.title, style = TrippinType.Title, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("Until ${now.endTime.take(5)}", style = TrippinType.Body, color = colors.inkMuted)
            }
        }
        if (next != null) {
            NextRow(next, onOpenPlan)
        }
        if (now == null && next == null) {
            Text(
                if (state.todayKnown) "Nothing else planned for today." else "Loading today's stops.",
                style = TrippinType.Body,
                color = colors.inkMuted
            )
        }
    }
}

@Composable
private fun NextRow(next: ActivityDto, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    TrippinCard(background = colors.paper, onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(next.startTime.take(5), style = TrippinType.Title, color = colors.ink, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Text("Next: ${next.title}", style = TrippinType.Label, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (next.travelTimeFromPreviousMinutes > 0) {
                    Text(
                        "${next.travelTimeFromPreviousMinutes} min from the previous stop",
                        style = TrippinType.Caption,
                        color = colors.inkMuted
                    )
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.inkMuted)
        }
    }
}

@Composable
private fun UpcomingRow(trip: SavedTripSummaryDto, today: LocalDate, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    val window = tripWindow(trip)
    val daysUntil = window?.let { ChronoUnit.DAYS.between(today, it.start) }
    TrippinCard(onClick = onClick) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PlaceThumb(name = trip.destinationName, photoUrl = trip.heroImageUrl)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(cityName(trip.destinationName), style = TrippinType.Heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                window?.let {
                    Text(
                        "${it.start.format(shortDate)} to ${it.end.format(shortDate)} · ${it.dayCount} days",
                        style = TrippinType.Caption,
                        color = colors.inkMuted
                    )
                }
            }
            if (daysUntil != null && daysUntil > 0) {
                StatusPill(text = if (daysUntil == 1L) "Tomorrow" else "In $daysUntil days", tone = PillTone.NEUTRAL)
            }
        }
    }
}

/** "Lisbon, Portugal" reads as "Lisbon" where space is tight. */
internal fun cityName(destination: String): String =
    destination.substringBefore(',').trim().ifBlank { destination.ifBlank { "Untitled trip" } }

/** Cities to start from. Only a shortcut into the planner; nothing about them is claimed here. */
private val starterCities = listOf("Lisbon", "Kyoto", "Mexico City", "Rome", "Seoul", "Istanbul")

/** What someone with no trips yet sees: what the app does, in three steps, and a way to start. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FirstTrip(onStartPlanner: () -> Unit, onStartFor: (String) -> Unit) {
    val colors = TrippinTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text("TRIPP'IN", style = TrippinType.Eyebrow, color = colors.accent)
            Spacer(Modifier.height(8.dp))
            Text("Where to first?", style = TrippinType.Display.copy(fontSize = 48.sp, lineHeight = 50.sp), color = colors.ink)
            Spacer(Modifier.height(10.dp))
            Text(
                "Say where and when. Tripp'in builds the days and checks every stop before you go.",
                style = TrippinType.Body,
                color = colors.inkMuted
            )
            Spacer(Modifier.height(28.dp))
            SectionLabel("How it works")
            Spacer(Modifier.height(12.dp))
            TrippinCard(tier = SurfaceTier.FLAT, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Step("01", "Say where and when", "A city, your dates, who is coming and what you are into.")
                    Step("02", "Every stop is checked", "Opening hours against the day you visit, travel times between each stop.")
                    Step("03", "Travel from Today", "On the day, the app opens on what is happening now and what is next.")
                }
            }
            Spacer(Modifier.height(28.dp))
            SectionLabel("Start with a city")
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                starterCities.forEach { city ->
                    com.trippin.core.design.TrippinChoiceChip(text = city, selected = false, onClick = { onStartFor(city) })
                }
            }
        }
        TrippinButton(
            text = "Plan a trip",
            onClick = onStartPlanner,
            leadingIcon = Icons.Default.Add,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        )
    }
}

@Composable
private fun Step(number: String, title: String, body: String) {
    val colors = TrippinTheme.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(number, style = TrippinType.Numeric.copy(fontSize = 24.sp), color = colors.accent, modifier = Modifier.width(44.dp))
        Column {
            Text(title, style = TrippinType.Heading, color = colors.ink)
            Spacer(Modifier.height(2.dp))
            Text(body, style = TrippinType.Body, color = colors.inkMuted)
        }
    }
}
