package com.trippin.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trippin.core.design.AccentCrimson
import com.trippin.core.design.Ink
import com.trippin.core.design.InkMuted
import com.trippin.core.design.Panel
import com.trippin.core.design.Paper
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinType
import com.trippin.core.design.trippinFieldInk
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.RequestMagicLinkDto
import com.trippin.core.network.SessionStore
import com.trippin.core.network.VerifyMagicLinkDto
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Signing in.
 *
 * There is no guest account any more. An install that has not signed in holds no identity, the API
 * answers it 401, and this is the screen that gives it one. The flow is the real one: an email goes
 * to POST /auth/request-link, the server issues a single use link and stores only its hash, and
 * POST /auth/verify turns that link into a session token.
 *
 * One thing is stated rather than papered over. No mail provider is wired up on the server yet, so
 * request-link answers `delivery: "console"` and logs the link instead of sending it. This screen
 * says exactly that, and because the server hands the link back to the caller, sign-in completes here
 * instead of pretending to wait for an email that was never sent. The day a provider is configured
 * the same two calls are still the whole flow and this note disappears.
 */
@Composable
fun SignInScreen(onSignedIn: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var step by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()
    val canSubmit = email.contains('@') && email.contains('.') && !busy

    fun signInWith(address: String) {
        scope.launch {
            busy = true
            error = null
            step = "Asking the server for a sign-in link"
            try {
                val link = NetworkModule.apiService.requestMagicLink(RequestMagicLinkDto(address))
                val token = tokenFrom(link.loginUrl)
                if (token == null) {
                    step = ""
                    error = "The server answered without a link this build can use. Try again."
                    return@launch
                }
                step = if (link.delivery == "console") {
                    "No mail provider is configured, so the link was written to the server log and " +
                        "handed back here. Signing in with it now."
                } else {
                    "Link sent. Signing in."
                }
                val session = NetworkModule.apiService.verifyMagicLink(
                    VerifyMagicLinkDto(token = token, email = address)
                )
                SessionStore.save(session)
                step = ""
                onSignedIn()
            } catch (e: HttpException) {
                step = ""
                error = when (e.code()) {
                    429 -> "Too many attempts from this device. Wait a minute and try again."
                    400 -> "That email address was not accepted. Check it and try again."
                    else -> "The server answered ${e.code()}. Try again in a moment."
                }
            } catch (e: Exception) {
                step = ""
                error = "Could not reach the server. Check the connection and try again."
            } finally {
                busy = false
            }
        }
    }

    fun signIn() = signInWith(email.trim())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Paper)
            .verticalScroll(rememberScrollState())
    ) {
        // The black header block. Boldness from colour and type, both flat.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Ink)
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "TRIPP'IN",
                style = TrippinType.Display,
                color = Paper
            )
            Text(
                text = "Sign in to plan a trip with your friends",
                style = TrippinType.Body,
                color = Paper.copy(alpha = 0.85f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "YOUR EMAIL",
                    style = TrippinType.Label,
                    color = InkMuted
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it.trim() },
                    singleLine = true,
                    enabled = !busy,
                    placeholder = { Text("you@example.com", style = TrippinType.Body) },
                    textStyle = TrippinType.Body,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Done
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, Ink, RoundedCornerShape(8.dp)),
                    colors = trippinFieldInk(container = Panel)
                )
            }

            TrippinButton(
                text = "Send sign-in link",
                onClick = { signIn() },
                enabled = canSubmit
            )

            if (busy || step.isNotBlank()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = AccentCrimson)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = step.ifBlank { "Working" },
                        style = TrippinType.Caption,
                        color = InkMuted
                    )
                }
            }

            error?.let {
                Text(
                    text = it,
                    style = TrippinType.Body,
                    color = Ink
                )
            }

            Text(
                text = "No mail provider is configured in this build. The server writes the sign-in " +
                    "link to its log and hands the same link back to this app, so signing in works " +
                    "here without an inbox. Once a provider is wired up, the same button emails the " +
                    "link instead.",
                style = TrippinType.Caption,
                color = InkMuted
            )

            Text(
                text = "Signing in keeps your trips on your account rather than on this device, so " +
                    "the same account sees the same trips anywhere.",
                style = TrippinType.Caption,
                color = InkMuted
            )
        }

        Box(modifier = Modifier.height(24.dp))
    }
}

/**
 * The single use token out of the link the server issued, read from the link's own query string.
 * Nothing is guessed: if the parameter is not there this returns null and the screen says so.
 */
private fun tokenFrom(loginUrl: String): String? {
    val query = loginUrl.substringAfter('?', missingDelimiterValue = "")
    if (query.isBlank()) return null
    return query
        .split('&')
        .firstOrNull { it.startsWith("token=") }
        ?.removePrefix("token=")
        ?.takeIf { it.length >= 16 }
}
