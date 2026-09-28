package com.trippin.feature.triphub

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.CardListSkeleton
import com.trippin.core.design.HeaderCreamMuted
import com.trippin.core.design.HeaderIconButton
import com.trippin.core.design.MessageState
import com.trippin.core.design.PhotoHeader
import com.trippin.core.design.PillTone
import com.trippin.core.design.SectionLabel
import com.trippin.core.design.StatusPill
import com.trippin.core.design.SurfaceTier
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.feature.home.cityName
import com.trippin.feature.home.nowAndNext
import com.trippin.feature.home.parseIsoDate
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripHubScreen(
    onBack: () -> Unit,
    onOpenPlan: (String) -> Unit,
    onOpenToday: (String) -> Unit,
    onOpenMap: (String) -> Unit,
    onOpenGroup: (String) -> Unit,
    onOpenBudget: (String) -> Unit,
    onOpenHistory: (String) -> Unit,
    viewModel: TripHubViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    val tripId = viewModel.tripId

    LaunchedEffect(state.shareUrl) {
        state.shareUrl?.let { url ->
            val place = state.details?.trip?.destination?.let(::cityName)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, if (place != null) "Our $place plan on Tripp'in: $url" else url)
            }
            context.startActivity(Intent.createChooser(send, "Share the trip"))
            viewModel.consumeShareUrl()
        }
    }

    val details = state.details
    when {
        details == null && state.loading -> Box(Modifier.fillMaxSize().background(colors.paper).statusBarsPadding()) {
            CardListSkeleton(label = "Loading the trip")
        }
        details == null -> Box(Modifier.fillMaxSize().background(colors.paper).statusBarsPadding()) {
            MessageState(
                icon = Icons.Default.CalendarMonth,
                title = "Could not open this trip",
                body = state.loadError ?: "It is not on this phone yet. Connect and try again.",
                actionLabel = "Try again",
                onAction = { viewModel.refresh() }
            )
        }
        else -> Column(Modifier.fillMaxSize().background(colors.paper)) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                HubBody(
                    details = details,
                    state = state,
                    onBack = onBack,
                    onShare = viewModel::share,
                    onOpenPlan = { onOpenPlan(tripId) },
                    onOpenToday = { onOpenToday(tripId) },
                    onOpenMap = { onOpenMap(tripId) },
                    onOpenGroup = { onOpenGroup(tripId) },
                    onOpenBudget = { onOpenBudget(tripId) },
                    onOpenHistory = { onOpenHistory(tripId) },
                    onOpenSharing = viewModel::openShareSheet
                )
            }
            val live = isLive(details, LocalDate.now())
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.paper)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .navigationBarsPadding()
            ) {
                if (live) {
                    TrippinButton(text = "Open today", onClick = { onOpenToday(tripId) })
                } else {
                    TrippinButton(text = "See the plan", onClick = { onOpenPlan(tripId) })
                }
            }
        }
    }

    if (state.shareSheetOpen) {
        ShareSheet(
            token = state.details?.trip?.shareToken,
            busy = state.sharing || state.revoking,
            onSend = { viewModel.share() },
            onStop = viewModel::stopSharing,
            onDismiss = viewModel::closeShareSheet
        )
    }

    state.message?.let { msg ->
        LaunchedEffect(msg) {
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearMessage()
        }
    }
}

