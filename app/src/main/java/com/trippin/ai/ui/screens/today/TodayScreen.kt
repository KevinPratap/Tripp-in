package com.trippin.ai.ui.screens.today

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.ItineraryStop
import com.trippin.ai.data.model.Trip
import com.trippin.ai.ui.components.FuzzyChart
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.theme.Trip as TripTheme
import com.trippin.intelligence.fuzzy.FatigueController
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Geo
import com.trippin.intelligence.model.asClock
import com.trippin.intelligence.rl.QLearningRecommender
import com.trippin.intelligence.rl.QLearningRecommender.Feedback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class TodayViewModel(private val container: AppContainer, tripId: String) : ViewModel() {
    data class Loaded(val trip: Trip?, val itinerary: Itinerary?)

    val loaded: StateFlow<Loaded?> = combine(container.tripRepository.trip(tripId), container.planRepository.itinerary(tripId)) { t, i -> Loaded(t, i) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val walkedKm = MutableStateFlow(0.0)
    val minute = MutableStateFlow(LocalTime.now().let { it.hour * 60 + it.minute })
    val temperatureOverride = MutableStateFlow<Double?>(null)
    val suggestions = MutableStateFlow<List<Pair<Category, Double>>>(emptyList())
    val kmToNext = MutableStateFlow<Double?>(null)
    val sensorAvailable = container.stepSensor.available

    init {
        if (container.stepSensor.hasPermission()) {
            viewModelScope.launch { container.stepSensor.kilometresToday().collect { walkedKm.value = it } }
        }
    }

    fun refreshDistance(lat: Double, lng: Double) = viewModelScope.launch {
        val here = container.location.lastLocation() ?: return@launch
        kmToNext.value = Geo.haversineKm(here.latitude, here.longitude, lat, lng)
    }

    fun refreshSuggestions(state: QLearningRecommender.State) = viewModelScope.launch {
        suggestions.value = container.learningRepository.suggest(state)
    }

    fun feedback(state: QLearningRecommender.State, category: Category, f: Feedback) = viewModelScope.launch {
        val next = QLearningRecommender.stateFor(minute.value + 90, 0.0).copy(energy = state.energy)
        container.learningRepository.feedback(state, category, f, next)
        suggestions.value = container.learningRepository.suggest(state)
    }
}

/** Live, on-the-day view of the plan: fatigue (fuzzy logic) and what to do next (Q-learning). */
@Composable
fun TodayScreen(container: AppContainer, tripId: String, onClose: () -> Unit) {
    val vm: TodayViewModel = viewModel(key = "today-$tripId") { TodayViewModel(container, tripId) }
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val walked by vm.walkedKm.collectAsStateWithLifecycle()
    val minute by vm.minute.collectAsStateWithLifecycle()
    val tempOverride by vm.temperatureOverride.collectAsStateWithLifecycle()
    val suggestions by vm.suggestions.collectAsStateWithLifecycle()
    val kmToNext by vm.kmToNext.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDemo by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    LaunchedEffect(Unit) {
        val perms = mutableListOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= 29) perms += Manifest.permission.ACTIVITY_RECOGNITION
        permissionLauncher.launch(perms.toTypedArray())
    }

    val trip = loaded?.trip
    val itin = loaded?.itinerary
    if (trip == null || itin == null) {
        Box(Modifier.fillMaxSize().background(TripTheme.Ink)) {
            IconButton(onClick = onClose, modifier = Modifier.statusBarsPadding()) { Icon(Icons.Filled.Close, contentDescription = "Close", tint = TripTheme.Paper) }
            Text(
                if (loaded == null) "Loading…" else "No confirmed plan yet for today.",
                color = TripTheme.Paper, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
        }
        return
    }

    val offset = ChronoUnit.DAYS.between(trip.startDate, LocalDate.now()).toInt()
    val preview = offset !in itin.days.indices
    val dayIndex = if (preview) 0 else offset
    val stops: List<ItineraryStop> = itin.days.getOrNull(dayIndex)?.stops ?: emptyList()
    val current = stops.lastOrNull { it.start <= minute } ?: stops.firstOrNull()
    val next = current?.let { c -> stops.getOrNull(stops.indexOf(c) + 1) }

    val temp = tempOverride ?: (itin.days.getOrNull(dayIndex)?.let { if (it.rainKnown) 28.0 else 26.0 } ?: 26.0)
    val activeHours = ((minute - (stops.firstOrNull()?.start ?: minute)) / 60.0).coerceAtLeast(0.0)
    val advice = FatigueController.advise(walked, activeHours, temp)
    val rlState = QLearningRecommender.stateFor(minute, advice.score)
    LaunchedEffect(rlState) { vm.refreshSuggestions(rlState) }

    Column(
        Modifier.fillMaxSize().background(TripTheme.Ink).statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Kicker("${trip.destination?.name ?: trip.title} · day ${dayIndex + 1}${if (preview) " · preview" else ""}", color = TripTheme.OnInkMuted, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close today mode", tint = TripTheme.Paper) }
        }

        if (current != null) {
            Kicker(if (minute in current.start until current.leave) "Now · until ${current.leave.asClock()}" else "First up · ${current.start.asClock()}", color = TripTheme.Signal)
            Text(current.name.uppercase(), style = MaterialTheme.typography.displayMedium, color = TripTheme.Paper)
            val progress = ((minute - current.start).toFloat() / (current.leave - current.start).coerceAtLeast(1)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress }, color = TripTheme.Signal, trackColor = Color(0xFF2A2B25),
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            )
        } else {
            Text("Nothing scheduled for today.", color = TripTheme.Paper, style = MaterialTheme.typography.titleLarge)
        }

        next?.let { n ->
            LaunchedEffect(n.key) { vm.refreshDistance(n.lat, n.lng) }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TripTheme.Signal).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Kicker("Next · ${n.start.asClock()} · ${n.travelMinutes} min away", color = TripTheme.Ink)
                Text(n.name.uppercase(), style = MaterialTheme.typography.displaySmall, color = TripTheme.Ink)
                kmToNext?.let { Text("You're ${"%.1f".format(it)} km away (GPS)", style = MaterialTheme.typography.titleMedium, color = TripTheme.Ink) }
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${n.lat},${n.lng}?q=${n.lat},${n.lng}(${Uri.encode(n.name)})"))) },
                    colors = ButtonDefaults.buttonColors(containerColor = TripTheme.Ink, contentColor = TripTheme.Paper),
                    shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text("TAKE ME THERE", style = MaterialTheme.typography.labelLarge) }
            }
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TripTheme.Paper).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("Fuzzy logic · how you're holding up", color = TripTheme.Ink)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${advice.score.toInt()}", style = MaterialTheme.typography.displayLarge, color = TripTheme.Ink)
                Text(advice.label.uppercase(), style = MaterialTheme.typography.headlineMedium, color = TripTheme.Signal, modifier = Modifier.padding(bottom = 12.dp))
            }
            Text(advice.suggestion, style = MaterialTheme.typography.titleMedium)
            FuzzyChart(
                terms = FatigueController.fatigue.terms.values.map { mf -> (0..100 step 2).map { x -> x.toDouble() to mf.degree(x.toDouble()) } },
                aggregated = advice.detail.aggregated,
                centroid = advice.score,
                range = 0.0..100.0,
            )
            Text(
                "Inputs: ${"%.1f".format(walked)} km walked${if (vm.sensorAvailable) " (step sensor)" else ""} · ${"%.1f".format(activeHours)} h out · ${temp.toInt()}°C",
                style = MaterialTheme.typography.bodyMedium, color = TripTheme.Muted,
            )
        }

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(TripTheme.Cobalt).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("Reinforcement learning · learns from you", color = Color.White)
            val top = suggestions.firstOrNull()
            Text((top?.first?.name ?: "—").uppercase(), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(
                "Best next kind of stop for ${rlState.time.name.lowercase()}, ${rlState.energy.name.lowercase()} energy. Tell it how that sounds — it updates its Q-table.",
                style = MaterialTheme.typography.bodyMedium, color = Color.White,
            )
            if (top != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FeedbackButton("Love it", Modifier.weight(1f)) { vm.feedback(rlState, top.first, Feedback.LIKED) }
                    FeedbackButton("Fine", Modifier.weight(1f)) { vm.feedback(rlState, top.first, Feedback.KEPT) }
                    FeedbackButton("Skip", Modifier.weight(1f)) { vm.feedback(rlState, top.first, Feedback.SKIPPED) }
                }
            }
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Demo controls", style = MaterialTheme.typography.titleMedium, color = TripTheme.Paper, modifier = Modifier.weight(1f))
            Switch(
                checked = showDemo, onCheckedChange = { showDemo = it },
                colors = SwitchDefaults.colors(checkedTrackColor = TripTheme.Signal, checkedThumbColor = TripTheme.Ink),
            )
        }
        if (showDemo) {
            DemoSlider("Clock ${minute.asClock()}", minute.toFloat(), 360f..1320f) { vm.minute.value = it.toInt() }
            DemoSlider("Walked ${"%.1f".format(walked)} km", walked.toFloat(), 0f..15f) { vm.walkedKm.value = it.toDouble() }
            DemoSlider("Temperature ${temp.toInt()}°C", temp.toFloat(), 10f..45f) { vm.temperatureOverride.value = it.toDouble() }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun FeedbackButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.height(52.dp), shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = TripTheme.Ink),
    ) { Text(label, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun DemoSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TripTheme.Paper)
        Slider(
            value = value.coerceIn(range), onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = TripTheme.Signal, activeTrackColor = TripTheme.Signal, inactiveTrackColor = Color(0xFF3A3B34)),
        )
    }
}
