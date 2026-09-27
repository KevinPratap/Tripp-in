package com.trippin.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.PillTone
import com.trippin.core.design.StatusPill
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinOutlineButton
import com.trippin.core.design.TrippinTextField
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    viewModel: SignInViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors

    LaunchedEffect(state.signedIn) { if (state.signedIn) onSignedIn() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.paper)
            .statusBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Spacer(Modifier.height(48.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusPill(text = "Verified only", tone = PillTone.ACCENT)
        }
        Spacer(Modifier.height(16.dp))
        Text("Tripp'in AI", style = TrippinType.Display, color = colors.ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "Honest, checked itineraries. Every stop is verified against real opening hours and travel times, and nothing is invented.",
            style = TrippinType.Body,
            color = colors.inkMuted
        )

        Spacer(Modifier.height(32.dp))

        TrippinCard {
            Column(Modifier.padding(20.dp)) {
                if (state.stage == SignInStage.ENTER_EMAIL) {
                    EnterEmail(state, viewModel)
                } else {
                    EnterCode(state, viewModel)
                }
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, style = TrippinType.Body, color = colors.danger)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = colors.inkMuted, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                "No password. We send a one-time sign-in code.",
                style = TrippinType.Caption,
                color = colors.inkMuted
            )
        }
    }
}

@Composable
private fun EnterEmail(state: SignInUiState, viewModel: SignInViewModel) {
    Text("Sign in", style = TrippinType.Title, color = TrippinTheme.colors.ink)
    Spacer(Modifier.height(4.dp))
    Text(
        "Enter your email and we send a one-time code.",
        style = TrippinType.Body,
        color = TrippinTheme.colors.inkMuted
    )
    Spacer(Modifier.height(16.dp))
    TrippinTextField(
        value = state.email,
        onValueChange = viewModel::onEmailChange,
        modifier = Modifier.fillMaxWidth(),
        label = "Email",
        placeholder = "you@example.com",
        keyboardType = KeyboardType.Email
    )
    Spacer(Modifier.height(16.dp))
    TrippinButton(
        text = "Send my code",
        onClick = viewModel::requestLink,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canRequest,
        loading = state.loading
    )
}

@Composable
private fun EnterCode(state: SignInUiState, viewModel: SignInViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TrippinIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Change email",
            onClick = viewModel::backToEmail
        )
        Spacer(Modifier.width(4.dp))
        Text("Enter your code", style = TrippinType.Title, color = TrippinTheme.colors.ink)
    }
    state.deliveryNote?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = TrippinType.Body, color = TrippinTheme.colors.inkMuted)
    }
    Spacer(Modifier.height(16.dp))
    TrippinTextField(
        value = state.token,
        onValueChange = viewModel::onTokenChange,
        modifier = Modifier.fillMaxWidth(),
        label = "Sign-in code",
        placeholder = "Paste the code from your link"
    )
    Spacer(Modifier.height(16.dp))
    TrippinButton(
        text = "Verify and continue",
        onClick = viewModel::verify,
        modifier = Modifier.fillMaxWidth(),
        enabled = state.canVerify,
        loading = state.loading
    )
    Spacer(Modifier.height(8.dp))
    TrippinOutlineButton(
        text = "Send a new code",
        onClick = viewModel::requestLink,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.loading
    )
}
