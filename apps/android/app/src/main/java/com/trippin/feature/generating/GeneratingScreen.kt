package com.trippin.feature.generating

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
import com.trippin.core.design.NeutralInkSurface
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
 *
 * The poll also has a budget, and the readout says so when it runs out rather than going on
 * spinning: see [pollSpent] below.
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
    // The poll below has a budget of 60 attempts, about a second and a half apart, so roughly 90
    // seconds. Until this flag existed the screen stopped asking at the end of that budget and went
    // on drawing the ring and the last sentence it held, which read as work continuing on a plan
    // nothing was asking about any more. The flag is what lets the readout say the budget is spent.
    var pollSpent by remember { mutableStateOf(false) }
    // Whether the LAST attempt got an answer, which is the only thing that decides what may be said
    // once the budget is spent: a server that answered is still building, and an attempt that threw
    // means no plan arrived. It is set on every attempt and not only on a failure.
    var lastAttemptAnswered by remember { mutableStateOf(false) }
    // Incremented by Retry, which is the only thing that starts the poll again.
    var attemptTick by remember { mutableStateOf(0) }

    LaunchedEffect(tripId, attemptTick) {
        statusMessage = "Getting the plan started..."
        progress = null
        buildFailed = false
        pollSpent = false
        lastAttemptAnswered = false
        var attempts = 0
        while (attempts < 60) {
            try {
                val statusRes = com.trippin.core.network.NetworkModule.apiService.getTripStatus(tripId)
                statusMessage = statusRes.currentStepMessage.ifBlank { "Working on the plan..." }
                // The server's own number, held inside the range the control accepts. It is capped
                // and never raised, so a run the server reports as 0 percent draws as 0 percent.
                progress = (statusRes.progressPercentage / 100f).coerceIn(0f, 1f)
                lastAttemptAnswered = true

                if (statusRes.status == "READY" || statusRes.status == "COMPLETED") {
                    delay(400)
                    onGenerationComplete(tripId)
                    break
                } else if (statusRes.status == "FAILED") {
                    // The status field this follows is a raw exception string, so it is not read.
                    buildFailed = true
                    break
                }
            } catch (_: Exception) {
                // No cause is named here, which is this file's own rule stated in the docblock: the
                // one catch covers a dead network, a rejected session and a trip that is not there
                // alike, so the line says only what the app is doing, which is waiting.
                lastAttemptAnswered = false
                statusMessage = "Waiting for the server to answer..."
            }
            delay(1500)
            attempts++
        }
        // Only the budget running out reaches this line: every break above leaves attempts below 60,
        // so a run that finishes or fails never shows the spent readout.
        if (attempts >= 60) pollSpent = true
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
            } else if (pollSpent) {
                /* The budget is spent, so this screen is no longer asking the server anything. What
                 * it may say depends on the last attempt and on nothing else: a server that
                 * answered is still building, and an attempt that threw means the plan never
                 * arrived. Retry is the only way back into the poll, and the ink follows the same
                 * flag, because crimson marks a failed action and not a plan still being written. */
                Text(
                    text = if (lastAttemptAnswered) "Still building the plan" else "Could not load the plan",
                    style = TrippinType.Title,
                    color = if (lastAttemptAnswered) Ink else DangerCrimson
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { attemptTick++ },
                    colors = trippinButtonColors()
                ) {
                    Text("Retry", style = TrippinType.Label)
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
 *
 * The track is named here and not left to the library: a determinate Material indicator paints the
 * unfilled remainder with the scheme's secondaryContainer, which this app never maps, so the rest of
 * the ring arrived in Material's own lavender #E8DEF8 on a parchment page. A track is neither good
 * nor bad, so it takes NeutralInkSurface, the token design system section 1 gives that meaning, the
 * surface the venue plate and a disabled button already draw. The indeterminate branch draws no
 * track at all, which is why it passes no track colour.
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
            strokeWidth = 6.dp,
            trackColor = NeutralInkSurface
        )
    }
}

/**
 * The bar under the stage sentence, determinate on the same rule as the ring. Both branches draw a
 * track, because a linear indicator always paints the unfilled remainder, so both name it: without
 * the argument that remainder came from the scheme's secondaryContainer, the same unmapped lavender
 * the ring above was drawing. Its stop indicator is not named here because Material draws it in the
 * bar's own colour, which is the accent.
 */
@Composable
private fun StatedProgressBar(progress: Float?) {
    val stated = progress
    val bar = Modifier
        .fillMaxWidth()
        .height(8.dp)
    if (stated == null) {
        LinearProgressIndicator(modifier = bar, color = AccentCrimson, trackColor = NeutralInkSurface)
    } else {
        LinearProgressIndicator(
            progress = { stated },
            modifier = bar,
            color = AccentCrimson,
            trackColor = NeutralInkSurface
        )
    }
}
