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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.trippin.ai.AppContainer
import com.trippin.ai.BuildConfig
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

/** You: your name, notification settings, default preferences and account controls. */
@Composable
fun ProfileScreen(container: AppContainer, onDefaults: () -> Unit) {
    val me = LocalSession.current
    val settings by container.preferences.settings.collectAsStateWithLifecycle(initialValue = Settings(null, null, true))
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snack = LocalSnack.current
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Kicker(if (me.isGuest) "Guest" else me.email ?: "Signed in")
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

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Kicker("About")
            Text("Places and routes come from OpenStreetMap contributors. Weather from Open-Meteo. Place photos from Wikimedia Commons, credited where shown.", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { openUrl(context, PRIVACY_URL) }) { Text("Privacy", color = Trip.Ink) }
                TextButton(onClick = { openUrl(context, TERMS_URL) }) { Text("Terms", color = Trip.Ink) }
            }
            Text("Trippin ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.labelMedium, color = Trip.Muted)
        }

        GhostButton("Sign out", onClick = { scope.launch { container.authRepository.signOut() } }, modifier = Modifier.fillMaxWidth())
        TextButton(onClick = { deleting = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete account and data", color = Trip.Danger) }
    }

    if (deleting) {
        var password by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        val needsPassword = !me.isGuest && me.email != null
        AlertDialog(
            onDismissRequest = { if (!busy) deleting = false },
            title = { Text("Delete your account?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("This permanently removes your account. Trips you created are deleted for everyone on them, and you leave trips you only joined. This can't be undone.", color = Trip.Muted)
                    if (needsPassword) TextInput(password, { password = it }, "Password", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && (!needsPassword || password.isNotEmpty()), onClick = {
                    busy = true
                    scope.launch {
                        // Check the password before touching any data, so a typo can't half-delete an account.
                        val ok = container.authRepository.confirmPassword(password.takeIf { needsPassword })
                        if (ok.isFailure) {
                            busy = false
                            snack(ok.exceptionOrNull()?.message ?: "Couldn't confirm. Try again.")
                            return@launch
                        }
                        container.tripRepository.clearAccountData(me)
                        container.authRepository.deleteAccount(password.takeIf { needsPassword })
                            .onFailure { busy = false; snack(it.message ?: "Couldn't delete the account.") }
                            .onSuccess { deleting = false }
                    }
                }) { Text(if (busy) "Deleting" else "Delete", color = Trip.Danger) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleting = false }) { Text("Cancel") } },
        )
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

private const val PRIVACY_URL = "https://trippin-ee84c.web.app/privacy.html"
private const val TERMS_URL = "https://trippin-ee84c.web.app/terms.html"

private fun openUrl(context: android.content.Context, url: String) {
    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
}
