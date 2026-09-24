package com.trippin.ai.ui.screens.plan

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.export.CalendarExport
import com.trippin.ai.data.local.GenerationEntity
import com.trippin.ai.data.local.StopEntity
import com.trippin.ai.data.local.TripWithStops
import com.trippin.ai.data.repository.TripRepository
import com.trippin.ai.ui.components.EvolutionChart
import com.trippin.ai.ui.components.GhostButton
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.NumberBadge
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.ProbabilityBar
import com.trippin.ai.ui.components.RiskPill
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.model.asClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalCoroutinesApi::class)
class PlanViewModel(repo: TripRepository, tripId: Long) : ViewModel() {
    val day = MutableStateFlow(0)
    val trip: StateFlow<TripWithStops?> = repo.trip(tripId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val generations: StateFlow<List<GenerationEntity>> = day
        .flatMapLatest { repo.generations(tripId, it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(container: AppContainer, tripId: Long, onBack: () -> Unit, onToday: () -> Unit) {
    val vm: PlanViewModel = viewModel(key = "plan-$tripId") { PlanViewModel(container.tripRepository, tripId) }
    val data by vm.trip.collectAsStateWithLifecycle()
    val day by vm.day.collectAsStateWithLifecycle()
    val gens by vm.generations.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var why by remember { mutableStateOf<StopEntity?>(null) }

    val t = data ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading the plan…") }
        return
    }
    val trip = t.trip
    val stops = t.stops.filter { it.dayIndex == day }.sortedBy { it.orderInDay }
    val start = LocalDate.parse(trip.startDate)

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(
                Modifier.fillMaxWidth().background(Trip.Signal).statusBarsPadding().padding(start = 8.dp, end = 8.dp, bottom = 22.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Trip.Ink) }
                    Row {
                        IconButton(onClick = { CalendarExport.share(context, t) }) { Icon(Icons.Filled.CalendarMonth, contentDescription = "Export to calendar", tint = Trip.Ink) }
                        IconButton(onClick = { share(context, t) }) { Icon(Icons.Filled.Share, contentDescription = "Share plan", tint = Trip.Ink) }
                    }
                }
                Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(trip.destination.uppercase(), style = MaterialTheme.typography.displayLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Kicker(
                            "${start.format(DateTimeFormatter.ofPattern("d MMM"))} · ${trip.days}D · ${trip.travellers} PPL",
                            color = Trip.Ink, modifier = Modifier.weight(1f),
                        )
                        if (trip.verified) Pill("VERIFIED", Trip.Ink, Trip.Paper) else Pill("DRAFT · CHECK HOURS", Trip.Paper, Trip.Ink)
                    }
                    Text(
                        "${"%.1f".format(trip.totalKm)} km in total · route ${trip.gaImprovementPercent.toInt()}% cheaper than the unsorted order · rain ${(trip.rainProbability * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (trip.usedDemoData) Text("Offline: showing the built-in Mumbai demo city.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        item {
            LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0 until trip.days).toList()) { d ->
                    val on = d == day
                    Column(
                        Modifier.clip(RoundedCornerShape(18.dp))
                            .background(if (on) Trip.Ink else Trip.Card)
                            .border(2.dp, Trip.Ink, RoundedCornerShape(18.dp))
                            .selectable(selected = on, role = Role.Tab) { vm.day.value = d }
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    ) {
                        Text("DAY ${d + 1}", style = MaterialTheme.typography.headlineMedium, color = if (on) Trip.Paper else Trip.Ink)
                        Text(start.plusDays(d.toLong()).format(DateTimeFormatter.ofPattern("EEE d")), style = MaterialTheme.typography.labelSmall, color = if (on) Trip.OnInkMuted else Trip.Muted)
                    }
                }
            }
        }

        stops.forEachIndexed { i, s ->
            if (s.travelMinutes > 0) {
                item(key = "leg-${s.id}") {
                    Row(Modifier.padding(start = 94.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(2.dp).height(24.dp).background(Trip.Ink))
                        Spacer(Modifier.width(10.dp))
                        Kicker("${s.travelMinutes} min travel")
                    }
                }
            }
            item(key = s.id) {
                StopRow(i + 1, s, onWhy = { why = s }, onGo = { openMaps(context, s) })
            }
        }

