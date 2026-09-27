package com.trippin.feature.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CardTravel
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.trippin.core.design.MessageState
import com.trippin.core.design.PillTone
import com.trippin.core.design.PlacePlate
import com.trippin.core.design.SectionLabel
import com.trippin.core.design.StatusPill
import com.trippin.core.design.SurfaceTier
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTextField
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.currencySymbol
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.rememberIsOffline
import com.trippin.core.network.DestinationCardDto
import com.trippin.core.network.SavedTripSummaryDto
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val tripDateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onOpenTrip: (String) -> Unit,
    onOpenToday: (String) -> Unit,
    onStartPlanner: (String?) -> Unit,
    viewModel: TripsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val joinState by viewModel.joinState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val offline = rememberIsOffline()

    LaunchedEffect(joinState.joinedTripId) {
        joinState.joinedTripId?.let { id ->
            viewModel.consumeJoined()
            onOpenTrip(id)
        }
    }

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = "Your trips",
                subtitle = if (offline) "Offline. Showing what your phone has." else "Verified plans, checked end to end",
                actions = {
                    TrippinIconButton(
                        icon = Icons.Default.GroupAdd,
                        contentDescription = "Join a trip",
                        onClick = viewModel::openJoin
                    )
                    TrippinIconButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Plan a new trip",
                        tint = colors.accent,
                        onClick = { onStartPlanner(null) }
                    )
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.loading) {
                com.trippin.core.design.LoadingBlock("Loading your trips")
            } else if (state.myTrips.isEmpty() && state.home == null) {
                MessageState(
                    icon = Icons.Default.CardTravel,
                    title = "No trips yet",
                    body = "Plan your first verified itinerary. Every stop is checked against real hours and travel times.",
                    actionLabel = "Plan a trip",
                    onAction = { onStartPlanner(null) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { StartTripCard(onClick = { onStartPlanner(null) }) }

                    if (state.myTrips.isNotEmpty()) {
                        item { SectionLabel("Your trips", Modifier.padding(top = 4.dp)) }
                        items(state.myTrips, key = { it.id }) { trip ->
                            TripCard(
                                trip = trip,
                                onOpen = { onOpenTrip(trip.id) },
                                onOpenToday = { onOpenToday(trip.id) }
                            )
                        }
                    }

                    val recommended = state.home?.recommendedDestinations.orEmpty()
                    val popular = state.home?.popularDestinations.orEmpty()
                    if (recommended.isNotEmpty()) {
                        item { SectionLabel("Ideas for you", Modifier.padding(top = 8.dp)) }
                        item { DestinationRow(recommended, onStartPlanner) }
                    }
                    if (popular.isNotEmpty()) {
                        item { SectionLabel("Popular right now", Modifier.padding(top = 8.dp)) }
                        item { DestinationRow(popular, onStartPlanner) }
                    }
                }
            }
        }
    }

    if (joinState.open) {
        JoinDialog(state = joinState, viewModel = viewModel)
    }
}

