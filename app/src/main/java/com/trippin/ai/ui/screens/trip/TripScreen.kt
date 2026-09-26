package com.trippin.ai.ui.screens.trip

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.data.model.TripStatus
import com.trippin.ai.ui.components.AvatarStack
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.dateRange
import com.trippin.ai.ui.navigation.LocalSession
import com.trippin.ai.ui.theme.Trip

enum class TripTab(val key: String, val label: String) {
    OVERVIEW("overview", "Overview"),
    IDEAS("ideas", "Ideas & votes"),
    PLAN("plan", "Plan"),
    PEOPLE("people", "People"),
    ACTIVITY("activity", "Activity"),
}

/**
 * One trip, with collaboration attached to it: Overview, Ideas & votes, Plan, People, Activity.
 * All tabs share one [TripViewModel], so they stay consistent as friends make changes live.
 */
@Composable
fun TripScreen(
    container: AppContainer,
    tripId: String,
    initialTab: String?,
    onBack: () -> Unit,
    onToday: () -> Unit,
    onPrefs: () -> Unit,
    onLab: () -> Unit,
    onDeleted: () -> Unit,
) {
    val me = LocalSession.current
    val vm: TripViewModel = viewModel(key = "trip-$tripId-${me.uid}") { TripViewModel(container, tripId, me) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(TripTab.entries.firstOrNull { it.key == initialTab } ?: TripTab.OVERVIEW) }

    val trip = ui.trip
    if (trip == null) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            if (ui.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Trip.Ink) }
            } else {
                EmptyState(
                    "Trip not available",
                    "It may have been deleted, or you're no longer a member.",
                    action = { SignalButton("Back to my trips", onBack) },
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        // Header: title, dates, destination and the people — a shared trip is obvious at a glance.
        Column(Modifier.fillMaxWidth().background(Trip.Signal).statusBarsPadding().padding(bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Trip.Ink) }
                Kicker(dateRange(trip.startDate, trip.days) + " · ${trip.days} day${if (trip.days == 1) "" else "s"}", color = Trip.Ink, modifier = Modifier.weight(1f))
                Pill(statusLabel(trip.status, trip.isPast()), Trip.Ink, Trip.Paper, Modifier.padding(end = 12.dp))
            }
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(trip.title, style = MaterialTheme.typography.displaySmall, color = Trip.Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        trip.destination?.label ?: "Destination not decided yet",
                        style = MaterialTheme.typography.titleMedium, color = Trip.Ink, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (ui.members.size > 1 || trip.isGroup) AvatarStack(ui.members.map { it.uid to it.name })
                }
            }
        }

        ScrollableTabRow(
            selectedTabIndex = tab.ordinal,
            containerColor = Trip.Paper,
            contentColor = Trip.Ink,
            edgePadding = 12.dp,
            indicator = { positions ->
                TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(positions[tab.ordinal]), color = Trip.Signal, height = 4.dp)
            },
        ) {
            TripTab.entries.forEach { t ->
                val badge = when (t) {
                    TripTab.PLAN -> ui.pendingProposals.size.takeIf { it > 0 && vm.canManage }
                    else -> null
                }
                Tab(selected = tab == t, onClick = { tab = t }, text = {
                    Text(t.label + (badge?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                })
            }
        }

        Box(Modifier.weight(1f)) {
            when (tab) {
                TripTab.OVERVIEW -> OverviewTab(vm, ui, onGo = { tab = it }, onToday = onToday, onPrefs = onPrefs)
                TripTab.IDEAS -> IdeasTab(vm, ui)
                TripTab.PLAN -> PlanTab(vm, ui, onToday = onToday, onLab = onLab, onIdeas = { tab = TripTab.IDEAS }, onPrefs = onPrefs)
                TripTab.PEOPLE -> PeopleTab(vm, ui, onPrefs = onPrefs, onDeleted = onDeleted)
                TripTab.ACTIVITY -> ActivityTab(vm, ui)
            }
        }
    }
}

fun statusLabel(s: TripStatus, past: Boolean): String = when {
    past -> "COMPLETED"
    s == TripStatus.DECIDING_DESTINATION -> "VOTING"
    s == TripStatus.COLLECTING_IDEAS -> "PLANNING"
    s == TripStatus.PLAN_READY -> "REVIEW"
    else -> "CONFIRMED"
}
