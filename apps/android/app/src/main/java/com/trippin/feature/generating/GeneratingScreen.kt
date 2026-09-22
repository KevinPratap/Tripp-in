package com.trippin.feature.generating

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.trippin.core.design.AccentCrimson
import com.trippin.core.design.DangerCrimson
import com.trippin.core.design.Ink
import com.trippin.core.design.InkMuted
import com.trippin.core.design.Paper
import com.trippin.core.design.TrippinType
import com.trippin.core.design.trippinButtonColors
import kotlinx.coroutines.delay

/**
 * The wait while a plan is built.
 *
 * This is a state and not a place. It has no bar, no name of its own and no route of its own any
 * more: the Plan screen draws it in place of a plan that does not exist yet, so the screen you wait
 * on is the screen you opened, with its own destination in the bar above. [embedded] is false only
 * for a caller that wants the whole window.
 *
 * Nothing here is invented. The stage sentence is the server's own status answer, and the ring and
 * the bar draw the server's own percentage and never a number past it. Until the server states one
 * they are indeterminate, which claims no progress at all, so a run the server reports as 0 percent
 * draws as 0 percent rather than as a fraction this screen guessed.
 *
 * A failure names no cause, because the only field that could carry one is a raw exception string.
 * getGenerationStatus returns errorMessage straight off the job, and the job's own writer of that
 * field is `error: err.message` in trips.service.ts, fed by the catch around processGenerationJob,
 * so what arrives is whatever threw: a Prisma message, a fetch failure, or the engine's own
 * developer wording. None of those was written for a person to read, so the failure draws the same
 * recipe Plan, Map and Today draw, a DangerCrimson title and a way past it, and prints no body.
 */
@Composable
fun GeneratingScreen(
    tripId: String,
    onGenerationComplete: (String) -> Unit,
    embedded: Boolean = false,
    modifier: Modifier = Modifier
) {
    var statusMessage by remember { mutableStateOf("Getting the plan started...") }
    // Null until the server states a percentage. A number this screen made up would be progress the
    // wire never reported, so the ring and the bar claim none until there is one to draw.
    var progress by remember { mutableStateOf<Float?>(null) }
    var buildFailed by remember { mutableStateOf(false) }

    LaunchedEffect(tripId) {
        var attempts = 0
        while (attempts < 60) {
            try {
                val statusRes = com.trippin.core.network.NetworkModule.apiService.getTripStatus(tripId)
                statusMessage = statusRes.currentStepMessage.ifBlank { "Working on the plan..." }
                // The server's own number, held inside the range the control accepts. It is capped
                // and never raised, so a run the server reports as 0 percent draws as 0 percent.
                progress = (statusRes.progressPercentage / 100f).coerceIn(0f, 1f)

                if (statusRes.status == "READY" || statusRes.status == "COMPLETED") {
                    delay(400)
                    onGenerationComplete(tripId)
                    break
                } else if (statusRes.status == "FAILED") {
                    // The status field this follows is a raw exception string, so it is not read.
                    buildFailed = true
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
        // Inside the Plan screen the surface takes the room its parent gives it, which is why that
        // caller passes a weight. On its own it fills the window. The content is the same either
        // way, so there is one readout and not two that can drift apart.
        modifier = if (embedded) modifier.fillMaxWidth() else Modifier.fillMaxSize(),
        color = Paper
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(if (embedded) 24.dp else 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (buildFailed) {
                Text(
                    text = "Could not build the plan",
                    style = TrippinType.Title,
                    color = DangerCrimson
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { onGenerationComplete(tripId) },
                    colors = trippinButtonColors()
                ) {
                    Text("Continue anyway", style = TrippinType.Label)
                }
            } else {
                StatedProgressRing(progress)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = "Building your plan",
                    style = TrippinType.Display,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = statusMessage,
                    style = TrippinType.Body,
                    color = InkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                StatedProgressBar(progress)
            }
        }
    }
}

/**
 * The ring, determinate only when the server has stated a percentage. With none it is Material's
 * indeterminate ring, which claims no fraction at all rather than a made up one.
 */
@Composable
private fun StatedProgressRing(progress: Float?) {
    val stated = progress
    if (stated == null) {
        CircularProgressIndicator(
            modifier = Modifier.size(72.dp),
            color = AccentCrimson,
            strokeWidth = 6.dp
        )
    } else {
        CircularProgressIndicator(
            progress = { stated },
            modifier = Modifier.size(72.dp),
            color = AccentCrimson,
            strokeWidth = 6.dp
        )
    }
}

/** The bar under the stage sentence, determinate on the same rule as the ring. */
@Composable
private fun StatedProgressBar(progress: Float?) {
    val stated = progress
    val bar = Modifier
        .fillMaxWidth()
        .height(8.dp)
    if (stated == null) {
        LinearProgressIndicator(modifier = bar, color = AccentCrimson)
    } else {
        LinearProgressIndicator(progress = { stated }, modifier = bar, color = AccentCrimson)
    }
}
