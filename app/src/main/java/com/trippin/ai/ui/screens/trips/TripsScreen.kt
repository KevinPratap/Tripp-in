package com.trippin.ai.ui.screens.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Trip
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.dateRange
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.theme.Trip as TripTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class TripsViewModel(container: AppContainer, uid: String) : ViewModel() {
    val trips: StateFlow<List<Trip>?> = container.tripRepository.myTrips(uid).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Every trip you're part of — solo or with friends — newest upcoming first. */
@Composable
fun TripsScreen(container: AppContainer, onOpen: (String) -> Unit, onCreate: () -> Unit, onJoin: () -> Unit) {
    val me = LocalSession.current
    val vm: TripsViewModel = viewModel(key = "trips-${me.uid}") { TripsViewModel(container, me.uid) }
    val trips by vm.trips.collectAsStateWithLifecycle()

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Headline("Trips") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallButton("New trip", onClick = onCreate)
                SmallButton("Join with code", onClick = onJoin)
            }
        }
        val list = trips
        when {
            list == null -> Unit
            list.isEmpty() -> item { EmptyState("No trips yet", "Plan one, or join a friend's with an invite code.") }
            else -> items(list, key = { it.id }) { trip -> TripRow(trip, onClick = { onOpen(trip.id) }) }
        }
    }
}

@Composable
private fun TripRow(trip: Trip, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(TripTheme.Card)
            .clickable(onClick = onClick).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Kicker(dateRange(trip.startDate, trip.days) + (if (trip.isPast()) " · past" else ""))
            if (trip.isGroup) Pill("GROUP", TripTheme.PaperDeep, TripTheme.Muted)
        }
        Text(trip.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(trip.destination?.label ?: "Destination not decided", style = MaterialTheme.typography.bodyMedium, color = TripTheme.Muted)
    }
}