@Composable
private fun HubBody(
    details: TripDetailsDto,
    state: TripHubUiState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenPlan: () -> Unit,
    onOpenToday: () -> Unit,
    onOpenMap: () -> Unit,
    onOpenGroup: () -> Unit,
    onOpenBudget: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSharing: () -> Unit
) {
    val colors = TrippinTheme.colors
    val trip = details.trip
    val today = LocalDate.now()
    val start = parseIsoDate(trip.startDate)
    val end = parseIsoDate(trip.endDate)
    val days = details.itinerary?.days.orEmpty()
    val stopCount = days.sumOf { it.activities.size }
    val travellers = maxOf(trip.travelersCount, trip.travellers.size)
    val live = isLive(details, today)

    val eyebrow = listOfNotNull(
        if (start != null && end != null) "${start.format(shortDate)} to ${end.format(shortDate)}" else null,
        "$travellers ${if (travellers == 1) "traveller" else "travellers"}"
    ).joinToString(" · ")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PhotoHeader(
            title = cityName(trip.destination),
            photoUrl = trip.heroImageUrl,
            eyebrow = eyebrow,
            actions = {
                HeaderIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                if (state.sharing) {
                    Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HeaderCreamMuted, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    }
                } else {
                    HeaderIconButton(Icons.Default.Share, "Share the trip", onShare)
                }
            },
            below = { statusPill(details)?.let { (text, tone) -> StatusPill(text, tone) } }
        )

        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (live) {
                val todays = days.firstOrNull { parseIsoDate(it.date) == today }?.activities.orEmpty()
                val pair = nowAndNext(todays, LocalTime.now())
                (pair.now ?: pair.next)?.let { stop ->
                    UpNext(stop = stop, isNow = pair.now != null, onClick = onOpenToday)
                }
            } else if (start != null && start.isAfter(today)) {
                val daysUntil = ChronoUnit.DAYS.between(today, start)
                Text(
                    if (daysUntil == 1L) "Starts tomorrow" else "Starts in $daysUntil days",
                    style = TrippinType.Heading,
                    color = colors.ink,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }

            Stats(
                first = (if (days.isEmpty()) "-" else days.size.toString()) to "days",
                second = stopCount.toString() to "stops",
                third = travellers.toString() to if (travellers == 1) "traveller" else "travellers"
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("This trip", Modifier.padding(horizontal = 4.dp))
                TrippinCard {
                    Column {
                        HubRow(Icons.Default.CalendarMonth, "Itinerary", if (days.isEmpty()) "Not built yet" else "${days.size} days, day by day", onOpenPlan)
                        if (live) {
                            HubDivider()
                            HubRow(Icons.Default.Today, "Today", "The stops for today", onOpenToday)
                        }
                        HubDivider()
                        HubRow(Icons.Default.Map, "Map", "Every stop on a map", onOpenMap)
                        HubDivider()
                        HubRow(Icons.Default.Payments, "Budget", "What the group spent, and who owes whom", onOpenBudget)
                        HubDivider()
                        HubRow(Icons.Default.Group, "Group", "$travellers ${if (travellers == 1) "traveller" else "travellers"} and what each wants", onOpenGroup)
                        HubDivider()
                        HubRow(
                            Icons.Default.History,
                            "Plan history",
                            details.itinerary?.version?.let { v -> if (v <= 1) "The first version of the plan" else "Version $v, with every earlier one kept" } ?: "Every change to the plan, kept",
                            onOpenHistory
                        )
                        HubDivider()
                        HubRow(
                            Icons.Default.Link,
                            "Sharing",
                            if (trip.shareToken != null) "Link is on. Anyone with it can view." else "Off. Send a view-only link.",
                            onOpenSharing
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNext(stop: ActivityDto, isNow: Boolean, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    TrippinCard(tier = SurfaceTier.RAISED, onClick = onClick) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (isNow) "NOW" else "UP NEXT · TODAY", style = TrippinType.Eyebrow, color = if (isNow) colors.accent else colors.inkMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stop.startTime.take(5), style = TrippinType.Numeric, color = colors.ink)
                Spacer(Modifier.width(14.dp))
                Text(stop.title, style = TrippinType.Title, color = colors.ink, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            if (!isNow && stop.travelTimeFromPreviousMinutes > 0) {
                Text("${stop.travelTimeFromPreviousMinutes} min from the stop before", style = TrippinType.Caption, color = colors.inkMuted)
            }
        }
    }
}

@Composable
private fun Stats(first: Pair<String, String>, second: Pair<String, String>, third: Pair<String, String>) {
    val colors = TrippinTheme.colors
    TrippinCard {
        Row(Modifier.fillMaxWidth()) {
            listOf(first, second, third).forEachIndexed { i, (value, label) ->
                if (i > 0) Box(Modifier.width(1.dp).height(64.dp).background(colors.hairline))
                Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Text(value, style = TrippinType.Title, color = colors.ink)
                    Text(label, style = TrippinType.Caption, color = colors.inkMuted)
                }
            }
        }
    }
}

@Composable
private fun HubRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickableRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(colors.panelAlt),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = TrippinType.Heading, color = colors.ink)
            Text(subtitle, style = TrippinType.Caption, color = colors.inkMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.inkMuted)
    }
}

@Composable
private fun HubDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(TrippinTheme.colors.hairline))
}

private fun Modifier.clickableRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

private fun isLive(details: TripDetailsDto, today: LocalDate): Boolean {
    val start = parseIsoDate(details.trip.startDate) ?: return false
    val end = parseIsoDate(details.trip.endDate) ?: return false
    return !today.isBefore(start) && !today.isAfter(end)
}

/** Only what is true of the plan right now: verified, a draft with open warnings, or still building. */
private fun statusPill(details: TripDetailsDto): Pair<String, PillTone>? {
    val itinerary = details.itinerary ?: return when (details.trip.status.uppercase()) {
        "GENERATING" -> "Building the plan" to PillTone.NEUTRAL
        "FAILED" -> "Plan build failed" to PillTone.DANGER
        else -> null
    }
    return when (itinerary.status.uppercase()) {
        "VERIFIED" -> "Verified plan" to PillTone.GOOD
        "DRAFT" -> "Draft plan" to PillTone.WARN
        else -> null
    }
}

@Composable
private fun ShareSheet(
    token: String?,
    busy: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val url = token?.let(::shareUrlFor)
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (url != null) "Sharing is on" else "Share this trip", style = TrippinType.Title, color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (url != null) {
                        "Anyone with this link can see the plan. They cannot change it."
                    } else {
                        "Make a view-only link. Anyone you send it to can see the plan, and you can turn it off at any time."
                    },
                    style = TrippinType.Body,
                    color = colors.inkMuted
                )
                if (url != null) {
                    TrippinCard(background = colors.panelAlt) {
                        Text(url, style = TrippinType.Caption, color = colors.ink, modifier = Modifier.padding(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.trippin.core.design.TrippinOutlineButton(
                            text = "Copy",
                            onClick = {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(url))
                                android.widget.Toast.makeText(context, "Link copied", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            contentColor = colors.ink,
                            modifier = Modifier.weight(1f)
                        )
                        com.trippin.core.design.TrippinOutlineButton(
                            text = "Stop sharing",
                            onClick = onStop,
                            enabled = !busy,
                            contentColor = colors.danger,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onSend, enabled = !busy) {
                Text(if (url != null) "Send link" else "Make and send link", style = TrippinType.Label, color = colors.accent)
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Close", style = TrippinType.Label, color = colors.ink)
            }
        },
        containerColor = colors.panel
    )
}
