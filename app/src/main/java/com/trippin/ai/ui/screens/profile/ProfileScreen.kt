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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.ai.AppContainer
import com.trippin.ai.auth.Session
import com.trippin.ai.data.repository.Settings
import com.trippin.ai.ui.components.GhostButton
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.theme.Trip
import com.trippin.ai.work.TripBriefWorker
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(container: AppContainer, onSignedOut: () -> Unit) {
    val session by container.authRepository.session.collectAsStateWithLifecycle(initialValue = null)
    val settings by container.preferences.settings.collectAsStateWithLifecycle(initialValue = Settings(null, true))
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Kicker(
            when (val s = session) {
                is Session.Account -> "Signed in · ${s.email}"
                is Session.Guest -> "Guest · on this phone only"
                else -> ""
            },
        )
        Headline(
            when (val s = session) {
                is Session.Account -> s.email.substringBefore('@')
                is Session.Guest -> s.name
                else -> "You"
            },
        )

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Trip.Card).border(2.dp, Trip.Ink, RoundedCornerShape(24.dp)).padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Daily trip briefing", style = MaterialTheme.typography.titleLarge)
                Text("Background check of the forecast and risk for your next trip.", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
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

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Trip.Ink).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Kicker("What makes it intelligent", color = Trip.Signal)
            listOf(
                "Bayesian network — risk that a stop goes wrong",
                "Fuzzy inference — how tired you are, and what to do",
                "Genetic algorithm — the order of the day's stops",
                "Q-learning — what kind of stop you'd like next",
            ).forEach { Text(it, style = MaterialTheme.typography.titleMedium, color = Trip.Paper) }
            Text("Data: OpenStreetMap, Open-Meteo. No paid APIs, no keys.", style = MaterialTheme.typography.bodyMedium, color = Trip.OnInkMuted)
        }

        GhostButton("Sign out", onClick = {
            scope.launch {
                container.authRepository.signOut()
                onSignedOut()
            }
        }, modifier = Modifier.fillMaxWidth())
    }
}
