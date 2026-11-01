package com.trippin.ai.ui.screens.join

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.trippin.ai.AppContainer
import com.trippin.ai.data.repository.TripRepository
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.TextInput
import com.trippin.ai.ui.components.TopBar
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.theme.Trip
import kotlinx.coroutines.launch

/** Enter (or arrive via) an invite code and join a friend's trip — needs a connected backend. */
@Composable
fun JoinScreen(container: AppContainer, code: String?, onBack: () -> Unit, onJoined: (String) -> Unit) {
    val me = LocalSession.current
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf(code.orEmpty()) }
    var joining by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize()) {
        TopBar("Join a trip", onBack = onBack)
        Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Kicker("Invite code")
            Headline("Join friends")
            Text("Ask whoever's planning for their 8-character invite link or code.", color = Trip.Muted, style = MaterialTheme.typography.bodyMedium)
            TextInput(
                input, { input = it.uppercase().take(40) }, "Code or link",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                error = error,
                onDone = { },
            )
            SignalButton(
                "Join", loading = joining, enabled = input.isNotBlank(),
                onClick = {
                    joining = true; error = null
                    scope.launch {
                        when (val r = container.tripRepository.join(input, me)) {
                            is TripRepository.JoinResult.Joined -> onJoined(r.tripId)
                            is TripRepository.JoinResult.AlreadyMember -> onJoined(r.tripId)
                            is TripRepository.JoinResult.Failed -> { joining = false; error = r.message }
                        }
                    }
                },
            )
        }
    }
}
