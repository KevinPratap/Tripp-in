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
                    loadError = e.message ?: "Failed loading itinerary details"
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
                modifyError = e.message ?: "Failed updating lock state"
            } finally {
                isLocking = false
            }
        }
    }

    fun triggerQuickReplan(intentKey: String, intentLabel: String) {
        if (isLocked || isReplanning) return
        scope.launch {
            try {
                isReplanning = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                replanStatusMsg = "Executing $intentLabel replan protocol..."
                NetworkModule.apiService.replanTrip(tripId, ReplanRequestDto(intent = intentKey))
                val updated = NetworkModule.apiService.getTripDetails(tripId)
                tripDetails = updated
                TripCacheManager.putTrip(tripId, updated)
                replanStatusMsg = "Verified $intentLabel schedule applied."
            } catch (e: Exception) {
                replanStatusMsg = "Replan: ${e.message}"
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
                modifyError = e.message ?: "Could not delete trip ticket"
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
                            text = "$destinationName Field Ticket".uppercase(),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 16.sp
                        )
                        Text(
                            text = if (isLocked) "SEALED // PLAN LOCKED" else "VERIFIED INK SCHEDULE",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isLocked) ComicBlack else ComicRed,
                            fontWeight = FontWeight.Bold
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
                                "Join our $destinationName squad collab: https://web-production-a9ec6.up.railway.app/trip/$tripId/collab"
                            )
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Invite Squad to Tripp'in"))
                    }) {
                        Icon(Icons.Default.Share, contentDescription = "Invite Squad", tint = ComicRed)
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
                                text = { Text("Today Field Mode", fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Navigation, contentDescription = null, tint = ComicRed) },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigateToToday(tripId)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (isLocked) "Unlock Schedule" else "Lock Schedule", fontWeight = FontWeight.Bold) },
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
                                text = { Text("Scrap Field Ticket", color = ComicRed, fontWeight = FontWeight.Bold) },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (!isLocked) {
                        showEditDialog = true
                    }
                },
                icon = {
                    if (isLocked) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                    } else {
                        Icon(Icons.Default.EditNote, contentDescription = null)
                    }
                },
                text = {
                    Text(
                        text = if (isLocked) "SCHEDULE LOCKED" else "REFINE SCHEDULE",
                        fontWeight = FontWeight.Bold
                    )
                },
                containerColor = if (isLocked) ComicMuted else ComicRed,
                contentColor = ComicPaper
            )
        }
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
                    Text("Fetching verified schedule...", fontWeight = FontWeight.Bold)
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
                    Text("UNABLE TO LOAD SCHEDULE", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(loadError!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadTripData(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed)
                    ) {
                        Text("RETRY", fontWeight = FontWeight.Bold)
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
                                        text = "PLAN LOCKED BY ORGANIZER",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 11.sp,
                                        color = ComicBlack
                                    )
                                    Text(
                                        text = "Schedule finalized. Tap unlock icon above to modify.",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontSize = 10.sp,
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
                            label = "Rain Protocol",
                            icon = Icons.Default.Thunderstorm,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("rain", "Rain Protocol") }
                        )
                        QuickReplanChip(
                            label = "Running Late",
                            icon = Icons.Default.AccessTime,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("running-late", "Running Late") }
                        )
                        QuickReplanChip(
                            label = "Tired / Rest",
                            icon = Icons.Default.Hotel,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("tired", "Tired / Rest") }
                        )
                        QuickReplanChip(
                            label = "Budget Cut",
                            icon = Icons.Default.AttachMoney,
                            enabled = !isLocked && !isReplanning,
                            onClick = { triggerQuickReplan("budget-cut", "Budget Cut") }
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
                            val dayCost = remember(activities) {
                                activities.sumOf { it.estimatedCost ?: 0.0 }
                            }

                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // Day Summary Card
                                item {
                                     Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                         Box(
                                             Modifier
                                                 .matchParentSize()
                                                 .offset(x = 4.dp, y = 4.dp)
                                                 .background(ComicInk, RoundedCornerShape(8.dp))
                                         )
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
                                                    text = "DAY ${day.dayIndex} // ${day.date}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Black,
                                                    color = ComicRed
                                                )
                                                if (dayCost > 0) {
                                                    Text(
                                                        text = "Day Est: $${dayCost.toInt()}",
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 11.sp,
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
                                                    text = "${activities.size} stops scheduled · $visitedCount visited",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = ComicMuted
                                                )
                                                Text(
                                                    text = "Swipe left/right for other days →",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
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
                                            val query = Uri.encode("${act.title} $destinationName")
                                            val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Conversational Edit Modal
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("REFINE SCHEDULE", fontWeight = FontWeight.Black) },
                text = {
                    Column {
                        Text(
                            "Enter modification directive (e.g. \"Shift museum to afternoon\", \"Add coffee break at 3pm\"):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editInstruction,
                            onValueChange = { editInstruction = it },
                            placeholder = { Text("e.g. Prioritize local bakeries") },
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
                            Text("APPLY DIRECTIVE", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }

        // Scrap Ticket Confirmation Dialog
        if (showScrapDialog) {
            AlertDialog(
                onDismissRequest = { if (!isScrapping) showScrapDialog = false },
                title = { Text("SCRAP FIELD TICKET", fontWeight = FontWeight.Black) },
                text = {
                    Text(
                        "Are you sure you want to delete and scrap this ticket? This action completely wipes the verified schedule and cannot be undone.",
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
                            Text("CONFIRM SCRAP", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isScrapping,
                        onClick = { showScrapDialog = false }
                    ) {
                        Text("KEEP TICKET")
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
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
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
    val address = activity.place?.formattedAddress ?: ""

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
                    text = "${activity.travelTimeFromPreviousMinutes}m verified transit trail",
                    style = MaterialTheme.typography.labelSmall,
                    color = ComicRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            Box(
                Modifier
                    .matchParentSize()
                    .offset(x = 4.dp, y = 4.dp)
                    .background(ComicInk, RoundedCornerShape(10.dp))
            )
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
                                text = if (photoUrl.contains("commons.wikimedia")) "PHOTO: WIKIMEDIA COMMONS" else "PHOTO: VERIFIED RECORD",
                                color = ComicPaper,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
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
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    fontSize = 12.sp
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${activity.startTime} - ${activity.endTime}",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ComicBlack
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (activity.estimatedCost != null && activity.estimatedCost > 0) {
                                Text(
                                    text = "$${activity.estimatedCost.toInt()}",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 13.sp,
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
                                        text = if (isVisited) "VISITED" else "CHECK IN",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isVisited) ComicPaper else ComicBlack
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activity.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = ComicBlack
                    )

                    if (!activity.reason.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = activity.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = ComicBlack.copy(alpha = 0.8f)
                        )
                    }

                    if (address.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = address,
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
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
                        if (address.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(address))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "Address copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .size(36.dp)
                                    .border(1.dp, ComicBlack, RoundedCornerShape(6.dp))
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Address", modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        OutlinedButton(
                            onClick = onNavigateClick,
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComicRed),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("NAVIGATE", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
}
