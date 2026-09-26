package com.trippin.ai.ui.screens.create

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.PlaceHit
import com.trippin.ai.data.repository.NewTrip
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.Segmented
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.Stepper
import com.trippin.ai.ui.components.TextInput
import com.trippin.ai.ui.components.TopBar
import com.trippin.ai.ui.components.dayLabel
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.navigation.LocalSnack
import com.trippin.ai.ui.screens.trip.SearchSheet
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.TripBrain
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun TripBrain.Pace.label() = when (this) {
    TripBrain.Pace.RELAXED -> "Relaxed"
    TripBrain.Pace.STANDARD -> "Standard"
    TripBrain.Pace.PACKED -> "Packed"
}

/** Set up a new trip: solo or with friends, where (or vote later), when, and how full the days should be. */
@Composable
fun CreateTripScreen(container: AppContainer, onBack: () -> Unit, onCreated: (String, Boolean) -> Unit) {
    val me = LocalSession.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var isGroup by remember { mutableStateOf(false) }
    var destination by remember { mutableStateOf<Destination?>(null) }
    var start by remember { mutableStateOf(LocalDate.now().plusWeeks(2)) }
    var days by remember { mutableStateOf(4) }
    var pace by remember { mutableStateOf(TripBrain.Pace.STANDARD) }
    var searching by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar("New trip", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                SectionTitle("Who's going")
                Segmented(listOf(false, true), isGroup, { if (it) "With friends" else "Just me" }, { isGroup = it })
                if (isGroup) Text("You'll get an invite link right after this — everyone votes and suggests together.", color = Trip.Muted, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                SectionTitle("Trip name")
                TextInput(title, { title = it.take(60) }, "e.g. Goa with the crew")
            }
            item {
                SectionTitle("Destination")
                if (isGroup) {
                    Text("Leave blank to decide together — you'll suggest and vote on cities as a group.", color = Trip.Muted, style = MaterialTheme.typography.bodyMedium)
                }
                Panel(onClick = { searching = true }) {
                    Text(destination?.label ?: if (isGroup) "Not decided — vote later" else "Choose a destination", style = MaterialTheme.typography.titleLarge)
                }
            }
            item {
                SectionTitle("When")
                Panel(onClick = { pickingDate = true }) { Text(dayLabel(start), style = MaterialTheme.typography.titleLarge) }
            }
            item {
                SectionTitle("How many days")
                Stepper(days, { days = it }, 1..21, "days")
            }
            item {
                SectionTitle("Pace")
                Segmented(TripBrain.Pace.entries.toList(), pace, { it.label() }, { pace = it })
                Text(
                    when (pace) {
                        TripBrain.Pace.RELAXED -> "Fewer stops a day, more breathing room."
                        TripBrain.Pace.STANDARD -> "A balanced day — the genetic algorithm fits what it can."
                        TripBrain.Pace.PACKED -> "Long days, more stops — good for a short trip."
                    },
                    color = Trip.Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                SignalButton(
                    if (isGroup) "Create & invite friends" else "Create trip",
                    loading = saving,
                    enabled = title.isNotBlank() && (isGroup || destination != null),
                    onClick = {
                        saving = true
                        scope.launch {
                            runCatching {
                                container.tripRepository.create(
                                    NewTrip(title.trim(), isGroup, start, days, pace, destination), me,
                                )
                            }.onSuccess { id -> onCreated(id, isGroup) }
                                .onFailure { saving = false; snack(it.message ?: "Couldn't create the trip. Check your connection.") }
                        }
                    },
                )
            }
        }
    }

    if (searching) {
        SearchSheet(
            title = "Where to?", hint = "Search a city",
            search = { q -> container.placesRepository.searchDestinations(q) },
            onPick = { hit: PlaceHit -> destination = Destination(hit.name, hit.country, hit.lat, hit.lng); searching = false },
            onDismiss = { searching = false },
        )
    }

    if (pickingDate) {
        DatePickerField(initial = start, onPick = { start = it; pickingDate = false }, onDismiss = { pickingDate = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerField(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()) else onDismiss()
            }) { Text("Set date") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) { DatePicker(state = state) }
}
