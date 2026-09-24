package com.trippin.ai.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.trippin.ai.AppContainer
import com.trippin.ai.ui.components.GhostButton
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.theme.Trip
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(container: AppContainer, onDone: () -> Unit) {
    val auth = container.authRepository
    val scope = rememberCoroutineScope()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun submit(block: suspend () -> Result<Unit>) {
        busy = true
        error = null
        scope.launch {
            block().onSuccess { onDone() }.onFailure { error = it.message ?: "Something went wrong" }
            busy = false
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Trip.Ink, unfocusedBorderColor = Trip.Ink,
        focusedContainerColor = Trip.Card, unfocusedContainerColor = Trip.Card,
    )

    Column(
        Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Kicker("Trippin' AI")
        Headline("Plans that think", accentLastChar = false)
        Text(
            "A trip planner that reasons under uncertainty, learns what you like, and evolves the best route.",
            style = MaterialTheme.typography.bodyLarge, color = Trip.Muted,
        )
        Spacer(Modifier.height(8.dp))

        if (auth.firebaseAvailable) {
            OutlinedTextField(
                value = email, onValueChange = { email = it }, label = { Text("Email") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(18.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it }, label = { Text("Password (6+ characters)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(18.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            SignalButton("Sign in", onClick = { submit { auth.signIn(email, password) } }, loading = busy, enabled = email.isNotBlank() && password.length >= 6)
            TextButton(onClick = { submit { auth.register(email, password) } }, enabled = !busy) {
                Text("New here? Create an account", color = Trip.Ink, style = MaterialTheme.typography.titleMedium)
            }
            Kicker("or")
        } else {
            Text(
                "Account sign-in is off in this build (no google-services.json). Your trips are kept on this phone.",
                style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
            )
        }

        OutlinedTextField(
            value = name, onValueChange = { name = it }, label = { Text("Your first name") }, singleLine = true,
            shape = RoundedCornerShape(18.dp), colors = fieldColors, modifier = Modifier.fillMaxWidth(),
        )
        GhostButton("Continue as a guest", onClick = { submit { auth.continueAsGuest(name); Result.success(Unit) } }, modifier = Modifier.fillMaxWidth())

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}
