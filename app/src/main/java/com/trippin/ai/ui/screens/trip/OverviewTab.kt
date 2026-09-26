package com.trippin.ai.ui.screens.trip

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.ai.data.model.TripStatus
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.daysUntil
import com.trippin.ai.ui.theme.Trip

/**
 * The trip at a glance: where things stand, the weather, and one clear next action — so a member
 * opening the trip after a few days away knows immediately what changed and what to do next.
 */
@Composable
fun OverviewTab(vm: TripViewModel, ui: TripUi, onGo: (TripTab) -> Unit, onToday: () -> Unit, onPrefs: () -> Unit) {
    val trip = ui.trip ?: return
    val weather by vm.weather.collectAsStateWithLifecycle()

    LaunchedEffect(trip.id, trip.stay, trip.destination) { vm.loadWeather(trip) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            if (trip.isOngoing()) {
                Panel(background = Trip.Signal) {
                    Kicker("Happening now", color = Trip.Ink)
                    Text("You're on the trip", style = MaterialTheme.typography.headlineSmall, color = Trip.Ink)
                    SignalButton("Open today", onClick = onToday, container = Trip.Ink, content = Trip.Paper)
                }
            } else if (!trip.isPast()) {
                Panel {
                    Kicker(daysUntil(trip.startDate).ifBlank { "Dates set" })
                    Text(trip.title, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        when (trip.status) {
            TripStatus.DECIDING_DESTINATION -> item {
                Panel(background = Trip.Card) {
                    Kicker("Not decided yet")
                    Text("Where should you go?", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (ui.destinationIdeas.isEmpty()) "Suggest some cities and vote together."
                        else "${ui.destinationIdeas.size} suggested so far — cast your vote.",
                        color = Trip.Muted,
                    )
                    SignalButton("Suggest & vote", onClick = { onGo(TripTab.IDEAS) })
                }
            }
            else -> item {
                Panel {
                    Kicker("Destination")
                    Text(trip.destination?.label ?: "Not set", style = MaterialTheme.typography.titleLarge)
                    if (weather != null && weather!!.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            weather!!.take(5).forEach { w ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(w.date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = Trip.Muted)
                                    Text(if (w.known) "${w.maxTempC.toInt()}°" else "—", style = MaterialTheme.typography.titleMedium)
                                    Text(if (w.known) "${(w.rainProbability * 100).toInt()}% rain" else "unknown", style = MaterialTheme.typography.labelSmall, color = Trip.Muted)
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionTitle("Where things stand")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatusRow("Ideas & votes", "${ui.destinationIdeas.size + ui.placeIdeas.size} suggested", onClick = { onGo(TripTab.IDEAS) })
                StatusRow(
                    "Plan",
                    when {
                        ui.itinerary == null -> "Not generated yet"
                        ui.itinerary.locked -> "Confirmed · ${ui.itinerary.days.size} days"
                        else -> "Ready for review"
                    },
                    onClick = { onGo(TripTab.PLAN) },
                )
                StatusRow("People", "${ui.members.size} on the trip", onClick = { onGo(TripTab.PEOPLE) })
                StatusRow(
                    "Your preferences",
                    if (ui.prefsOf(vm.me.uid)?.updatedAt ?: 0 > 0) "Saved" else "Not set — helps the planner",
                    onClick = onPrefs,
                )
            }
        }

        if (!ui.itinerary?.notes.isNullOrEmpty()) {
            item {
                SectionTitle("How the plan balances the group")
                Panel(background = Trip.Card) {
                    ui.itinerary!!.notes.forEach { n -> Text("· $n", style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }

        if (vm.isDemo && trip.isGroup) {
            item {
                Panel(background = Trip.PaperDeep) {
                    Kicker("Demo mode")
                    Text("No friends on this device yet? Simulate a few to see how the group features behave.", color = Trip.Muted)
                    SmallButton("Add simulated friends", onClick = { vm.simulateFriends() })
                }
            }
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, onClick: () -> Unit) {
    Panel(onClick = onClick, padding = 14.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
            }
            Pill("OPEN", Trip.Ink, Trip.Paper)
        }
    }
}
