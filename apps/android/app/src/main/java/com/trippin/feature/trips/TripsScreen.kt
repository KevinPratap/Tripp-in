package com.trippin.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.*
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripSummaryDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrip: (String) -> Unit,
    onNavigateToToday: (String) -> Unit,
    onNavigateToPlanner: () -> Unit
) {
    val cachedFeed = TripCacheManager.homeFeedState.value
    var trips by remember { mutableStateOf(cachedFeed?.recentTrips ?: emptyList()) }
    var isLoading by remember { mutableStateOf(trips.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var tripToScrap by remember { mutableStateOf<TripSummaryDto?>(null) }
    var isScrapping by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val loadTrips: (isManualRefresh: Boolean) -> Unit = { isManualRefresh ->
        scope.launch {
            if (isManualRefresh) {
                isRefreshing = true
            } else if (trips.isEmpty()) {
                isLoading = true
            }
            try {
                val feed = NetworkModule.apiService.getHome()
                TripCacheManager.homeFeedState.value = feed
                trips = feed.recentTrips
            } catch (_: Exception) {
                // Keep cached
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadTrips(false)
    }

    fun handleScrapTrip(tripId: String) {
        scope.launch {
            try {
                isScrapping = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                NetworkModule.apiService.deleteTrip(tripId)
                TripCacheManager.invalidateTrip(tripId)
                tripToScrap = null
                loadTrips(true)
            } catch (_: Exception) {
                // Ignore or reload
            } finally {
                isScrapping = false
            }
        }
    }

    val filterOptions = listOf("ALL", "READY", "LOCKED", "DRAFT")
    val filteredTrips = remember(trips, selectedFilter) {
        when (selectedFilter) {
            "READY" -> trips.filter { it.status.uppercase() == "READY" || it.status.uppercase() == "VERIFIED" }
            "LOCKED" -> trips.filter { it.isLocked }
            "DRAFT" -> trips.filter { it.status.uppercase() == "DRAFT" || !it.isLocked }
            else -> trips
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FIELD TICKETS ARCHIVE",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "SAVED TRAVEL MANIFESTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = ComicRed,
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
                    IconButton(onClick = onNavigateToPlanner) {
                        Icon(Icons.Default.Add, contentDescription = "New Trip", tint = ComicRed)
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadTrips(true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ComicPaper)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Filter Segmented Control
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val filterIndex = filterOptions.indexOf(selectedFilter).coerceAtLeast(0)
                    TrippinSegmentedTabs(
                        options = filterOptions,
                        selectedIndex = filterIndex,
                        onOptionSelected = { index ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFilter = filterOptions[index]
                        }
                    )
                }


                if (isLoading && trips.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ComicRed)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Retrieving ticket dispatch...", fontWeight = FontWeight.Bold)
                        }
                    }
                } else if (filteredTrips.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
                            color = ComicPanel,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = ComicRed,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "NO TICKETS FOUND",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = ComicBlack
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "No itineraries match the \"$selectedFilter\" filter criteria.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ComicMuted
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = onNavigateToPlanner,
                                    colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.border(2.dp, ComicBlack, RoundedCornerShape(8.dp))
                                ) {
                                    Text("DRAFT NEW TICKET", fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredTrips, key = { it.id }) { trip ->
                            // Native Swipe to Scrap/Delete Gesture
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        tripToScrap = trip
                                        false
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(ComicRed)
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.DeleteOutline,
                                                contentDescription = "Scrap",
                                                tint = ComicPaper,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "SCRAP",
                                                color = ComicPaper,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                }
                            ) {
                                TripTicketCard(
                                    trip = trip,
                                    onOpen = { onNavigateToTrip(trip.id) },
                                    onToday = { onNavigateToToday(trip.id) },
                                    onScrap = { tripToScrap = trip }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Scrap Confirmation Dialog
        if (tripToScrap != null) {
            AlertDialog(
                onDismissRequest = { if (!isScrapping) tripToScrap = null },
                title = { Text("SCRAP FIELD TICKET", fontWeight = FontWeight.Black) },
                text = {
                    Text(
                        "Scrap ticket for ${tripToScrap?.destination}? This wipes the schedule permanently from dispatch records.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        enabled = !isScrapping,
                        onClick = { tripToScrap?.let { handleScrapTrip(it.id) } }
                    ) {
                        if (isScrapping) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("SCRAP TICKET", fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isScrapping,
                        onClick = { tripToScrap = null }
                    ) {
                        Text("KEEP TICKET")
                    }
                }
            )
        }
    }
}

fun getDestinationHeroFallback(destination: String): String {
    val dest = destination.lowercase()
    return when {
        dest.contains("tokyo") -> "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=800"
        dest.contains("paris") -> "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=800"
        dest.contains("rome") -> "https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=800"
        dest.contains("kyoto") -> "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=800"
        dest.contains("lisbon") -> "https://images.unsplash.com/photo-1588614959060-4d144f28b207?w=800"
        dest.contains("london") -> "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?w=800"
        else -> "https://images.unsplash.com/photo-1488646953014-85cb44e25828?w=800"
    }
}

@Composable
fun TripTicketCard(
    trip: TripSummaryDto,
    onOpen: () -> Unit,
    onToday: () -> Unit,
    onScrap: () -> Unit
) {
    val imageUrl = trip.heroImageUrl ?: getDestinationHeroFallback(trip.destination)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(ComicInk, RoundedCornerShape(12.dp))
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, ComicInk, RoundedCornerShape(12.dp)),
            color = ComicPanel,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column {
                // Visual Hero Image Header with Halftone Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = trip.destination,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    )
                    // Gradient Scrim
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, ComicInk.copy(alpha = 0.85f)),
                                    startY = 40f
                                )
                            )
                    )

                    // Header Badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        TrippinStatusBadge(status = if (trip.isLocked) "LOCKED" else trip.status)

                        Surface(
                            color = ComicInk.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${trip.travelersCount} TRAVELERS",
                                color = ComicPaper,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Destination overlay title
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = trip.destination.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 20.sp,
                            color = ComicPaper
                        )
                        Text(
                            text = "${trip.startDate} to ${trip.endDate}",
                            style = MaterialTheme.typography.bodySmall,
                            color = ComicPaper.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(thickness = 2.dp, color = ComicInk)

                Column(modifier = Modifier.padding(14.dp)) {
                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpen,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .border(1.5.dp, ComicInk, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.AirplaneTicket, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("OPEN TICKET", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }

                        OutlinedButton(
                            onClick = onToday,
                            modifier = Modifier
                                .height(44.dp)
                                .border(1.5.dp, ComicInk, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ComicRed)
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("TODAY", fontWeight = FontWeight.Black, fontSize = 12.sp)
                        }

                        IconButton(
                            onClick = onScrap,
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.5.dp, ComicInk.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Scrap", tint = ComicRed)
                        }
                    }
                }
            }
        }
    }
}
