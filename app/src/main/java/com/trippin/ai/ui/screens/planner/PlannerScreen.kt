package com.trippin.ai.ui.screens.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.repository.BuildStage
import com.trippin.ai.data.repository.PlannerInput
import com.trippin.ai.data.repository.TripRepository
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.Stepper
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.model.Category
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class PlannerState(
    val destination: String = "",
    val startDate: LocalDate = LocalDate.now().plusDays(7),
    val days: Int = 2,
    val travellers: Int = 2,
    val pace: TripBrain.Pace = TripBrain.Pace.STANDARD,
    val interests: List<Category> = listOf(Category.MUSEUM, Category.FOOD, Category.LANDMARK),
    val stage: BuildStage? = null,
    val builtTripId: Long? = null,
    val error: String? = null,
) {
    val canBuild: Boolean get() = destination.isNotBlank() && interests.isNotEmpty() && stage == null
}

class PlannerViewModel(private val repo: TripRepository) : ViewModel() {
    private val _state = MutableStateFlow(PlannerState())
    val state: StateFlow<PlannerState> = _state

    fun update(f: (PlannerState) -> PlannerState) = _state.update(f)

    fun toggle(c: Category) = _state.update { s ->
        s.copy(interests = if (c in s.interests) s.interests - c else s.interests + c)
    }

    fun build() {
        val s = _state.value
        if (!s.canBuild) return
        viewModelScope.launch {
            runCatching {
                repo.createTrip(PlannerInput(s.destination.trim(), s.startDate, s.days, s.travellers, s.pace, s.interests)) { stage ->
                    _state.update { it.copy(stage = stage) }
                }
            }.onSuccess { id -> _state.update { it.copy(builtTripId = id) } }
                .onFailure { e -> _state.update { it.copy(stage = null, error = e.message ?: "Could not build the plan") } }
        }
    }
}

private val labels = mapOf(
    Category.MUSEUM to "Museums", Category.FOOD to "Local food", Category.LANDMARK to "Landmarks",
    Category.PARK to "Parks & walks", Category.SHOPPING to "Markets", Category.NIGHTLIFE to "Nightlife",
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(container: AppContainer, onBack: () -> Unit, onBuilt: (Long) -> Unit) {
    val vm: PlannerViewModel = viewModel { PlannerViewModel(container.tripRepository) }
    val s by vm.state.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }

    LaunchedEffect(s.builtTripId) { s.builtTripId?.let(onBuilt) }

    if (s.stage != null) {
        BuildingView(s.destination, s.stage!!)
        return
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            IconButton(onClick = onBack, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("PLAN A\nTRIP", style = MaterialTheme.typography.displayMedium)

            Section("Where") {
                OutlinedTextField(
                    value = s.destination, onValueChange = { v -> vm.update { it.copy(destination = v, error = null) } },
                    placeholder = { Text("e.g. Jaipur, Lisbon, Tokyo") }, singleLine = true,
                    textStyle = MaterialTheme.typography.titleLarge,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Trip.Ink, unfocusedBorderColor = Trip.Ink,
                        focusedContainerColor = Trip.Card, unfocusedContainerColor = Trip.Card,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section("When") {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(18.dp))
                        .border(2.dp, Trip.Ink, RoundedCornerShape(18.dp)).background(Trip.Card)
                        .clickable(onClickLabel = "Change start date") { pickingDate = true }.padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Starts " + s.startDate.format(DateTimeFormatter.ofPattern("EEE d MMM")), style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(10.dp))
                Stepper(s.days, { v -> vm.update { it.copy(days = v) } }, 1..5, if (s.days == 1) "day" else "days")
            }

            Section("Who") {
                Stepper(s.travellers, { v -> vm.update { it.copy(travellers = v) } }, 1..12, if (s.travellers == 1) "traveller" else "travellers")
            }

            Section("Pace") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TripBrain.Pace.entries.forEach { p ->
                        val on = p == s.pace
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(18.dp))
                                .background(if (on) Trip.Signal else Trip.Card)
                                .border(2.dp, Trip.Ink, RoundedCornerShape(18.dp))
                                .selectable(selected = on, role = Role.RadioButton) { vm.update { it.copy(pace = p) } }
                                .padding(14.dp),
                        ) {
                            Text(p.name, style = MaterialTheme.typography.headlineMedium)
                            Text("≤ ${p.maxStops} stops", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            Section("You're into") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        val on = c in s.interests
                        FilterChip(
                            selected = on, onClick = { vm.toggle(c) },
                            label = { Text(labels.getValue(c), style = MaterialTheme.typography.titleMedium) },
                            leadingIcon = if (on) { { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) } } else null,
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Trip.Ink, selectedLabelColor = Trip.Paper, selectedLeadingIconColor = Trip.Signal,
                                containerColor = Trip.Card, labelColor = Trip.Ink,
                            ),
                            modifier = Modifier.heightIn(min = 44.dp),
                        )
                    }
                }
            }

            s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Spacer(Modifier.height(8.dp))
        }
        SignalButton(
            "Build my plan", onClick = vm::build, enabled = s.canBuild,
            modifier = Modifier.navigationBarsPadding().padding(20.dp),
        )
    }

    if (pickingDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = s.startDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { ms ->
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.update { it.copy(startDate = d) }
                    }
                    pickingDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = picker) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Kicker(title, color = Trip.Ink)
        content()
    }
}

/** Full-screen ink "building" state: each stage names the technique doing the work. */
@Composable
private fun BuildingView(destination: String, stage: BuildStage) {
    Column(
        Modifier.fillMaxSize().background(Trip.Ink).statusBarsPadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Kicker(destination, color = Trip.OnInkMuted)
        Text(
            "${stage.ordinal + 1}/${BuildStage.entries.size}",
            style = MaterialTheme.typography.displayLarge, color = Trip.Signal,
        )
        Text("BUILDING YOUR PLAN", style = MaterialTheme.typography.displaySmall, color = Trip.Paper)
        Spacer(Modifier.height(8.dp))
        BuildStage.entries.forEach { st ->
            val done = st.ordinal < stage.ordinal
            val now = st == stage
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                when {
                    done -> Icon(Icons.Filled.Check, contentDescription = "Done", tint = Trip.Signal, modifier = Modifier.size(26.dp))
                    now -> CircularProgressIndicator(color = Trip.Signal, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                    else -> Spacer(Modifier.size(26.dp))
                }
                Column {
                    Text(st.label, style = MaterialTheme.typography.titleLarge, color = if (done || now) Trip.Paper else Trip.OnInkMuted.copy(alpha = 0.6f))
                    Text(st.technique.uppercase(), style = MaterialTheme.typography.labelSmall, color = Trip.OnInkMuted)
                }
            }
        }
    }
}
