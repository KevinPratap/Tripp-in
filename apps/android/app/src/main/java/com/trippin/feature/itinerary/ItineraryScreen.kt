package com.trippin.feature.itinerary

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trippin.core.cache.SavedSpotsManager
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.*
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.ModifyItineraryRequestDto
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.PlaceSearchResultDto
import com.trippin.core.network.ReplanRequestDto
import com.trippin.core.network.TripDetailsDto
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    onOpenMap: (String) -> Unit,
    onNavigateToToday: (String) -> Unit
) {
    val cachedTrip = remember(tripId) { TripCacheManager.getTrip(tripId) }
    var tripDetails by remember { mutableStateOf(cachedTrip) }
    var isLoading by remember { mutableStateOf(cachedTrip == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editInstruction by remember { mutableStateOf("") }
    var isModifying by remember { mutableStateOf(false) }
    var modifyError by remember { mutableStateOf<String?>(null) }

    // Lock, Replan & Scrap states
    var isLocking by remember { mutableStateOf(false) }
    var isReplanning by remember { mutableStateOf(false) }
    var replanStatusMsg by remember { mutableStateOf<String?>(null) }
    var showScrapDialog by remember { mutableStateOf(false) }
    var isScrapping by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val loadTripData: (isManualRefresh: Boolean) -> Unit = { isManualRefresh ->
        scope.launch {
            try {
                if (isManualRefresh) {
                    isRefreshing = true
                } else if (tripDetails == null) {
                    isLoading = true
                }
                loadError = null
                val fetched = NetworkModule.apiService.getTripDetails(tripId)
                tripDetails = fetched
                TripCacheManager.putTrip(tripId, fetched)
            } catch (e: Exception) {
                if (tripDetails == null) {
                    loadError = e.message ?: "Could not load the plan"
                }
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(tripId) {
        loadTripData(false)
    }

    val isLocked = tripDetails?.trip?.isLocked == true
    val days = tripDetails?.itinerary?.days ?: emptyList()
    val destinationName = tripDetails?.trip?.destination ?: "Trip"

    // Money honesty. Every amount on this screen is printed in the currency its own stop declares,
    // never in the trip's currency, because the two have already disagreed on the wire (a JPY trip
    // whose stops were stored in USD). A stop that states an amount without a currency of its own
    // is not printed at all, and a day total is printed only when every priced stop in that day
    // states the same one. That is the same rule the backend now enforces when it serializes a
    // stop price: no amount travels without a named source and a confirmed provenance check.

    // Plain state for the header. Nothing is asserted that the loaded data does not show:
    // locked or not, how many days the plan has, or that there is no plan yet.
    val planSubtitle = when {
        isLocked -> "Locked by whoever set this up"
        isLoading -> "Loading the plan"
        days.isEmpty() -> "No plan yet"
        days.size == 1 -> "1 day planned"
        else -> "${days.size} days planned"
    }

    // Horizontal Pager state for smooth day-swiping gestures
    val pageCount = days.size.coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }

    // Haptic feedback when swiping between days
    LaunchedEffect(pagerState.currentPage) {
        if (days.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleToggleLock() {
        if (isLocking) return
        scope.launch {
            try {
                isLocking = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isLocked) {
                    NetworkModule.apiService.unlockTrip(tripId)
                } else {
                    NetworkModule.apiService.lockTrip(tripId)
                }
                val updated = NetworkModule.apiService.getTripDetails(tripId)
                tripDetails = updated
                TripCacheManager.putTrip(tripId, updated)
            } catch (e: Exception) {
                modifyError = e.message ?: "Could not change the lock"
            } finally {
                isLocking = false
            }
        }
    }

    fun triggerQuickReplan(intentKey: String) {
        if (isLocked || isReplanning) return
        scope.launch {
            try {
                isReplanning = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                replanStatusMsg = "Replanning..."
                NetworkModule.apiService.replanTrip(tripId, ReplanRequestDto(intent = intentKey))
                val updated = NetworkModule.apiService.getTripDetails(tripId)
                tripDetails = updated
                TripCacheManager.putTrip(tripId, updated)
                replanStatusMsg = "Plan updated."
            } catch (e: Exception) {
                replanStatusMsg = "Could not replan: ${e.message}"
            } finally {
                isReplanning = false
            }
        }
    }

    fun handleScrapTrip() {
        if (isScrapping) return
        scope.launch {
            try {
                isScrapping = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                NetworkModule.apiService.deleteTrip(tripId)
                TripCacheManager.invalidateTrip(tripId)
                showScrapDialog = false
                onNavigateBack()
            } catch (e: Exception) {
                modifyError = e.message ?: "Could not delete this trip"
                isScrapping = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = destinationName.uppercase(),
                            style = TrippinType.Heading
                        )
                        Text(
                            text = planSubtitle,
                            style = TrippinType.Caption,
                            color = if (isLocked) ComicBlack else ComicRed
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenMap(tripId) }) {
                        Icon(Icons.Default.Map, contentDescription = "Map")
                    }
                    IconButton(onClick = {
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Join the group for $destinationName on Tripp'in: https://web-production-a9ec6.up.railway.app/trip/$tripId/collab"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Invite the group to Tripp'in"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Invite the group", tint = ComicRed)
                    }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Today", fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null, tint = ComicRed) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToToday(tripId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLocked) "Unlock the plan" else "Lock the plan", fontWeight = FontWeight.Bold) },
                                leadingIcon = {
                                    Icon(
                                        if (isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = ComicBlack
                                    )
                                },
                                onClick = {
                                    showMoreMenu = false
                                    handleToggleLock()
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Delete trip", color = ComicRed, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ComicRed) },
                                onClick = {
                                    showMoreMenu = false
                                    showScrapDialog = true
                                }
                            )
                        }
                    }
                }

            )
        },
    ) { padding ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ComicRed)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading the plan...", fontWeight = FontWeight.Bold)
                }
            }
        } else if (loadError != null && tripDetails == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load the plan", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(loadError!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadTripData(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed)
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { loadTripData(true) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(ComicPaper)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Locked Plan Banner
                    if (isLocked) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                                .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                            color = ComicYellow,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = ComicBlack, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Locked by whoever set this up",
                                        style = TrippinType.Label,
                                        color = ComicBlack
                                    )
                                    Text(
                                        text = "Unlock it from the menu at the top right to change anything.",
                                        style = TrippinType.Body,
                                        color = ComicBlack
                                    )
                                }
                            }
                        }
                    }

                    // Quick Replan Directives Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickReplanChip(
                            label = "Rain",
                            icon = Icons.Default.Thunderstorm,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("rain") }
                        )
                        QuickReplanChip(
                            label = "Running late",
                            icon = Icons.Default.AccessTime,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("running-late") }
                        )
                        QuickReplanChip(
                            label = "Tired",
                            icon = Icons.Default.Hotel,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("tired") }
                        )
                        QuickReplanChip(
                            label = "Trim the plan",
                            icon = Icons.Default.AttachMoney,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("budget-cut") }
                        )
                    }

                    if (replanStatusMsg != null) {
                        Text(
                            text = replanStatusMsg ?: "",
                            style = MaterialTheme.typography.labelSmall,
                            color = ComicRed,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )
                    }

                    // Day selector tabs (synced with HorizontalPager)
                    if (days.isNotEmpty()) {
                        ScrollableTabRow(
                            selectedTabIndex = pagerState.currentPage.coerceIn(0, days.size - 1),
                            edgePadding = 16.dp,
                            containerColor = ComicPaper
                        ) {
                            days.forEachIndexed { index, day ->
                                val isSelected = pagerState.currentPage == index
                                Tab(
                                    selected = isSelected,
                                    onClick = {
                                        scope.launch {
                                            pagerState.animateScrollToPage(index)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = "DAY ${day.dayIndex}",
                                            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                            color = if (isSelected) ComicRed else ComicBlack
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Swipable Native Horizontal Pager for Days
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) { pageIdx ->
                        val day = days.getOrNull(pageIdx)
                        if (day != null) {
                            val activities = day.activities
                            val visitedCount = remember(activities) {
                                TripCacheManager.getVisitedCountForDay(activities.map { it.id })
                            }
                            val pricedStops = remember(activities) {
                                activities.mapNotNull { it.statedMoney() }
                            }
                            val dayCurrency = remember(pricedStops) {
                                pricedStops.map { it.currency }.distinct().singleOrNull()
                            }
                            val dayTotal = remember(pricedStops, dayCurrency) {
                                if (dayCurrency == null) null else pricedStops.sumOf { it.value }
                            }

                            LazyColumn(
                                // The plan's action now sits in a reserved band below this list, so
                                // the list only needs its own breathing room at the bottom. The old
                                // 96dp existed to let the last card scroll out from under a floating
                                // button, which never helped a card mid-list.
                                contentPadding = PaddingValues(
                                    start = 16.dp,
                                    end = 16.dp,
                                    top = 16.dp,
                                    bottom = 16.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Day Summary Card
                                item {
                                     Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                         Surface(
                                             modifier = Modifier
                                                 .fillMaxWidth()
                                                 .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                                             color = ComicPanel,
                                             shape = RoundedCornerShape(8.dp)
                                         ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Day ${day.dayIndex} · ${day.date}",
                                                    style = TrippinType.Label,
                                                    color = ComicRed
                                                )
                                                if (dayTotal != null && dayTotal > 0) {
                                                    Text(
                                                        text = "Stops total " +
                                                            formatAmount(dayTotal, dayCurrency),
                                                        style = TrippinType.Caption,
                                                        color = ComicBlack
                                                    )
                                                }
                                            }

                                            if (!day.summary.isNullOrBlank()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = day.summary,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = ComicBlack
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${activities.size} ${if (activities.size == 1) "stop" else "stops"} · $visitedCount visited",
                                                    style = TrippinType.Caption,
                                                    color = ComicMuted
                                                )
                                                Text(
                                                    text = "Swipe for other days",
                                                    style = TrippinType.Caption,
                                                    color = ComicMuted
                                                )
                                            }

                                            // Shown when stops below do carry an amount but it
                                            // cannot be totalled, so the missing total is
                                            // explained instead of silently skipped.
                                            if (dayTotal == null &&
                                                activities.any { it.estimatedCost != null }
                                            ) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = "No day total: some stops here do not " +
                                                        "say which currency their amount is in.",
                                                    style = TrippinType.Body,
                                                    color = ComicMuted
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                                // Activity Cards
                                itemsIndexed(activities) { actIdx, act ->
                                    ActivityComicCard(
                                        activity = act,
                                        index = actIdx + 1,
                                        onNavigateClick = {
                                            // Exact coordinates when the venue has them, because that
                                            // navigates to the place itself. A search for the title
                                            // would be a guess dressed as a route. The title is only
                                            // used when there are no coordinates at all.
                                            val point = act.place?.location
                                            val exact = point
                                                ?.takeIf { it.latitude != 0.0 || it.longitude != 0.0 }
                                                ?.let { String.format(java.util.Locale.US, "%f,%f", it.latitude, it.longitude) }
                                            val query = Uri.encode(exact ?: "${act.title} $destinationName")
                                            val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // The plan's action is part of the layout rather than floating over it. The
                    // audit's defect was this button covering stop 02's address mid-list, and a
                    // bottom content padding could never fix that: content still scrolled under a
                    // floating button. A reserved band means the list's viewport ends above the
                    // button, so no stop card can render beneath it at any scroll position.
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        color = ComicPaper
                    ) {
                        TrippinButton(
                            text = if (isLocked) "Plan is locked" else "Change the plan",
                            enabled = !isLocked,
                            onClick = { showEditDialog = true }
                        )
                    }
                }
            }
        }

        // Conversational Edit Modal
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Change the plan", fontWeight = FontWeight.Black) },
                text = {
                    Column {
                        Text(
                            "Say what you want different. For example: shift the museum to the afternoon, or add a coffee break at 3pm.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editInstruction,
                            onValueChange = { editInstruction = it },
                            placeholder = { Text("e.g. Add a coffee break at 3pm") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp)
                        )
                        modifyError?.let { message ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                message,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        enabled = !isModifying && editInstruction.isNotBlank(),
                        onClick = {
                            val instruction = editInstruction.trim()
                            val itineraryId = tripDetails?.itinerary?.id
                            if (instruction.isBlank() || itineraryId == null) return@Button
                            scope.launch {
                                try {
                                    isModifying = true
                                    modifyError = null
                                    NetworkModule.apiService.modifyItinerary(
                                        itineraryId,
                                        ModifyItineraryRequestDto(instruction = instruction)
                                    )
                                    val updated = NetworkModule.apiService.getTripDetails(tripId)
                                    tripDetails = updated
                                    TripCacheManager.putTrip(tripId, updated)
                                    editInstruction = ""
                                    showEditDialog = false
                                } catch (e: Exception) {
                                    modifyError = e.message ?: "Could not apply that change"
                                } finally {
                                    isModifying = false
                                }
                            }
                        }
                    ) {
                        if (isModifying) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Apply", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Scrap Ticket Confirmation Dialog
        if (showScrapDialog) {
            AlertDialog(
                onDismissRequest = { if (!isScrapping) showScrapDialog = false },
                title = { Text("Delete this trip", fontWeight = FontWeight.Black) },
                text = {
                    Text(
                        "This deletes the trip and its plan. It cannot be undone.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        enabled = !isScrapping,
                        onClick = { handleScrapTrip() }
                    ) {
                        if (isScrapping) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Delete", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isScrapping,
                        onClick = { showScrapDialog = false }
                    ) {
                        Text("Keep")
                    }
                }
            )
        }
    }
}

@Composable
fun QuickReplanChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.border(
            width = 1.5.dp,
            color = if (enabled) ComicBlack else ComicBlack.copy(alpha = 0.3f),
            shape = RoundedCornerShape(6.dp)
        ),
        color = if (enabled) ComicPanel else ComicPaper,
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) ComicRed else ComicMuted,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = TrippinType.Caption,
                color = if (enabled) ComicBlack else ComicMuted
            )
        }
    }
}

@Composable
fun ActivityComicCard(
    activity: ActivityDto,
    index: Int,
    onNavigateClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    var isVisited by remember {
        mutableStateOf(TripCacheManager.isActivityVisited(activity.id))
    }

    val photoUrl = activity.effectivePhotoUrl
    val place = activity.place
    val rawAddress = place?.formattedAddress.orEmpty()

    // An address is either navigable or it is not printed as one. A prefecture repeated twice, or a
    // ward with no street, is not something a traveller standing on a pavement can walk to, and the
    // audit found exactly that case sitting next to a button promising to take them there.
    val address = rawAddress.takeIf { isStreetLevelAddress(it) }
    val coordinates = place?.location?.let { point ->
        if (point.latitude == 0.0 && point.longitude == 0.0) null
        else String.format(java.util.Locale.US, "%.5f, %.5f", point.latitude, point.longitude)
    }
    // Coordinates navigate honestly, which is why the action survives an unusable street address.
    val canNavigate = address != null || coordinates != null

    Column {
        if (activity.travelTimeFromPreviousMinutes > 0) {
            Row(
                modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(ComicRed, RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${activity.travelTimeFromPreviousMinutes} min travel from the stop before",
                    style = TrippinType.Caption,
                    color = ComicRed
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                color = if (isVisited) Color(0xFFF4F8F4) else ComicPaper,
                shape = RoundedCornerShape(10.dp)
            ) {
            Column {
                // Venue Image Header (Real Photography from Wikimedia / OSM)
                if (!photoUrl.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                    ) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = activity.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        )
                        // Photo Source Tag
                        Surface(
                            color = ComicBlack.copy(alpha = 0.75f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = if (photoUrl.contains("wikimedia.org")) "Photo: Wikimedia Commons" else "Photo: map data",
                                color = ComicPaper,
                                style = TrippinType.Caption,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    HorizontalDivider(thickness = 2.dp, color = ComicBlack)
                }

                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = ComicRed,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = String.format("%02d", index),
                                    color = ComicPaper,
                                    style = TrippinType.Caption,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${activity.startTime} - ${activity.endTime}",
                                style = TrippinType.Label,
                                color = ComicBlack
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Printed only in the currency this stop itself declares. A stop that
                            // carries an amount but no currency of its own prints nothing here,
                            // rather than borrowing the trip currency for a number it was not
                            // sent in.
                            val stopMoney = activity.statedMoney()
                            if (stopMoney != null) {
                                Text(
                                    text = formatAmount(stopMoney.value, stopMoney.currency),
                                    style = TrippinType.Label,
                                    color = ComicRed
                                )
                            }

                            // Visited Check-Off Badge
                            Surface(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isVisited = TripCacheManager.toggleActivityVisited(activity.id)
                                },
                                shape = RoundedCornerShape(4.dp),
                                color = if (isVisited) Color(0xFF16A34A) else ComicPanel,
                                modifier = Modifier.border(
                                    1.dp,
                                    if (isVisited) Color(0xFF16A34A) else ComicBlack,
                                    RoundedCornerShape(4.dp)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isVisited) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = "Mark visited",
                                        tint = if (isVisited) ComicPaper else ComicBlack,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = if (isVisited) "Visited" else "Mark visited",
                                        style = TrippinType.Caption,
                                        color = if (isVisited) ComicPaper else ComicBlack
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activity.title,
                        style = TrippinType.Heading,
                        color = ComicBlack
                    )

                    if (!activity.reason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activity.reason,
                            style = TrippinType.Body,
                            color = ComicBlack.copy(alpha = 0.8f)
                        )
                    }

                    if (address != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = address,
                            style = TrippinType.Body,
                            color = ComicMuted
                        )
                    } else {
                        // The map data has no street for this venue, so the screen says that and gives
                        // the one thing that is navigable instead of printing an address that is not.
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (coordinates != null) {
                                "No street address in the map data. It sits at $coordinates."
                            } else {
                                "No street address in the map data for this venue."
                            },
                            style = TrippinType.Body,
                            color = ComicMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Action buttons: Copy Address & Navigate in Maps
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val copyable = address ?: coordinates
                        if (copyable != null) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(copyable))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(
                                        context,
                                        if (address != null) "Address copied" else "Coordinates copied",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(1.dp, ComicBlack, RoundedCornerShape(6.dp))
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Address", modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        // Offered only when the app can actually get there. Never a button that leads
                        // nowhere, which is the same rule the Trips card now follows about invites.
                        if (canNavigate) {
                            OutlinedButton(
                                onClick = onNavigateClick,
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ComicRed),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take me there", style = TrippinType.Label)
                            }
                        }
                    }
                }
            }
        }
    }
}
}

