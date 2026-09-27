package com.trippin.feature.itinerary

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.LoadingBlock
import com.trippin.core.design.MessageState
import com.trippin.core.design.PillTone
import com.trippin.core.design.StatusPill
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinSegmentedTabs
import com.trippin.core.design.TrippinTextField
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.rememberIsOffline
import com.trippin.core.network.ItineraryDayDto
import com.trippin.feature.today.TodayContent
import kotlinx.coroutines.launch

private const val WEB_BASE = "https://web-production-a9ec6.up.railway.app"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    tripId: String,
    onBack: () -> Unit,
    onOpenMap: (String) -> Unit,
    viewModel: ItineraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    val offline = rememberIsOffline()

    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var subView by remember(tripId) { mutableStateOf(0) }

    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    val destinationName = state.details?.trip?.destination?.takeIf { it.isNotBlank() }
    val days = state.days
    val subtitle = when {
        state.isLocked -> "Locked"
        state.loading -> "Loading the plan"
        state.showBuildState && state.isBuilding -> "Building your plan"
        state.showBuildState && state.buildFailed -> "The plan could not be built"
        days.isEmpty() -> "No plan yet"
        days.size == 1 -> "1 day planned"
        else -> "${days.size} days planned"
    }

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = destinationName ?: "Plan",
                subtitle = subtitle,
                subtitleColor = if (state.isLocked) colors.good else colors.inkMuted,
                onBack = onBack,
                actions = {
                    TrippinIconButton(Icons.Default.Map, "Map", { onOpenMap(tripId) })
                    TrippinIconButton(Icons.Default.Share, "Invite the group", tint = colors.accent, onClick = {
                        val line = destinationName?.let { "Join the group for $it on Tripp'in" } ?: "Join the group on Tripp'in"
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "$line: $WEB_BASE/trip/$tripId/collab")
                        }
                        context.startActivity(Intent.createChooser(send, "Invite the group to Tripp'in"))
                    })
                    Box {
                        TrippinIconButton(Icons.Default.MoreVert, "More", { showMenu = true })
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }, containerColor = colors.panel) {
                            DropdownMenuItem(
                                text = { Text(if (state.isLocked) "Unlock the plan" else "Lock the plan", style = TrippinType.Label, color = colors.ink) },
                                leadingIcon = { Icon(if (state.isLocked) Icons.Default.LockOpen else Icons.Default.Lock, null, tint = colors.ink) },
                                onClick = { showMenu = false; viewModel.toggleLock() }
                            )
                            HorizontalDivider(color = colors.line)
                            DropdownMenuItem(
                                text = { Text("Delete trip", style = TrippinType.Label, color = colors.danger) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, null, tint = colors.danger) },
                                onClick = { showMenu = false; showDeleteDialog = true }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingBlock("Loading the plan")
                state.loadError != null && state.details == null -> MessageState(
                    icon = Icons.Default.Map,
                    title = "Could not load the plan",
                    body = state.loadError ?: "",
                    actionLabel = "Retry",
                    onAction = { viewModel.refresh() },
                    titleColor = colors.danger
                )
                state.showBuildState -> BuildStatePane(state, onDismiss = viewModel::dismissBuildState)
                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { viewModel.refresh() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(Modifier.fillMaxSize()) {
                        if (offline) {
                            Text(
                                "You are offline. Showing the plan your phone already has, so it may be out of date.",
                                style = TrippinType.Caption,
                                color = colors.inkMuted,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                        if (state.isLocked) LockBanner()

                        TrippinSegmentedTabs(
                            options = listOf("Days", "Today"),
                            selectedIndex = subView,
                            onOptionSelected = { subView = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )

                        if (subView == 1) {
                            TodayContent(
                                details = state.details,
                                visited = state.visited,
                                onToggleVisited = viewModel::toggleVisited,
                                onOpenMap = { onOpenMap(tripId) },
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            DaysPane(
                                state = state,
                                destinationName = destinationName,
                                onToggleVisited = viewModel::toggleVisited,
                                onReplan = viewModel::replan,
                                modifier = Modifier.weight(1f)
                            )
                            Box(Modifier.background(colors.paper).padding(horizontal = 16.dp, vertical = 12.dp)) {
                                TrippinButton(
                                    text = if (state.isLocked) "Plan is locked" else "Change the plan",
                                    onClick = { showEditDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !state.isLocked && !state.busy
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        ModifyDialog(
            busy = state.busy,
            onDismiss = { showEditDialog = false },
            onApply = { instruction -> viewModel.modify(instruction) { ok -> if (ok) showEditDialog = false } }
        )
    }
    if (showDeleteDialog) {
        DeleteDialog(busy = state.busy, onDismiss = { showDeleteDialog = false }, onConfirm = { viewModel.delete() })
    }
}

@Composable
private fun BuildStatePane(state: ItineraryUiState, onDismiss: () -> Unit) {
    val colors = TrippinTheme.colors
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (state.buildFailed) {
                Text("The plan could not be built", style = TrippinType.Title, color = colors.danger)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Nothing was saved. You can try again, or change the trip and rebuild.",
                    style = TrippinType.Body,
                    color = colors.inkMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                TrippinButton("Back to the trip", onDismiss, Modifier.width(220.dp))
            } else {
                androidx.compose.material3.CircularProgressIndicator(color = colors.accent)
                Spacer(Modifier.height(20.dp))
                Text("Building your plan", style = TrippinType.Title, color = colors.ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    state.generationStage ?: "Checking opening hours and travel times between every stop.",
                    style = TrippinType.Body,
                    color = colors.inkMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                state.generationProgress?.let { pct ->
                    Spacer(Modifier.height(12.dp))
                    Text("$pct%", style = TrippinType.Numeric, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun LockBanner() {
    val colors = TrippinTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .background(colors.goodSurface, TrippinTheme.shapes.badge)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Lock, null, tint = colors.good, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Locked. Unlock from the menu to change anything.", style = TrippinType.Body, color = colors.good)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DaysPane(
    state: ItineraryUiState,
    destinationName: String?,
    onToggleVisited: (String) -> Unit,
    onReplan: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = TrippinTheme.colors
    val days = state.days
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { days.size.coerceAtLeast(1) })

    Column(modifier) {
        // Quick replan chips
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val enabled = !state.isLocked && !state.busy
            TrippinChoiceChip("Rain", false, { if (enabled) onReplan("rain") }, leadingIcon = Icons.Default.Thunderstorm)
            TrippinChoiceChip("Running late", false, { if (enabled) onReplan("running-late") }, leadingIcon = Icons.Default.AccessTime)
            TrippinChoiceChip("Tired", false, { if (enabled) onReplan("tired") }, leadingIcon = Icons.Default.Hotel)
            TrippinChoiceChip("Trim the plan", false, { if (enabled) onReplan("budget-cut") }, leadingIcon = Icons.Default.ContentCut)
        }

        state.message?.let { msg ->
            Text(
                msg.text,
                style = TrippinType.Body,
                color = when (msg.tone) {
                    MessageTone.GOOD -> colors.good
                    MessageTone.WORKING -> colors.inkMuted
                    MessageTone.BAD -> colors.danger
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (days.isEmpty()) {
            MessageState(
                icon = Icons.Default.Map,
                title = "No plan yet",
                body = "This trip has no itinerary yet.",
                modifier = Modifier.weight(1f)
            )
            return@Column
        }

        // Day tabs synced to the pager
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            days.forEachIndexed { index, day ->
                TrippinChoiceChip(
                    text = "Day ${day.dayIndex}",
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                )
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth().weight(1f)) { page ->
            days.getOrNull(page)?.let { day ->
                DayList(day, state, destinationName, onToggleVisited, multiDay = days.size > 1)
            }
        }
    }
}

@Composable
private fun DayList(
    day: ItineraryDayDto,
    state: ItineraryUiState,
    destinationName: String?,
    onToggleVisited: (String) -> Unit,
    multiDay: Boolean
) {
    val colors = TrippinTheme.colors
    val activities = day.activities
    val priced = activities.mapNotNull { it.statedMoney() }
    val singleCurrency = priced.map { it.currency }.distinct().singleOrNull()
    val dayTotal = if (singleCurrency == null) null else priced.sumOf { it.value }
    val visitedCount = activities.count { it.id in state.visited }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            TrippinCard {
                Column(Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Day ${day.dayIndex} · ${day.date}", style = TrippinType.Label, color = colors.ink)
                        if (dayTotal != null && dayTotal > 0) {
                            Text("Stops total ${formatStatedAmount(dayTotal, singleCurrency)}", style = TrippinType.Caption, color = colors.ink)
                        }
                    }
                    if (!day.summary.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(day.summary, style = TrippinType.Body, color = colors.ink)
                    }
                    if (!day.weatherSummary.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(day.weatherSummary, style = TrippinType.Caption, color = colors.inkMuted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        val stopWord = if (activities.size == 1) "stop" else "stops"
                        Text("${activities.size} $stopWord · $visitedCount visited", style = TrippinType.Caption, color = colors.inkMuted)
                        if (multiDay) Text("Swipe for other days", style = TrippinType.Caption, color = colors.inkMuted)
                    }
                }
            }
        }
        items(activities.size) { i ->
            val act = activities[i]
            ActivityCard(
                activity = act,
                index = i + 1,
                visited = act.id in state.visited,
                onToggleVisited = { onToggleVisited(act.id) },
                destinationName = destinationName
            )
        }
    }
}

@Composable
private fun ModifyDialog(busy: Boolean, onDismiss: () -> Unit, onApply: (String) -> Unit) {
    val colors = TrippinTheme.colors
    var instruction by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = colors.panel,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
        title = { Text("Change the plan", style = TrippinType.Heading) },
        text = {
            Column {
                Text("Say what you want different. For example: shift the museum to the afternoon, or add a coffee break at 3pm.", style = TrippinType.Body, color = colors.inkMuted)
                Spacer(Modifier.height(12.dp))
                TrippinTextField(
                    value = instruction,
                    onValueChange = { instruction = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = false,
                    placeholder = "e.g. Add a coffee break at 3pm"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(instruction) }, enabled = !busy && instruction.isNotBlank()) {
                Text(if (busy) "Applying..." else "Apply", style = TrippinType.Label, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel", style = TrippinType.Label, color = colors.inkMuted) }
        }
    )
}

@Composable
private fun DeleteDialog(busy: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val colors = TrippinTheme.colors
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = colors.panel,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
        title = { Text("Delete this trip", style = TrippinType.Heading) },
        text = { Text("This deletes the trip and its plan. It cannot be undone.", style = TrippinType.Body, color = colors.inkMuted) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Deleting..." else "Delete", style = TrippinType.Label, color = colors.danger)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Keep", style = TrippinType.Label, color = colors.inkMuted) }
        }
    )
}
