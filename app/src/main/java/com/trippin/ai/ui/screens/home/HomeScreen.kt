package com.trippin.ai.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.trippin.ai.data.local.TripEntity
import com.trippin.ai.data.repository.TripRepository
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.theme.Trip
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class HomeViewModel(private val repo: TripRepository) : ViewModel() {
    val trips: StateFlow<List<TripEntity>?> = repo.trips().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}

@Composable
fun HomeScreen(container: AppContainer, onPlan: () -> Unit, onOpen: (Long) -> Unit) {
    val vm: HomeViewModel = viewModel { HomeViewModel(container.tripRepository) }
    val trips by vm.trips.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<TripEntity?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Kicker("Trippin' AI") }
        item { Headline("Where next?", accentLastChar = true) }
        item { SignalButton("Plan a trip", onClick = onPlan, container = Trip.Signal, content = Trip.Ink) }
        item {
            Spacer(Modifier.height(6.dp))
            Kicker("Your trips · ${trips?.size ?: 0}", color = Trip.Ink)
        }
        val list = trips
        if (list != null && list.isEmpty()) {
            item {
                Text(
                    "No trips yet. Plan one — the genetic algorithm will order the stops and the Bayesian network will flag the risky ones.",
                    style = MaterialTheme.typography.bodyLarge, color = Trip.Muted,
                )
            }
        }
        items(list.orEmpty(), key = { it.id }) { trip ->
            TripCard(trip, index = list.orEmpty().indexOf(trip), onClick = { onOpen(trip.id) }, onDelete = { confirmDelete = trip })
        }
    }

    confirmDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${t.destination}?") },
            text = { Text("This removes the plan and its stops from this phone.") },
            confirmButton = { TextButton(onClick = { vm.delete(t.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("d MMM")

@Composable
private fun TripCard(trip: TripEntity, index: Int, onClick: () -> Unit, onDelete: () -> Unit) {
    // Alternate loud colour blocks: orange with ink text, cobalt with white text.
    val (bg, fg) = if (index % 2 == 0) Trip.Signal to Trip.Ink else Trip.Cobalt to Color.White
    val start = LocalDate.parse(trip.startDate)
    val end = start.plusDays((trip.days - 1).toLong())
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(bg).clickable(onClick = onClick).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Kicker("${start.format(dateFmt)} – ${end.format(dateFmt)} · ${trip.days} days", color = fg, modifier = Modifier.weight(1f))
            if (trip.verified) Pill("VERIFIED", Trip.Ink, Trip.Paper) else Pill("DRAFT", Color.White, Trip.Ink)
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${trip.destination}", tint = fg) }
        }
        Text(
            trip.destination.uppercase(), style = MaterialTheme.typography.displayLarge, color = fg,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Box {
            Text(
                "${trip.travellers} travellers · ${trip.pace.lowercase()} pace · GA saved ${trip.gaImprovementPercent.toInt()}% vs. input order" +
                    if (trip.usedDemoData) " · offline demo data" else "",
                style = MaterialTheme.typography.titleMedium, color = fg,
            )
        }
    }
}