/** An amount together with the currency the stop that carries it declares. */
private data class StatedMoney(val value: Double, val currency: String)

/**
 * Whether an address names a street, which is the only thing that makes it usable on foot.
 *
 * The audit found two kinds of unusable address on this screen: `東京都, 東京都, 日本`, where the
 * prefecture is repeated and nothing else is there, and `13, 台東区, 東京都, 日本`, a bare number in a
 * ward. Neither is walkable, and both sat next to a button offering to navigate. A street-level
 * address carries a number or a named street, and it does not repeat one of its own parts.
 */
private fun isStreetLevelAddress(address: String?): Boolean {
    if (address.isNullOrBlank()) return false
    val parts = address.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return false

    val repeatsItself = parts
        .groupingBy { it.lowercase() }
        .eachCount()
        .any { it.value > 1 }
    if (repeatsItself) return false

    return parts.any { part -> part.any { it.isDigit() } }
}

/**
 * The amount this stop may be printed with, or null when it may not be printed at all.
 *
 * A stop that states an amount but no currency of its own is not printable: there is no honest
 * symbol for it, and borrowing the trip currency would label the number as money it was not sent
 * in. The backend drops such an amount before it leaves the server, and this is the second half
 * of the same rule, on the screen.
 */
private fun ActivityDto.statedMoney(): StatedMoney? {
    val amount = estimatedCost ?: return null
    if (amount <= 0.0) return null
    val code = currency?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
    return StatedMoney(amount, code)
}

/** A whole amount with its currency, and no symbol at all when the currency is not known. */
private fun formatAmount(value: Double, currency: String?): String {
    val grouped = NumberFormat.getIntegerInstance(Locale.US).format(value.roundToLong())
    val code = currency?.trim()?.uppercase()
    val prefix = when (code) {
        null, "" -> ""
        "INR" -> "₹"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "USD" -> "$"
        else -> "$code "
    }
    return "$prefix$grouped"
}
