package com.trippin.feature.stop

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.HeaderCreamMuted
import com.trippin.core.design.HeaderIconButton
import com.trippin.core.design.LoadingBlock
import com.trippin.core.design.MessageState
import com.trippin.core.design.PhotoHeader
import com.trippin.core.design.SectionLabel
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinOutlineButton
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.isStreetLevelAddress
import com.trippin.core.design.rememberCommitHaptic
import com.trippin.core.network.VerificationCheckDto
import com.trippin.feature.itinerary.launchAddStopToCalendar
import com.trippin.feature.itinerary.launchMaps
import com.trippin.feature.itinerary.statedMoney
import java.util.Locale

@Composable
fun StopScreen(
    onBack: () -> Unit,
    viewModel: StopViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val placement = state.placement

    when {
        state.loading -> Box(Modifier.fillMaxSize().background(colors.paper).statusBarsPadding()) {
            LoadingBlock("Loading the stop")
        }
        placement == null -> Box(Modifier.fillMaxSize().background(colors.paper).statusBarsPadding()) {
            MessageState(
                icon = Icons.Default.EventBusy,
                title = "This stop is no longer in the plan",
                body = "The plan changed since this was opened. Go back to see the current stops.",
                actionLabel = "Back to the plan",
                onAction = onBack
            )
        }
        else -> StopContent(placement, state, onBack, viewModel::toggleVisited, viewModel::vote)
    }
}

@Composable
private fun StopContent(
    placement: StopPlacement,
    state: StopUiState,
    onBack: () -> Unit,
    onToggleVisited: () -> Unit,
    onVote: (Int) -> Unit
) {
    val visited = state.visited
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val haptic = rememberCommitHaptic()
    val activity = placement.activity

    val address = activity.place?.formattedAddress?.takeIf { isStreetLevelAddress(it) }
    val coordinates = activity.place?.location
        ?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
        ?.let { String.format(Locale.US, "%.5f, %.5f", it.latitude, it.longitude) }

    Column(Modifier.fillMaxSize().background(colors.paper)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            PhotoHeader(
                title = activity.title,
                photoUrl = activity.effectivePhotoUrl,
                height = 280.dp,
                eyebrow = "Day ${placement.dayIndex} · Stop ${placement.position} of ${placement.stopsThatDay}",
                actions = { HeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack) },
                below = {
                    readableType(activity.type)?.let { Text(it, style = TrippinType.Label, color = HeaderCreamMuted) }
                }
            )

            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("${activity.startTime.take(5)} to ${activity.endTime.take(5)}", style = TrippinType.Numeric, color = colors.ink)
                    Spacer(Modifier.width(12.dp))
                    Text(durationLabel(activity.durationMinutes), style = TrippinType.Label, color = colors.inkMuted, modifier = Modifier.padding(bottom = 4.dp))
                }

                if (!activity.reason.isNullOrBlank()) {
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SectionLabel("Why it is in the plan")
                        Text(activity.reason, style = TrippinType.Body, color = colors.ink)
                    }
                }

                GroupVotes(state, activity.support, onVote)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Facts and where they came from", Modifier.padding(horizontal = 4.dp))
                    TrippinCard {
                        Column {
                            FactRow(
                                label = "Address",
                                value = address ?: coordinates?.let { "No street address in the map data. It sits at $it." }
                                    ?: "No street address in the map data for this venue.",
                                source = null,
                                tone = FactTone.UNCHECKED,
                                first = true
                            )
                            if (activity.travelTimeFromPreviousMinutes > 0 && placement.position > 1) {
                                FactRow(
                                    label = "Getting there",
                                    value = "${activity.travelTimeFromPreviousMinutes} min from the stop before",
                                    source = null,
                                    tone = FactTone.UNCHECKED
                                )
                            }
                            activity.statedMoney()?.let { money ->
                                FactRow(
                                    label = "Likely spend",
                                    value = "About ${formatStatedAmount(money.value, money.currency)}",
                                    source = "Estimated",
                                    tone = FactTone.ESTIMATED
                                )
                            }
                            activity.checks.forEach { check -> CheckRow(check) }
                        }
                    }
                    Text(
                        "A green label was checked against the source it names. Yellow is the plan's own estimate. Grey could not be checked.",
                        style = TrippinType.Caption,
                        color = colors.inkMuted,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TrippinOutlineButton(
                        text = "Add to calendar",
                        onClick = { launchAddStopToCalendar(context, activity, placement.dayDate) },
                        leadingIcon = Icons.Default.CalendarMonth,
                        contentColor = colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                    TrippinOutlineButton(
                        text = if (visited) "Visited" else "Mark visited",
                        onClick = { haptic(); onToggleVisited() },
                        leadingIcon = if (visited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentColor = if (visited) colors.good else colors.ink,
                        modifier = Modifier.weight(1f)
                    )
                }

                val copyable = address ?: coordinates
                if (copyable != null) {
                    TrippinOutlineButton(
                        text = if (address != null) "Copy address" else "Copy coordinates",
                        onClick = {
                            clipboard.setText(AnnotatedString(copyable))
                            haptic()
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                        leadingIcon = Icons.Default.ContentCopy,
                        contentColor = colors.ink,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .background(colors.paper)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .navigationBarsPadding()
        ) {
            TrippinButton(
                text = "Take me there",
                onClick = { launchMaps(context, activity, placement.destinationName) },
                leadingIcon = Icons.Default.Directions
            )
        }
    }
}

private enum class FactTone { CONFIRMED, ESTIMATED, UNCHECKED }

private fun toneOf(status: String): FactTone = when (status.lowercase()) {
    "confirmed" -> FactTone.CONFIRMED
    "estimated" -> FactTone.ESTIMATED
    else -> FactTone.UNCHECKED
}

@Composable
private fun CheckRow(check: VerificationCheckDto) {
    FactRow(
        label = check.label,
        value = check.details?.takeIf { it.isNotBlank() } ?: statusWord(check.status),
        source = check.source.ifBlank { "engine" },
        tone = toneOf(check.status)
    )
}

private fun statusWord(status: String): String = when (status.lowercase()) {
    "confirmed" -> "Confirmed"
    "estimated" -> "Estimated"
    else -> "Not checked"
}

@Composable
private fun FactRow(label: String, value: String, source: String?, tone: FactTone, first: Boolean = false) {
    val colors = TrippinTheme.colors
    val (fg, bg) = when (tone) {
        FactTone.CONFIRMED -> colors.good to colors.goodSurface
        FactTone.ESTIMATED -> colors.warn to colors.warnSurface
        FactTone.UNCHECKED -> colors.neutral to colors.neutralSurface
    }
    if (!first) Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(colors.hairline))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = TrippinType.Eyebrow, color = colors.inkMuted)
            Spacer(Modifier.height(3.dp))
            Text(value, style = TrippinType.Body, color = colors.ink)
        }
        if (source != null) {
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(source, style = TrippinType.Caption, color = fg)
            }
        }
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes <= 0 -> ""
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** "VIEWPOINT" and "fast_food" read as "Viewpoint" and "Fast food". Unknown or blank types show nothing. */
private fun readableType(type: String): String? =
    type.trim().takeIf { it.isNotBlank() && !it.equals("activity", ignoreCase = true) }
        ?.replace('_', ' ')
        ?.lowercase(Locale.US)
        ?.replaceFirstChar { it.titlecase(Locale.US) }