        item {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Kicker("How the route evolved · Day ${day + 1}", color = Trip.Ink)
                if (gens.size > 1) {
                    EvolutionChart(gens.map { it.bestCost }, gens.map { it.averageCost })
                    Text(
                        "Genetic algorithm: ${gens.size - 1} generations. Cost fell from ${"%.2f".format(gens.first().bestCost)} to ${"%.2f".format(gens.last().bestCost)}.",
                        style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
                    )
                } else {
                    Text("Too few stops to need evolving — every order was checked directly.", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
                }
                Spacer(Modifier.height(4.dp))
                SignalButton("Open today mode", onClick = onToday, modifier = Modifier.navigationBarsPadding())
            }
        }
    }

    why?.let { s ->
        ModalBottomSheet(onDismissRequest = { why = null }, containerColor = Trip.Paper) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Kicker("Bayesian network · ${s.name}", color = Trip.Ink)
                Text("WHY ${s.riskLevel.uppercase()} RISK?", style = MaterialTheme.typography.displaySmall)
                ProbabilityBar("Plan disrupted", s.pDisrupted, Trip.Signal)
                ProbabilityBar("Delayed on the way", s.pDelay)
                ProbabilityBar("Closed on arrival", s.pClosed)
                ProbabilityBar("Rain, if it does go wrong", s.pRainGivenDisrupted, Trip.Cobalt)
                Text(
                    "Evidence used: arrive ${s.arrive.asClock()}${if (s.travelMinutes > 20) ", long leg (${s.travelMinutes} min)" else ""}" +
                        (if (s.hoursEstimated) ", opening hours estimated from the category" else ", published opening hours") +
                        ". The last bar is diagnostic reasoning — reading the network backwards with Bayes' rule.",
                    style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
                )
                TextButton(onClick = { why = null }) { Text("Close", color = Trip.Ink) }
            }
        }
    }
}

@Composable
private fun StopRow(n: Int, s: StopEntity, onWhy: () -> Unit, onGo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.width(64.dp).padding(top = 18.dp)) {
            Text(s.start.asClock(), style = MaterialTheme.typography.headlineMedium)
            Text("to ${s.leave.asClock()}", style = MaterialTheme.typography.labelSmall, color = Trip.Muted)
        }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Trip.Card).border(2.dp, Trip.Ink, RoundedCornerShape(24.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        "${s.category.lowercase().replaceFirstChar { it.uppercase() }} · ${s.leave - s.start} min" +
                            if (s.hoursEstimated) " · hours estimated" else "",
                        style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
                    )
                }
                NumberBadge(n)
            }
            RiskPill(s.riskLevel, s.pDisrupted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Take me there", onGo)
                TextButton(onClick = onWhy) { Text("Why?", color = Trip.Ink, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}

/** Implicit intent: any maps app can handle a geo: URI. */
private fun openMaps(context: Context, s: StopEntity) {
    val uri = Uri.parse("geo:${s.lat},${s.lng}?q=${s.lat},${s.lng}(${Uri.encode(s.name)})")
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: ActivityNotFoundException) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.openstreetmap.org/?mlat=${s.lat}&mlon=${s.lng}#map=17/${s.lat}/${s.lng}")))
    }
}

/** Implicit intent + chooser: share the itinerary as text to WhatsApp, mail, etc. */
private fun share(context: Context, t: TripWithStops) {
    val text = buildString {
        appendLine("${t.trip.destination} — planned with Trippin' AI")
        t.stops.groupBy { it.dayIndex }.toSortedMap().forEach { (d, stops) ->
            appendLine()
            appendLine("Day ${d + 1}")
            stops.sortedBy { it.orderInDay }.forEach { appendLine("${it.start.asClock()}  ${it.name}") }
        }
    }
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    context.startActivity(Intent.createChooser(send, "Share plan"))
}
