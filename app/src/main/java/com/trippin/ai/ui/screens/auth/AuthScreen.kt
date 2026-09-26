package com.trippin.ai.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.trippin.ai.AppContainer
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Segmented
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.TextInput
import com.trippin.ai.ui.navigation.LocalSnack
import com.trippin.ai.ui.theme.Trip
import kotlinx.coroutines.launch

private enum class Mode(val label: String) { GUEST("Quick start"), SIGN_IN("Sign in"), REGISTER("New account") }

/** Welcome + sign-in. A guest can start immediately and save an account later without losing trips. */
@Composable
fun AuthScreen(container: AppContainer) {
    val auth = container.authRepository
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    var mode by rememberSaveable { mutableStateOf(Mode.GUEST) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(block: suspend () -> Result<Unit>) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            block().onFailure { error = it.message ?: "Something went wrong" }
            busy = false
        }
    }

    val emailOk = email.trim().matches(Regex("""[^@\s]+@[^@\s]+\.[^@\s]+"""))
    val canSubmit = when (mode) {
        Mode.GUEST -> name.isNotBlank()
        Mode.SIGN_IN -> emailOk && password.length >= 6
        Mode.REGISTER -> name.isNotBlank() && emailOk && password.length >= 6
    }
    val action = {
        when (mode) {
            Mode.GUEST -> submit { auth.continueAsGuest(name) }
            Mode.SIGN_IN -> submit { auth.signIn(email, password) }
            Mode.REGISTER -> submit { auth.register(name, email, password) }
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Kicker("Trippin' AI")
        Headline("Plan trips together", accentLastChar = false)
        Text(
            "Invite friends, vote on where to go and what to do, and let the planner turn the group's choices into a day-by-day plan that fits everyone.",
            style = MaterialTheme.typography.bodyLarge, color = Trip.Muted,
        )
        Spacer(Modifier.height(4.dp))

        if (auth.firebaseAvailable) {
            Segmented(Mode.entries, mode, { it.label }, { mode = it; error = null })
        } else {
            Text(
                "Offline demo build: trips are stored on this phone. Connect Firebase to sync with friends.",
                style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
            )
        }

        if (mode != Mode.SIGN_IN) {
            TextInput(name, { name = it.take(40) }, "Your first name", placeholder = "Shown to friends on shared trips", onDone = if (mode == Mode.GUEST && canSubmit) action else null)
        }
        if (mode != Mode.GUEST) {
            TextInput(
                email, { email = it }, "Email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                error = if (email.isNotBlank() && !emailOk) "That doesn't look like an email" else null,
            )
            TextInput(
                password, { password = it }, if (mode == Mode.REGISTER) "Password (6+ characters)" else "Password",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visual = PasswordVisualTransformation(),
                onDone = if (canSubmit) action else null,
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

        SignalButton(
            when (mode) { Mode.GUEST -> "Start planning"; Mode.SIGN_IN -> "Sign in"; Mode.REGISTER -> "Create account" },
            onClick = action, enabled = canSubmit, loading = busy,
        )

        when (mode) {
            Mode.GUEST -> if (auth.firebaseAvailable) Text(
                "No account needed. You can add an email later in Profile to keep your trips on a new phone.",
                style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
            )
            Mode.SIGN_IN -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = {
                        if (!emailOk) error = "Enter your email first, then tap Forgot password."
                        else submit { auth.resetPassword(email).onSuccess { snack("Password reset email sent to ${email.trim()}") } }
                    },
                ) { Text("Forgot password?", color = Trip.Ink) }
            }
            Mode.REGISTER -> {}
        }
    }
}