/**
 * What the group thinks of this stop. The tally names who voted which way, so "2 keep, 1 skip" is
 * never a mystery, and the buttons show this phone's own vote. Pressing your vote again takes it back.
 */
@Composable
private fun GroupVotes(state: StopUiState, support: com.trippin.core.network.StopSupportDto?, onVote: (Int) -> Unit) {
    val colors = TrippinTheme.colors
    val votes = state.votes
    val keepers = votes?.voters?.filterValues { it == 1 }?.keys.orEmpty().sorted()
    val skippers = votes?.voters?.filterValues { it == -1 }?.keys.orEmpty().sorted()
    val mine = state.myVote

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionLabel("The group", Modifier.padding(horizontal = 4.dp))
        TrippinCard(tier = com.trippin.core.design.SurfaceTier.RAISED) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (support != null && support.total > 0) {
                    val against = support.against
                    Text(
                        "Matches the interests of ${support.want} of ${support.total} ${if (support.total == 1) "traveller" else "travellers"}." +
                            when (against.size) {
                                0 -> ""
                                1 -> " ${against[0]} listed something here as a dislike."
                                else -> " ${against.dropLast(1).joinToString(", ")} and ${against.last()} listed something here as a dislike."
                            },
                        style = TrippinType.Body,
                        color = colors.ink
                    )
                }
                when {
                    !state.votesLoaded -> Text("Loading votes", style = TrippinType.Caption, color = colors.inkMuted)
                    keepers.isEmpty() && skippers.isEmpty() -> Text("No votes yet. Say whether you want to keep it.", style = TrippinType.Body, color = colors.inkMuted)
                    else -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (keepers.isNotEmpty()) Text("Keep: ${keepers.joinToString(", ")}", style = TrippinType.Label, color = colors.good)
                        if (skippers.isNotEmpty()) Text("Skip: ${skippers.joinToString(", ")}", style = TrippinType.Label, color = colors.danger)
                    }
                }
                if (state.voterName != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        VoteButton("Keep it", selected = mine == 1, enabled = !state.voting, modifier = Modifier.weight(1f)) { onVote(1) }
                        VoteButton("Skip it", selected = mine == -1, enabled = !state.voting, modifier = Modifier.weight(1f)) { onVote(-1) }
                    }
                    Text(
                        if (mine != null) "Voting as ${state.voterName}. Tap your vote again to take it back." else "Voting as ${state.voterName}.",
                        style = TrippinType.Caption,
                        color = colors.inkMuted
                    )
                }
                state.voteError?.let { Text(it, style = TrippinType.Caption, color = colors.danger) }
                votes?.comments?.takeIf { it.isNotEmpty() }?.let { comments ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        comments.takeLast(5).forEach { c ->
                            Text("${c.voterName}: ${c.text}", style = TrippinType.Body, color = colors.ink)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VoteButton(text: String, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    com.trippin.core.design.TrippinSurface(
        modifier = modifier,
        shape = TrippinTheme.shapes.button,
        background = if (selected) colors.ink else colors.panel,
        borderColor = if (selected) colors.ink else colors.controlEdge,
        enabled = enabled,
        onClick = onClick
    ) {
        Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.Center) {
            Text(text, style = TrippinType.Label, color = if (selected) colors.paper else colors.ink)
        }
    }
}
