package com.trippin.ai.ui.screens.prefs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.Budget
import com.trippin.ai.data.model.MemberPrefs
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.Segmented
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.Stepper
import com.trippin.ai.ui.components.TopBar
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.navigation.LocalSnack
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.group.Diet
import com.trippin.intelligence.group.StartPreference
import com.trippin.intelligence.model.Category
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private fun Diet.label() = when (this) { Diet.ANY -> "Anything"; Diet.VEGETARIAN -> "Vegetarian"; Diet.VEGAN -> "Vegan" }
private fun StartPreference.label() = when (this) { StartPreference.EARLY -> "Early"; StartPreference.NORMAL -> "Normal"; StartPreference.LATE -> "Late" }
private fun Budget.label() = when (this) { Budget.LOW -> "Low"; Budget.MID -> "Mid"; Budget.HIGH -> "High" }
private fun Category.label() = when (this) {
    Category.MUSEUM -> "Museums"; Category.FOOD -> "Food"; Category.PARK -> "Parks"
    Category.LANDMARK -> "Sights"; Category.SHOPPING -> "Shopping"; Category.NIGHTLIFE -> "Nightlife"
}

/**
 * What the planner should know about you, specifically. Per-trip when [tripId] is set (visible to
 * the group's planner, never their votes); otherwise your defaults for every new trip you join.
 */
@Composable
fun PrefsScreen(container: AppContainer, tripId: String?, onDone: () -> Unit) {
    val me = LocalSession.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()

    var interests by remember { mutableStateOf(setOf(Category.FOOD, Category.LANDMARK)) }
    var diet by remember { mutableStateOf(Diet.ANY) }
    var noAlcohol by remember { mutableStateOf(false) }
    var start by remember { mutableStateOf(StartPreference.NORMAL) }
    var maxWalk by remember { mutableStateOf(0) }
    var accessibility by remember { mutableStateOf(false) }
    var budget by remember { mutableStateOf(Budget.MID) }
    var loaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        val p = if (tripId != null) {
            container.collabRepository.prefs(tripId).first().firstOrNull { it.uid == me.uid }
                ?: container.preferences.defaultPrefs(me.uid, me.name)
        } else {
            container.preferences.defaultPrefs(me.uid, me.name)
        }
        interests = p.interests; diet = p.diet; noAlcohol = p.noAlcohol; start = p.start
        maxWalk = p.maxWalkKm ?: 0; accessibility = p.accessibility; budget = p.budget
        loaded = true
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(if (tripId != null) "Your preferences" else "Default preferences", onBack = onDone)
        if (!loaded) return@Column
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Text(
                    if (tripId != null) "The planner reads this for you specifically — it's separate from your votes." else "Copied into every new trip you create or join. You can still change it per trip.",
                    color = Trip.Muted, style = MaterialTheme.typography.bodyMedium,
                )
            }
            item {
                SectionTitle("Interested in")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { c ->
                                val on = c in interests
                                Panel(
                                    onClick = { interests = if (on) interests - c else interests + c },
                                    background = if (on) Trip.Ink else Trip.Card,
                                    padding = 12.dp,
                                    modifier = Modifier.weight(1f),
                                ) { Text(c.label(), color = if (on) Trip.Paper else Trip.Ink, style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }
            }
            item {
                SectionTitle("Diet")
                Segmented(Diet.entries.toList(), diet, { it.label() }, { diet = it })
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Doesn't drink alcohol", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(noAlcohol, { noAlcohol = it }, colors = SwitchDefaults.colors(checkedTrackColor = Trip.Signal, checkedThumbColor = Trip.Ink))
                }
            }
            item {
                SectionTitle("Start of day")
                Segmented(StartPreference.entries.toList(), start, { it.label() }, { start = it })
            }
            item {
                SectionTitle("Walking limit")
                Stepper(maxWalk, { maxWalk = it.coerceIn(0, 15) }, 0..15, if (maxWalk == 0) "km (no limit)" else "km/day")
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Accessibility needs (step-free access)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Switch(accessibility, { accessibility = it }, colors = SwitchDefaults.colors(checkedTrackColor = Trip.Signal, checkedThumbColor = Trip.Ink))
                }
            }
            item {
                SectionTitle("Budget")
                Segmented(Budget.entries.toList(), budget, { it.label() }, { budget = it })
            }
            item {
                SignalButton(
                    "Save", loading = saving,
                    onClick = {
                        saving = true
                        val p = MemberPrefs(me.uid, me.name, interests, diet, noAlcohol, start, maxWalk.takeIf { it > 0 }, accessibility, budget, System.currentTimeMillis())
                        scope.launch {
                            runCatching {
                                if (tripId != null) {
                                    val trip = container.tripRepository.trip(tripId).first()
                                    if (trip != null) container.collabRepository.savePrefs(trip, me, p)
                                } else {
                                    container.preferences.saveDefaultPrefs(p)
                                }
                            }.onSuccess { onDone() }.onFailure { saving = false; snack(it.message ?: "Couldn't save your preferences.") }
                        }
                    },
                )
            }
        }
    }
}
