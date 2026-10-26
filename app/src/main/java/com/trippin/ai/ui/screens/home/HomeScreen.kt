package com.trippin.ai.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripStatus
import com.trippin.ai.ui.components.Avatar
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.StoryRing
import com.trippin.ai.ui.components.dateRange
import com.trippin.ai.ui.components.seedBrush
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.theme.Trip as TripTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.LocalTime

class HomeViewModel(container: AppContainer, uid: String) : ViewModel() {
    val trips: StateFlow<List<Trip>?> = container.tripRepository.myTrips(uid).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Landing screen. Top to bottom: greeting, a search-style entry to plan a trip, quick actions,
 * story rings for your trips, what is happening now, trips open for voting, then everything else.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    onCreate: () -> Unit,
    onJoin: () -> Unit,
    onOpen: (String, String?) -> Unit,
    onToday: (String) -> Unit,
    onSwipe: (String) -> Unit,
    onSwipeHub: () -> Unit,
) {
    val me = LocalSession.current
    val vm: HomeViewModel = viewModel(key = "home-${me.uid}") { HomeViewModel(container, me.uid) }
    val trips by vm.trips.collectAsStateWithLifecycle()
    val ongoing = trips?.firstOrNull { it.isOngoing() }
    val current = trips?.filterNot { it.isPast() }.orEmpty()
    val upcoming = current.filter { it != ongoing }
    val voting = current.filter { it.status == TripStatus.DECIDING_DESTINATION || it.status == TripStatus.COLLECTING_IDEAS }
    val past = trips?.filter { it.isPast() }.orEmpty()
    val hour = LocalTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning"
        hour < 17 -> "Good afternoon"
        else -> "Good evening"
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Greeting
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting, style = MaterialTheme.typography.labelMedium, color = TripTheme.Muted)
                    Text("Where next, ${me.name.substringBefore(' ')}?", style = MaterialTheme.typography.displaySmall, maxLines = 2)
                }
                Avatar(me.name, me.uid, 46.dp)
            }
        }

        // Search-style entry point (opens the plan-a-trip flow)
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                    .shadow(if (TripTheme.dark) 0.dp else 3.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1A14141A), spotColor = Color(0x2614141A))
                    .clip(RoundedCornerShape(18.dp)).background(TripTheme.Card)
                    .border(1.dp, TripTheme.Line, RoundedCornerShape(18.dp))
                    .clickable(onClick = onCreate).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = TripTheme.Muted)
                Text("Where do you want to go?", style = MaterialTheme.typography.bodyLarge, color = TripTheme.Muted)
            }
        }

        // Quick actions
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item { ActionChip(Icons.Filled.Add, "Plan a trip", filled = true, onClick = onCreate) }
                item { ActionChip(Icons.Filled.Style, "Swipe with friends", onClick = onSwipeHub) }
                item { ActionChip(null, "Join with code", onClick = onJoin) }
            }
        }

        // Story rings: one per trip. The ring is the brand gradient while the trip is still being planned.
        if (current.isNotEmpty()) {
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(current, key = { "story-" + it.id }) { t ->
                        StoryRing(
                            label = (t.destination?.name ?: t.title),
                            seed = t.id,
                            active = t.status != TripStatus.CONFIRMED,
                            onClick = { onOpen(t.id, null) },
                        )
                    }
                }
            }
        }

        // Happening now
        ongoing?.let { t ->
            item {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp).clip(RoundedCornerShape(28.dp)).background(TripTheme.Brand)
                        .clickable { onToday(t.id) }.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("Happening now", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
                    Text(t.destination?.label ?: t.title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Pill("Open today", Color.White, TripTheme.Ink, Modifier.align(Alignment.Start))
                }
            }
        }

        // Open for voting (Blinkit-style rail of quick actions)
        if (voting.isNotEmpty()) {
            item { SectionTitle("Open for voting", Modifier.padding(horizontal = 20.dp)) }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(voting, key = { "vote-" + it.id }) { t -> VoteCard(t, onClick = { onSwipe(t.id) }) }
                }
            }
        }

        // Upcoming
        if (trips != null) {
            item { SectionTitle("Your trips", Modifier.padding(horizontal = 20.dp)) }
            if (upcoming.isEmpty()) {
                item {
                    EmptyState(
                        "No trips planned",
                        "Start one solo, or with friends. Vote together and the plan is built around everyone.",
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            }
        }
        items(upcoming, key = { it.id }) { trip ->
            TripCard(trip, onClick = { onOpen(trip.id, null) }, modifier = Modifier.padding(horizontal = 20.dp))
        }

        if (past.isNotEmpty()) {
            item { SectionTitle("Past trips", Modifier.padding(horizontal = 20.dp)) }
            items(past.take(5), key = { "past-" + it.id }) { trip ->
                PastRow(trip, onClick = { onOpen(trip.id, null) }, modifier = Modifier.padding(horizontal = 20.dp))
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector?, text: String, onClick: () -> Unit, filled: Boolean = false) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        Modifier.clip(shape)
            .then(if (filled) Modifier.background(TripTheme.Brand) else Modifier.background(TripTheme.Card).border(1.dp, TripTheme.Line, shape))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = if (filled) Color.White else TripTheme.Ink, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = if (filled) Color.White else TripTheme.Ink, maxLines = 1)
    }
}

@Composable
private fun VoteCard(trip: Trip, onClick: () -> Unit) {
    Column(
        Modifier.width(250.dp).clip(RoundedCornerShape(24.dp)).background(seedBrush(trip.id)).clickable(onClick = onClick).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(statusWord(trip.status), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.85f))
        Text(trip.destination?.label ?: trip.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Pill("Swipe now", Color.White, TripTheme.Ink, Modifier.align(Alignment.Start))
    }
}

@Composable
private fun TripCard(trip: Trip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().height(168.dp).clip(shape).background(seedBrush(trip.id)).clickable(onClick = onClick)) {
        Row(Modifier.align(Alignment.TopStart).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(dateRange(trip.startDate, trip.days), Color.White.copy(alpha = 0.22f), Color.White)
            if (trip.isGroup) Pill("Group", Color.White.copy(alpha = 0.22f), Color.White)
        }
        statusPillText(trip.status)?.let { Pill(it, Color.White, TripTheme.Ink, Modifier.align(Alignment.TopEnd).padding(16.dp)) }
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(trip.destination?.label ?: trip.title, style = MaterialTheme.typography.displaySmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (trip.destination != null) Text(trip.title, style = MaterialTheme.typography.titleMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun PastRow(trip: Trip, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(TripTheme.Card).border(1.dp, TripTheme.Line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(seedBrush(trip.id)))
        Column(Modifier.weight(1f)) {
            Text(trip.destination?.label ?: trip.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateRange(trip.startDate, trip.days), style = MaterialTheme.typography.bodyMedium, color = TripTheme.Muted)
        }
    }
}

private fun statusWord(s: TripStatus): String = when (s) {
    TripStatus.DECIDING_DESTINATION -> "Choosing where to go"
    TripStatus.COLLECTING_IDEAS -> "Collecting ideas"
    TripStatus.PLAN_READY -> "Plan ready for review"
    TripStatus.CONFIRMED -> "Plan confirmed"
}

private fun statusPillText(s: TripStatus): String? = when (s) {
    TripStatus.DECIDING_DESTINATION -> "Voting"
    TripStatus.COLLECTING_IDEAS -> "Planning"
    TripStatus.PLAN_READY -> "Review"
    TripStatus.CONFIRMED -> null
}