@Composable
private fun StartTripCard(onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    TrippinCard(onClick = onClick, background = colors.accent) {
        Row(
            Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Plan a new trip", style = TrippinType.Title, color = colors.onAccent)
                Text("Tell us where and when. We build a checked plan.", style = TrippinType.Body, color = colors.onAccent)
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: SavedTripSummaryDto,
    onOpen: () -> Unit,
    onOpenToday: () -> Unit
) {
    val colors = TrippinTheme.colors
    val start = trip.startDate.parseDateOrNull()
    val end = trip.endDate.parseDateOrNull()
    val today = LocalDate.now()
    val isLive = start != null && end != null && !today.isBefore(start) && !today.isAfter(end)
    val dateLabel = when {
        start != null && end != null -> "${start.format(tripDateFormat)} to ${end.format(tripDateFormat)}"
        else -> null
    }
    val (statusText, statusTone) = statusPill(trip.itineraryStatus ?: trip.status)

    // The trip happening right now is the one thing on this screen worth leading with, so it is the
    // only card that lifts. The rest sit flat, which is what makes the lift mean anything.
    TrippinCard(
        tier = if (isLive) SurfaceTier.RAISED else SurfaceTier.FLAT,
        onClick = onOpen
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = trip.destinationName.ifBlank { "Untitled trip" },
                    style = TrippinType.Title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(text = statusText, tone = statusTone)
            }
            if (dateLabel != null) {
                Spacer(Modifier.height(4.dp))
                Text(dateLabel, style = TrippinType.Body, color = colors.inkMuted)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val stopWord = if (trip.stopCount == 1) "stop" else "stops"
                val dayWord = if (trip.dayCount == 1) "day" else "days"
                Text(
                    "${trip.dayCount} $dayWord · ${trip.stopCount} $stopWord",
                    style = TrippinType.Caption,
                    color = colors.inkMuted
                )
                val cost = trip.totalEstimatedCost
                if (cost != null && cost > 0 && !trip.currency.isNullOrBlank()) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Est. ${formatStatedAmount(cost, trip.currency)}",
                        style = TrippinType.Caption,
                        color = colors.ink
                    )
                }
            }
            if (isLive) {
                Spacer(Modifier.height(12.dp))
                com.trippin.core.design.TrippinOutlineButton(
                    text = "Today",
                    onClick = onOpenToday,
                    leadingIcon = Icons.Default.Today,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun DestinationRow(items: List<DestinationCardDto>, onPick: (String) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(items, key = { it.id }) { dest ->
            DestinationCard(dest, onClick = { onPick(dest.name) })
        }
    }
}

@Composable
private fun DestinationCard(dest: DestinationCardDto, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    TrippinCard(modifier = Modifier.width(220.dp), onClick = onClick) {
        Column {
            Box(Modifier.fillMaxWidth().height(120.dp)) {
                if (dest.imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = dest.imageUrl,
                        contentDescription = dest.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    )
                } else {
                    PlacePlate(title = dest.name, category = dest.country, height = 120.dp)
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(dest.name, style = TrippinType.Heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (dest.country.isNotBlank()) {
                    Text(dest.country, style = TrippinType.Caption, color = colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun JoinDialog(state: JoinUiState, viewModel: TripsViewModel) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!state.loading) viewModel.closeJoin() },
        containerColor = TrippinTheme.colors.panel,
        titleContentColor = TrippinTheme.colors.ink,
        textContentColor = TrippinTheme.colors.ink,
        title = { Text("Join a trip", style = TrippinType.Heading) },
        text = {
            Column {
                Text(
                    "Paste the invite code someone shared, and the name you want the group to see.",
                    style = TrippinType.Body,
                    color = TrippinTheme.colors.inkMuted
                )
                Spacer(Modifier.height(12.dp))
                TrippinTextField(
                    value = state.token,
                    onValueChange = viewModel::onJoinTokenChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Invite code"
                )
                Spacer(Modifier.height(8.dp))
                TrippinTextField(
                    value = state.name,
                    onValueChange = viewModel::onJoinNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = "Your name"
                )
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = TrippinType.Body, color = TrippinTheme.colors.danger)
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = viewModel::submitJoin, enabled = state.canJoin) {
                Text("Join", style = TrippinType.Label, color = TrippinTheme.colors.accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = viewModel::closeJoin, enabled = !state.loading) {
                Text("Cancel", style = TrippinType.Label, color = TrippinTheme.colors.inkMuted)
            }
        }
    )
}

private fun String.parseDateOrNull(): LocalDate? =
    takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

private fun statusPill(status: String): Pair<String, PillTone> = when (status.uppercase()) {
    "VERIFIED" -> "Verified" to PillTone.GOOD
    "DRAFT" -> "Draft" to PillTone.WARN
    "ARCHIVED" -> "Archived" to PillTone.NEUTRAL
    "GENERATING" -> "Building" to PillTone.ACCENT
    "FAILED" -> "Failed" to PillTone.DANGER
    else -> (status.ifBlank { "Trip" }.lowercase().replaceFirstChar { it.uppercase() }) to PillTone.NEUTRAL
}
