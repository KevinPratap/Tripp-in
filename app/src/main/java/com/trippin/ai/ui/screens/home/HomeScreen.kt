package com.trippin.ai.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripStatus
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.dateRange
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.theme.Trip as TripTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(container: AppContainer, uid: String) : ViewModel() {
    val trips: StateFlow<List<Trip>?> = container.tripRepository.myTrips(uid).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Landing screen: what's happening now, what's coming up, and the two ways to start a trip. */
@Composable
fun HomeScreen(container: AppContainer, onCreate: () -> Unit, onJoin: () -> Unit, onOpen: (String, String?) -> Unit, onToday: (String) -> Unit) {
    val me = LocalSession.current
    val vm: HomeViewModel = viewModel(key = "home-${me.uid}") { HomeViewModel(container, me.uid) }
    val trips by vm.trips.collectAsStateWithLifecycle()
    val ongoing = trips?.firstOrNull { it.isOngoing() }
    val upcoming = trips?.filterNot { it.isPast() || it == ongoing }.orEmpty()
    val past = trips?.filter { it.isPast() }.orEmpty()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Kicker("Hey ${me.name.substringBefore(' ')}") }
        item { Headline("Where next?", accentLastChar = true) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SignalButton("Plan a trip", onClick = onCreate, container = TripTheme.Signal, content = TripTheme.Ink, modifier = Modifier.weight(1f))
            }
        }
        item { SmallButton("Have an invite code?", onClick = onJoin) }

        ongoing?.let { t ->
            item {
                Spacer(Modifier.height(4.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TripTheme.Ink).clickable { onToday(t.id) }.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Kicker("Happening now", color = TripTheme.Signal)
                    Text(t.title, style = MaterialTheme.typography.displaySmall, color = TripTheme.Paper)
                    Text("Tap to open today", style = MaterialTheme.typography.titleMedium, color = TripTheme.OnInkMuted)
                }
            }
        }

        if (trips != null) {
            item {
                Spacer(Modifier.height(6.dp))
                Kicker("Your trips · ${upcoming.size}", color = TripTheme.Ink)
            }
            if (upcoming.isEmpty()) {
                item { EmptyState("No trips planned", "Start one solo, or with friends — vote together, and the AI plans around everyone.") }
            }
        }
        items(upcoming, key = { it.id }) { trip ->
            TripCard(trip, index = upcoming.indexOf(trip), onClick = { onOpen(trip.id, null) })
        }

        if (past.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Kicker("Past trips · ${past.size}", color = TripTheme.Muted)
            }
            items(past.take(5), key = { "past-" + it.id }) { trip ->
                TripCard(trip, index = trip.hashCode(), onClick = { onOpen(trip.id, null) }, muted = true)
            }
        }
    }
}

@Composable
private fun TripCard(trip: Trip, index: Int, onClick: () -> Unit, muted: Boolean = false) {
    val (bg, fg) = when {
        muted -> TripTheme.PaperDeep to TripTheme.Muted
        index % 2 == 0 -> TripTheme.Signal to TripTheme.Ink
        else -> TripTheme.Cobalt to Color.White
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).clickable(onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker(dateRange(trip.startDate, trip.days), color = fg, modifier = Modifier.weight(1f))
            if (trip.isGroup) Pill("GROUP", if (muted) TripTheme.Muted else TripTheme.Ink, if (muted) TripTheme.Paper else TripTheme.Paper)
            statusPillText(trip.status)?.let { Pill(it, if (muted) TripTheme.Muted else TripTheme.Ink, TripTheme.Paper) }
        }
        Text(
            (trip.destination?.label ?: trip.title).uppercase(), style = MaterialTheme.typography.displaySmall, color = fg,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(trip.title, style = MaterialTheme.typography.titleMedium, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun statusPillText(s: TripStatus): String? = when (s) {
    TripStatus.DECIDING_DESTINATION -> "VOTING"
    TripStatus.COLLECTING_IDEAS -> "PLANNING"
    TripStatus.PLAN_READY -> "REVIEW"
    TripStatus.CONFIRMED -> null
}
