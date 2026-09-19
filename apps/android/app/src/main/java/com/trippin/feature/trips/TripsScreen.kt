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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    var trips by remember { mutableStateOf<List<TripSummaryDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var tripToScrap by remember { mutableStateOf<TripSummaryDto?>(null) }
    var isScrapping by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val loadTrips = {
        scope.launch {
            isLoading = true
            try {
                val feed = NetworkModule.apiService.getHome()
                trips = feed.recentTrips
            } catch (_: Exception) {
                // Keep empty or cached
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadTrips()
    }

    fun handleScrapTrip(tripId: String) {
        scope.launch {
            try {
                isScrapping = true
                NetworkModule.apiService.deleteTrip(tripId)
                tripToScrap = null
                loadTrips()
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ComicPaper)
        ) {
            // Filter Pills Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filterOptions) { filter ->
                    val isSelected = selectedFilter == filter
                    Surface(
                        onClick = { selectedFilter = filter },
                        shape = RoundedCornerShape(8.dp),
                        color = if (isSelected) ComicBlack else ComicPanel,
                        modifier = Modifier.border(
                            2.dp,
                            ComicBlack,
                            RoundedCornerShape(8.dp)
                        )
                    ) {
                        Text(
                            text = filter,
                            color = if (isSelected) ComicPaper else ComicBlack,
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            if (isLoading) {
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
                    items(filteredTrips) { trip ->
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

@Composable
fun TripTicketCard(
    trip: TripSummaryDto,
    onOpen: () -> Unit,
    onToday: () -> Unit,
    onScrap: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
        color = ComicPanel,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Destination & Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.destination.uppercase(),
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = ComicBlack
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${trip.startDate} - ${trip.endDate} · ${trip.travelersCount} Travelers",
                        style = MaterialTheme.typography.bodySmall,
                        color = ComicMuted,
                        fontWeight = FontWeight.Bold
                    )
                }

                val statusColor = when {
                    trip.isLocked -> ComicYellow
                    trip.status.uppercase() == "READY" || trip.status.uppercase() == "VERIFIED" -> ComicRed
                    else -> ComicMuted
                }
                val statusText = if (trip.isLocked) "LOCKED" else trip.status.uppercase()

                Surface(
                    color = statusColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(4.dp))
                ) {
                    Text(
                        text = statusText,
                        color = if (trip.isLocked) ComicBlack else ComicPaper,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

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
                        .border(1.5.dp, ComicBlack, RoundedCornerShape(8.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ConfirmationNumber, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("VIEW TICKET", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onToday,
                    modifier = Modifier
                        .height(44.dp)
                        .border(1.5.dp, ComicBlack, RoundedCornerShape(8.dp)),
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
                        .border(1.5.dp, ComicBlack.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Scrap", tint = ComicRed)
                }
            }
        }
    }
}
