package com.trippin.feature.today

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trippin.core.cache.TripCacheManager
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
    onOpenMap: (String) -> Unit,
    /**
     * True when this screen is the Today sub-view of Plan rather than a destination of its own.
     * Then Plan's own top bar is the only one on screen: this one draws no title, no back arrow and
     * no system insets, so today's stop is not pushed down by two bars.
     */
    embedded: Boolean = false,
    /** The height the caller is giving this screen. Plan passes weight so the list stays bounded. */
    modifier: Modifier = Modifier
) {
    val cached = remember(tripId) { TripCacheManager.getTrip(tripId) }
    var tripDetails by remember { mutableStateOf(cached) }
    var isLoading by remember { mutableStateOf(cached == null) }
    var isRefreshing by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    val loadTripData: (isManual: Boolean) -> Unit = { isManual ->
        scope.launch {
            if (isManual) isRefreshing = true
            else if (tripDetails == null) isLoading = true
            loadError = null
            try {
                val fetched = NetworkModule.apiService.getTripDetails(tripId)
                tripDetails = fetched
                TripCacheManager.putTrip(tripId, fetched)
            } catch (e: Exception) {
                if (tripDetails == null) {
                    loadError = e.message ?: "Could not load today's plan"
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

    val days = tripDetails?.itinerary?.days ?: emptyList()
    val todayDateStr = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }

    val activeDay = remember(days, todayDateStr) {
        days.firstOrNull { it.date.startsWith(todayDateStr) } ?: days.firstOrNull()
    }
    val activities = activeDay?.activities ?: emptyList()
    val destinationName = tripDetails?.trip?.destination ?: "Destination"

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

    /* The day on screen is not always today. Plan opens this screen as its Today sub-view, and this
     * screen falls back to the trip's first day whenever the itinerary has no day for the real date,
     * so a day in December can be on screen in September. The clock says nothing about a stop on a
     * day that is not today, so Now, Next, Earlier, Today and "Later today" are only allowed when the
     * day on screen really is today. On any other day the honest answer is the day itself. */
    val dayDate = activeDay?.date
    val dayIsToday = dayDate != null && dayDate.startsWith(todayDateStr)
    val dayIsKnown = dayDate != null && dayDate.isNotBlank()
    val dayHasPassed = dayDate != null && dayDate.isNotBlank() && dayDate < todayDateStr
    val stopCountText = "${activities.size} ${if (activities.size == 1) "stop" else "stops"}"

    val stopTimeLabel: String? = currentStop?.let { stop ->
        val startMin = parseTimeToMinutes(stop.startTime)
        val endMin = parseTimeToMinutes(stop.endTime)
        when {
            dayIsToday && currentTimeMinutes in startMin..endMin -> "Now"
            dayIsToday && currentTimeMinutes < startMin -> "Next"
            dayIsToday -> "Earlier"
            !dayIsKnown -> null
            dayHasPassed -> "Past"
            else -> "Upcoming"
        }
    }

    Scaffold(
        // Plan draws the top bar when this is its Today sub-view, so this one stays empty rather
        // than printing a second title and a back arrow onto the screen you are already on.
        topBar = {
            if (!embedded) TopAppBar(
                title = {
                    Column {
                        Text(
                            // The bar may only say Today when the day under it really is today.
                            text = when {
                                dayIsToday -> "Today · $destinationName"
                                activeDay != null -> "Day ${activeDay.dayIndex} · $destinationName"
                                else -> destinationName
                            }.uppercase(),
                            style = TrippinType.Heading
                        )
                        Text(
                            text = when {
                                activeDay == null -> "No days planned yet"
                                activities.isEmpty() -> if (dayIsToday) "Nothing planned for today" else "Nothing planned for this day"
                                dayIsToday -> "$stopCountText today"
                                else -> "$stopCountText on this day"
                            },
                            style = TrippinType.Caption,
                            color = InkMuted
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
                        Icon(Icons.Default.Map, contentDescription = "Map", tint = ComicBlack)
                    }
                }
            )
        },
        // Embedded means Plan already took the system insets with its own bar, so this one must not
        // add a second helping of top padding. Wrapped at the call site in Plan, this screen also
        // gets a bounded height, which is what the list needs.
        modifier = modifier,
        contentWindowInsets = if (embedded) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets
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
                        "Loading today's plan...",
                        style = TrippinType.Body,
                        color = ComicBlack
                    )
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
                    Text(
                        "You are offline",
                        color = MaterialTheme.colorScheme.error,
                        style = TrippinType.Title
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(loadError ?: "", style = TrippinType.Body)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadTripData(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed)
                    ) {
                        Text("Retry", style = TrippinType.Label)
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
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
                                                .background(Ink, RoundedCornerShape(2.dp))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            // Never prints today's real date as a stand-in for a day
                                            // whose own date the wire did not send.
                                            text = when {
                                                activeDay == null -> "No days planned"
                                                dayDate.isNullOrBlank() -> "DAY ${activeDay.dayIndex}"
                                                else -> "DAY ${activeDay.dayIndex} - $dayDate"
                                            },
                                            style = TrippinType.Label,
                                            color = ComicBlack
                                        )
                                    }
                                    if (!activeDay?.weatherSummary.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = activeDay.weatherSummary,
                                            style = TrippinType.Caption,
                                            color = ComicMuted
                                        )
                                    }
                                }

                                /* Only when the day on screen really is today. Nothing else here
                                 * claims to be live. */
                                if (dayIsToday) {
                                    Surface(
                                        color = NeutralInkSurface,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(4.dp))
                                    ) {
                                        Text(
                                            text = "Today",
                                            color = NeutralInk,
                                            style = TrippinType.Caption,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Current or Next Stop Hero Card with Photo
                    if (currentStop != null) {
                        item {
                            val photoUrl = currentStop.effectivePhotoUrl
                            var isVisited by remember {
                                mutableStateOf(TripCacheManager.isActivityVisited(currentStop.id))
                            }

                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
                                color = ComicPanel,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column {
                                    if (!photoUrl.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(160.dp)
                                        ) {
                                            AsyncImage(
                                                model = photoUrl,
                                                contentDescription = currentStop.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                                            )
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
                                    } else {
                                        // Half of a real trip's stops have no genuine photograph of
                                        // the venue on Wikidata, and a stand-in image is not allowed.
                                        // The plate names the place in its own letters instead.
                                        PlacePlate(
                                            title = currentStop.title,
                                            category = currentStop.type,
                                            height = 160.dp
                                        )
                                        HorizontalDivider(thickness = 2.dp, color = ComicBlack)
                                    }

                                    Column(modifier = Modifier.padding(18.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            /* The word on this chip is the stop's real relation to the
                                             * clock, and it is only available when the day on screen is
                                             * today. A stop on a day that has not started is Upcoming,
                                             * not Now. */
                                            if (stopTimeLabel != null) {
                                                val isRunningNow = stopTimeLabel == "Now"
                                                Surface(
                                                    color = if (isRunningNow) ComicRed else NeutralInkSurface,
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(4.dp))
                                                ) {
                                                    Text(
                                                        text = stopTimeLabel,
                                                        color = if (isRunningNow) ComicPaper else NeutralInk,
                                                        style = TrippinType.Caption,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${currentStop.startTime} - ${currentStop.endTime}",
                                                style = TrippinType.Label,
                                                color = ComicBlack
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = currentStop.title,
                                            style = TrippinType.Title,
                                            color = ComicBlack,
                                            lineHeight = 24.sp
                                        )

                                        if (!currentStop.reason.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = currentStop.reason,
                                                style = TrippinType.Body,
                                                color = ComicBlack.copy(alpha = 0.8f)
                                            )
                                        }

                                        val address = currentStop.place?.formattedAddress ?: ""
                                        if (address.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = address,
                                                style = TrippinType.Body,
                                                color = ComicMuted
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
                                                        tint = InkMuted
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        text = "${currentStop.travelTimeFromPreviousMinutes} min travel, from OSRM",
                                                        style = TrippinType.Caption,
                                                        color = ComicBlack
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(16.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (address.isNotBlank()) {
                                                IconButton(
                                                    onClick = {
                                                        clipboardManager.setText(AnnotatedString(address))
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .border(1.5.dp, ComicBlack, RoundedCornerShape(8.dp))
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val query = Uri.encode("${currentStop.title} $destinationName")
                                                    val webMapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, webMapsUri))
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                                                colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Navigation, contentDescription = null, tint = ComicPaper)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Take me there",
                                                    style = TrippinType.Label,
                                                    color = ComicPaper
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Remaining Stops Section
                    item {
                        Text(
                            // "Later today" is only true on the day that is today.
                            text = if (dayIsToday) "Later today" else "Later that day",
                            style = TrippinType.Label,
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
                                    text = "That is every stop for today.",
                                    style = TrippinType.Body,
                                    color = ComicMuted,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(remainingStops) { idx, act ->
                            val photo = act.effectivePhotoUrl
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
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (!photo.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, ComicBlack, RoundedCornerShape(6.dp))
                                        ) {
                                            AsyncImage(
                                                model = photo,
                                                contentDescription = act.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                    } else {
                                        // Same plate as the stop cards, at thumbnail size: the
                                        // monogram reads as the venue without a photograph of it.
                                        Box(
                                            modifier = Modifier
                                                .size(54.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, ComicBlack, RoundedCornerShape(6.dp))
                                        ) {
                                            PlacePlate(
                                                title = act.title,
                                                category = act.type,
                                                height = 54.dp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${act.startTime} - ${act.endTime}",
                                            style = TrippinType.Label,
                                            color = ComicBlack
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = act.title,
                                            style = TrippinType.Heading,
                                            color = ComicBlack
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(act.title)}")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp))
                                    ) {
                                        Icon(
                                            Icons.Default.Directions,
                                            contentDescription = "Navigate",
                                            tint = ComicRed,
                                            modifier = Modifier.size(18.dp)
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
}
