package com.trippin.ai.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.ai.AppContainer
import com.trippin.ai.data.repository.Settings
import com.trippin.ai.ui.components.Avatar
import com.trippin.ai.ui.components.GhostButton
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.TextInput
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.navigation.LocalSnack
import com.trippin.ai.ui.theme.Trip
import com.trippin.ai.work.TripBriefWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** You: your name, whether you're on Firebase or a local demo, notification settings, and the AI Lab. */
@Composable
fun ProfileScreen(container: AppContainer, onLab: () -> Unit, onDefaults: () -> Unit) {
    val me = LocalSession.current
    val settings by container.preferences.settings.collectAsStateWithLifecycle(initialValue = Settings(null, null, true))
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snack = LocalSnack.current
    var renaming by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Kicker(if (me.isDemo) "Demo mode · on this phone only" else if (me.isGuest) "Guest" else me.email ?: "Signed in")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Avatar(me.name, me.uid, 56.dp)
            Column(Modifier.weight(1f)) { Headline(me.name) }
            SmallButton("Edit", onClick = { renaming = true })
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Trip.Card).border(2.dp, Trip.Ink, RoundedCornerShape(24.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Trip briefings", style = MaterialTheme.typography.titleLarge)
                Text("Background check of the forecast and group activity for your trips.", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
            }
            Switch(
                checked = settings.briefingsOn,
                onCheckedChange = { on ->
                    scope.launch { container.preferences.setBriefings(on) }
                    if (on) TripBriefWorker.schedule(context) else TripBriefWorker.cancel(context)
                },
                colors = SwitchDefaults.colors(checkedTrackColor = Trip.Signal, checkedThumbColor = Trip.Ink),
            )
        }

        SmallButton("Default preferences for new trips", onClick = onDefaults, modifier = Modifier.fillMaxWidth())
        SmallButton("Open the AI Lab", onClick = onLab, modifier = Modifier.fillMaxWidth())

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Trip.Ink).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("What makes it intelligent", color = Trip.Signal)
            listOf(
                "Bayesian network — risk that a stop goes wrong",
                "Fuzzy inference — how tired the group is, and what to do",
                "Genetic algorithm — the order of each day's stops",
                "Q-learning — what kind of stop you'd like next",
            ).forEach { Text(it, style = MaterialTheme.typography.titleMedium, color = Trip.Paper) }
            Text("Places & weather: OpenStreetMap, Open-Meteo. No paid APIs, no keys.", style = MaterialTheme.typography.bodyMedium, color = Trip.OnInkMuted)
        }

        if (me.isDemo) {
            Text(
                "This build isn't connected to Firebase yet, so friends on other phones can't see this account. Everything still works on this device.",
                style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
            )
        }

        GhostButton("Sign out", onClick = { scope.launch { container.authRepository.signOut() } }, modifier = Modifier.fillMaxWidth())
    }

    if (renaming) {
        var name by remember { mutableStateOf(me.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Your name") },
            text = { TextInput(name, { name = it.take(40) }, "Name") },
            confirmButton = {
                TextButton(onClick = {
                    renaming = false
                    scope.launch {
                        container.authRepository.rename(name).onFailure { snack(it.message ?: "Couldn't save your name.") }
                        val tripIds = container.tripRepository.myTrips(me.uid).first().map { it.id }
                        container.tripRepository.updateMemberName(tripIds, me.copy(name = name.trim()))
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
}
