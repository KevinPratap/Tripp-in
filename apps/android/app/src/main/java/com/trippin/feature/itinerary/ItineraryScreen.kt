package com.trippin.feature.itinerary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.*
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripDetailsDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    onOpenMap: (String) -> Unit
) {
    var tripDetails by remember { mutableStateOf<TripDetailsDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editInstruction by remember { mutableStateOf("") }
    var isModifying by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(tripId) {
        try {
            isLoading = true
            tripDetails = NetworkModule.apiService.getTripDetails(tripId)
            isLoading = false
        } catch (e: Exception) {
            loadError = e.message ?: "Failed loading itinerary details"
            isLoading = false
        }
    }

    val days = tripDetails?.itinerary?.days ?: emptyList()
    val activeDay = days.getOrNull(selectedDayIndex) ?: days.firstOrNull()
    val activities = activeDay?.activities ?: emptyList()
    val destinationName = tripDetails?.trip?.destination ?: "Trip"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "$destinationName Field Ticket".uppercase(),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "VERIFIED INK SCHEDULE",
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
                    IconButton(onClick = { onOpenMap(tripId) }) {
                        Icon(Icons.Default.Map, contentDescription = "Map")
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.EditNote, contentDescription = "Edit", tint = ComicRed)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditDialog = true },
                icon = { Icon(Icons.Default.EditNote, contentDescription = null) },
                text = { Text("REFINE SCHEDULE", fontWeight = FontWeight.Bold) },
                containerColor = ComicRed,
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
                    Text("Fetching verified schedule from Railway...", fontWeight = FontWeight.Bold)
                }
            }
        } else if (loadError != null) {
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
                    Button(onClick = {
                        scope.launch {
                            try {
                                isLoading = true
                                loadError = null
                                tripDetails = NetworkModule.apiService.getTripDetails(tripId)
                                isLoading = false
                            } catch (e: Exception) {
                                loadError = e.message
                                isLoading = false
                            }
                        }
                    }) {
                        Text("RETRY")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(ComicPaper)
            ) {
                // Day selector tabs
                if (days.isNotEmpty()) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedDayIndex.coerceIn(0, days.size - 1),
                        edgePadding = 16.dp,
                        containerColor = ComicPaper
                    ) {
                        days.forEachIndexed { index, day ->
                            Tab(
                                selected = selectedDayIndex == index,
                                onClick = { selectedDayIndex = index },
                                text = {
                                    Text(
                                        text = "DAY ${day.dayIndex}",
                                        fontWeight = if (selectedDayIndex == index) FontWeight.Black else FontWeight.Normal,
                                        color = if (selectedDayIndex == index) ComicRed else ComicBlack
                                    )
                                }
                            )
                        }
                    }
                }

                // Summary / Day banner
                if (activeDay != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                        color = ComicPaper,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "FIELD LOG · ${activeDay.date}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Black,
                                color = ComicRed
                            )
                            if (!activeDay.summary.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = activeDay.summary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ComicBlack
                                )
                            }
                        }
                    }
                }

                // Timeline list of activities
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(activities) { index, act ->
                        ActivityComicCard(
                            activity = act,
                            index = index + 1,
                            onNavigateClick = {
                                val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(act.title)}")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                context.startActivity(mapIntent)
                            }
                        )
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
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        onClick = {
                            showEditDialog = false
                        }
                    ) {
                        Text("APPLY DIRECTIVE", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("CANCEL")
                    }
                }
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
                    text = "${activity.travelTimeFromPreviousMinutes}m transit transit trail",
                    style = MaterialTheme.typography.labelSmall,
                    color = ComicRed,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
            color = ComicPaper,
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                            text = "${activity.startTime} — ${activity.endTime}",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = ComicBlack
                        )
                    }

                    if (activity.estimatedCost != null && activity.estimatedCost > 0) {
                        Text(
                            text = "$${activity.estimatedCost.toInt()}",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = ComicRed
                        )
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

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onNavigateClick,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ComicRed)
                ) {
                    Icon(Icons.Default.Directions, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NAVIGATE", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}
