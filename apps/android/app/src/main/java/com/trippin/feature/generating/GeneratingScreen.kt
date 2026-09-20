package com.trippin.feature.generating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trippin.core.design.TrippinType
import kotlinx.coroutines.delay

@Composable
fun GeneratingScreen(
    tripId: String,
    onGenerationComplete: (String) -> Unit
) {
    var statusMessage by remember { mutableStateOf("Getting the plan started...") }
    var progress by remember { mutableFloatStateOf(0.10f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tripId) {
        var attempts = 0
        while (attempts < 60) {
            try {
                val statusRes = com.trippin.core.network.NetworkModule.apiService.getTripStatus(tripId)
                statusMessage = statusRes.currentStepMessage.ifBlank { "Working on the plan..." }
                progress = (statusRes.progressPercentage / 100f).coerceIn(0.1f, 1.0f)

                if (statusRes.status == "READY" || statusRes.status == "COMPLETED") {
                    delay(400)
                    onGenerationComplete(tripId)
                    break
                } else if (statusRes.status == "FAILED") {
                    errorMessage = statusRes.errorMessage ?: "The plan could not be built."
                    break
                }
            } catch (e: Exception) {
                statusMessage = "Trying to reach the server..."
            }
            delay(1500)
            attempts++
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (errorMessage != null) {
                Text(
                    text = "Could not build the plan",
                    style = TrippinType.Title,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = errorMessage!!,
                    style = TrippinType.Body,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { onGenerationComplete(tripId) }) {
                    Text("Continue anyway", style = TrippinType.Label)
                }
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(72.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp
                )
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Building your plan",
                    style = TrippinType.Display,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusMessage,
                    style = TrippinType.Body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
