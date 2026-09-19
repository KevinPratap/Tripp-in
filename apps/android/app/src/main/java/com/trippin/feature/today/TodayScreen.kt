package com.trippin.feature.today

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
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    onOpenMap: (String) -> Unit
) {
    var tripDetails by remember { mutableStateOf<TripDetailsDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val loadTripData = {
        scope.launch {
            try {
                isLoading = true
                loadError = null
                tripDetails = NetworkModule.apiService.getTripDetails(tripId)
            } catch (e: Exception) {
                loadError = e.message ?: "Failed loading live today view"
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(tripId) {
        loadTripData()
    }

    val days = tripDetails?.itinerary?.days ?: emptyList()
    val todayDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    // Match today's dayIndex or default to Day 1
    val activeDay = remember(days, todayDateStr) {
        days.firstOrNull { it.date.startsWith(todayDateStr) } ?: days.firstOrNull()
    }
    val activities = activeDay?.activities ?: emptyList()
    val destinationName = tripDetails?.trip?.destination ?: "Destination"

    // Current or upcoming activity based on current clock time
    val currentTimeMinutes = remember {
        val cal = Calendar.getInstance()
        cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    fun parseTimeToMinutes(timeStr: String): Int {
        val parts = timeStr.split(":")
        if (parts.size < 2) return 0
        val h = parts[0].toIntOrNull() ?: 0
        val m = parts[1].toIntOrNull() ?: 0
        return h * 60 + m
    }

    val currentStop = remember(activities, currentTimeMinutes) {
        activities.firstOrNull { act ->
            val startMin = parseTimeToMinutes(act.startTime)
            val endMin = parseTimeToMinutes(act.endTime)
            currentTimeMinutes in startMin..endMin
        } ?: activities.firstOrNull { act ->
            parseTimeToMinutes(act.startTime) > currentTimeMinutes
        } ?: activities.firstOrNull()
    }

    val remainingStops = remember(activities, currentStop) {
        if (currentStop == null) emptyList()
        else activities.filter { it.id != currentStop.id }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TODAY // $destinationName".uppercase(),
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "REAL-TIME FIELD DISPATCH",
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
                        Icon(Icons.Default.Map, contentDescription = "Route Map", tint = ComicBlack)
                    }
                    IconButton(onClick = { loadTripData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = ComicRed)
                    }
                }
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
                    Text(
                        "Connecting live field GPS...",
                        fontWeight = FontWeight.Bold,
                        color = ComicBlack
                    )
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
                    Text(
                        "CONNECTION LOST",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(loadError ?: "", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadTripData() },
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed)
                    ) {
                        Text("RECONNECT", fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(ComicPaper),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Today Field Header
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.5.dp, ComicBlack, RoundedCornerShape(8.dp)),
                        color = ComicPanel,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(ComicRed, RoundedCornerShape(2.dp))
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "DAY ${activeDay?.dayIndex ?: 1} - ${activeDay?.date ?: todayDateStr}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Black,
                                        color = ComicBlack
                                    )
                                }
                                if (!activeDay?.weatherSummary.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = activeDay?.weatherSummary ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ComicMuted
                                    )
                                }
                            }

                            Surface(
                                color = ComicYellow,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = "LIVE",
                                    color = ComicBlack,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Current or Next Stop Hero Card
                if (currentStop != null) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
                            color = ComicPanel,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        color = ComicRed,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(4.dp))
                                    ) {
                                        Text(
                                            text = "CURRENT TARGET",
                                            color = ComicPaper,
                                            fontWeight = FontWeight.Black,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            fontSize = 11.sp
                                        )
                                    }

                                    Text(
                                        text = "${currentStop.startTime} - ${currentStop.endTime}",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        color = ComicBlack
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = currentStop.title,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 20.sp,
                                    color = ComicBlack,
                                    lineHeight = 24.sp
                                )

                                if (!currentStop.reason.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = currentStop.reason,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = ComicBlack.copy(alpha = 0.8f)
                                    )
                                }

                                if (currentStop.travelTimeFromPreviousMinutes > 0) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = ComicPaper,
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.border(1.dp, ComicBlack.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Default.Directions,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = ComicRed
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "${currentStop.travelTimeFromPreviousMinutes} min transit via OSRM",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = ComicBlack
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = {
                                        val gmmIntentUri = Uri.parse("google.navigation:q=${Uri.encode(currentStop.title)}")
                                        val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri).apply {
                                            setPackage("com.google.android.apps.maps")
                                        }
                                        try {
                                            context.startActivity(mapIntent)
                                        } catch (e: Exception) {
                                            val webMapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(currentStop.title)}")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, webMapsUri))
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.Navigation, contentDescription = null, tint = ComicPaper)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "NAVIGATE TO TARGET",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp,
                                        letterSpacing = 0.5.sp,
                                        color = ComicPaper
                                    )
                                }
                            }
                        }
                    }
                }

                // Remaining Stops Section
                item {
                    Text(
                        text = "UPCOMING SCHEDULE FOR TODAY",
                        fontWeight = FontWeight.Black,
                        fontSize = 13.sp,
                        letterSpacing = 1.sp,
                        color = ComicMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                if (remainingStops.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.5.dp, ComicBlack.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                            color = ComicPanel,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "All stops for today are completed or free roam mode is active.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ComicMuted,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    itemsIndexed(remainingStops) { idx, act ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                            color = ComicPanel,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${act.startTime} - ${act.endTime}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ComicRed
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = act.title,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = ComicBlack
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(act.title)}")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp))
                                ) {
                                    Icon(
                                        Icons.Default.Directions,
                                        contentDescription = "Navigate",
                                        tint = ComicRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
