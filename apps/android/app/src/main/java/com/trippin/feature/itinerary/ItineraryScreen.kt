package com.trippin.feature.itinerary

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.*
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.ModifyItineraryRequestDto
import com.trippin.core.network.NetworkModule
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
    var tripDetails by remember { mutableStateOf<TripDetailsDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editInstruction by remember { mutableStateOf("") }
    var isModifying by remember { mutableStateOf(false) }
    var modifyError by remember { mutableStateOf<String?>(null) }

    // Lock & Replan & Scrap states
    var isLocking by remember { mutableStateOf(false) }
    var isReplanning by remember { mutableStateOf(false) }
    var replanStatusMsg by remember { mutableStateOf<String?>(null) }
    var showScrapDialog by remember { mutableStateOf(false) }
    var isScrapping by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loadTripData = {
        scope.launch {
            try {
                isLoading = true
                tripDetails = NetworkModule.apiService.getTripDetails(tripId)
                isLoading = false
            } catch (e: Exception) {
                loadError = e.message ?: "Failed loading itinerary details"
                isLoading = false
            }
        }
    }

    LaunchedEffect(tripId) {
        loadTripData()
    }

    val isLocked = tripDetails?.trip?.isLocked == true
    val days = tripDetails?.itinerary?.days ?: emptyList()
    val activeDay = days.getOrNull(selectedDayIndex) ?: days.firstOrNull()
    val activities = activeDay?.activities ?: emptyList()
    val destinationName = tripDetails?.trip?.destination ?: "Trip"

    fun handleToggleLock() {
        if (isLocking) return
        scope.launch {
            try {
                isLocking = true
                if (isLocked) {
                    NetworkModule.apiService.unlockTrip(tripId)
                } else {
                    NetworkModule.apiService.lockTrip(tripId)
                }
                tripDetails = NetworkModule.apiService.getTripDetails(tripId)
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
                replanStatusMsg = "Executing $intentLabel replan protocol..."
                NetworkModule.apiService.replanTrip(tripId, ReplanRequestDto(intent = intentKey))
                tripDetails = NetworkModule.apiService.getTripDetails(tripId)
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
                NetworkModule.apiService.deleteTrip(tripId)
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
                    // Today Mode
                    IconButton(onClick = { onNavigateToToday(tripId) }) {
                        Icon(Icons.Default.Navigation, contentDescription = "Today Mode", tint = ComicRed)
                    }
                    // Lock / Unlock Toggle
                    IconButton(onClick = { handleToggleLock() }) {
                        if (isLocked) {
                            Icon(Icons.Default.Lock, contentDescription = "Unlock Plan", tint = ComicBlack)
                        } else {
                            Icon(Icons.Default.LockOpen, contentDescription = "Lock Plan", tint = ComicRed)
                        }
                    }
                    // Print Field Voucher
                    IconButton(onClick = {
                        val printUri = Uri.parse("https://web-production-a9ec6.up.railway.app/trip/$tripId/print")
                        context.startActivity(Intent(Intent.ACTION_VIEW, printUri))
                    }) {
                        Icon(Icons.Default.Print, contentDescription = "Print Voucher", tint = ComicBlack)
                    }
                    // Share / Collab
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
                    // Sync Calendar
                    IconButton(onClick = {
                        val calIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://backend-production-011e.up.railway.app/api/v1/trips/$tripId/calendar.ics"))
                        context.startActivity(calIntent)
                    }) {
                        Icon(Icons.Default.CalendarToday, contentDescription = "Sync Calendar")
                    }
                    // Map View
                    IconButton(onClick = { onOpenMap(tripId) }) {
                        Icon(Icons.Default.Map, contentDescription = "Map")
                    }
                    // Scrap Ticket (Delete)
                    IconButton(onClick = { showScrapDialog = true }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Scrap Ticket", tint = ComicRed)
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
                    Button(
                        onClick = { loadTripData() },
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed)
                    ) {
                        Text("RETRY", fontWeight = FontWeight.Bold)
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
                // Locked Plan Banner
                if (isLocked) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                        color = ComicYellow,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = ComicBlack, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "PLAN LOCKED BY ORGANIZER",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = ComicBlack
                                )
                                Text(
                                    text = "Schedule finalized. Tap unlock icon above to modify.",
                                    style = MaterialTheme.typography.bodySmall,
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
                        .padding(horizontal = 16.dp, vertical = 6.dp),
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
                                text = "FIELD LOG - ${activeDay.date}",
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
                                    tripDetails = NetworkModule.apiService.getTripDetails(tripId)
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
                            text = "${activity.startTime} - ${activity.endTime}",
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
