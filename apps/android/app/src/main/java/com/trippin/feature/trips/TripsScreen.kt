package com.trippin.feature.trips

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
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
import com.trippin.core.design.ComicInk
import com.trippin.core.design.ComicMuted
import com.trippin.core.design.ComicPanel
import com.trippin.core.design.ComicPaper
import com.trippin.core.design.ComicRed
import com.trippin.core.design.ComicYellow
import com.trippin.core.design.TrippinSegmentedTabs
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripSummaryDto
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToLong

/** The home feed returns at most this many trips, so the header never claims to count them all. */
private const val HOME_FEED_TRIP_LIMIT = 5

private val STATE_FILTERS = listOf("All", "Draft", "Deciding", "Locked")

/**
 * The four states a trip card may show, and every one is read off the trip the server sent.
 * Draft is no plan yet. Locked is the trip itself locked. Finished is the end date behind us.
 * Deciding is a plan that exists with nothing locked yet, so it can still change. Voting does not
 * exist in the engine yet, so this chip never claims that a vote is open.
 */
private enum class TripState(val label: String) {
    DRAFT("Draft"),
    DECIDING("Deciding"),
    LOCKED("Locked"),
    FINISHED("Finished")
}

/**
 * The trips you have, newest work first. Every line on a card is derived from the trip payload:
 * the state chip, the countdown from the real dates, the per-person range when the server sends
 * one, and a "Needs you" line only when something real is waiting. Where the server sends no cost
 * estimate the card says so instead of printing a number, because a number there would be invented.
 */
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
    var selectedFilter by remember { mutableStateOf(0) }
    var tripToDelete by remember { mutableStateOf<TripSummaryDto?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val today = LocalDate.now()

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
                // Keep whatever was cached. The screen never invents a trip.
            } finally {
                isLoading = false
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadTrips(false)
    }

    fun handleDeleteTrip(tripId: String) {
        scope.launch {
            try {
                isDeleting = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                NetworkModule.apiService.deleteTrip(tripId)
                TripCacheManager.invalidateTrip(tripId)
                tripToDelete = null
                loadTrips(true)
            } catch (_: Exception) {
                // Leave the trip on screen. It is still there.
            } finally {
                isDeleting = false
            }
        }
    }

    // One pass: state first, then needs-you first, then the soonest upcoming, then the past.
    val ordered = remember(trips, today) {
        val withState = trips.map { trip -> trip to tripStateOf(trip, today) }
        val (past, ahead) = withState.partition { it.second == TripState.FINISHED }
        ahead.sortedWith(
            compareBy(
                { if (needsYouLine(it.first, it.second) != null) 0 else 1 },
                { it.first.startDate }
            )
        ) + past.sortedByDescending { it.first.startDate }
    }

    val filterLabel = STATE_FILTERS.getOrElse(selectedFilter) { "All" }
    val visible = remember(ordered, filterLabel) {
        if (filterLabel == "All") ordered
        else ordered.filter { it.second.label == filterLabel }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Trips",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                            fontSize = 20.sp,
                            color = ComicInk
                        )
                        if (trips.isNotEmpty()) {
                            Text(
                                text = tripCountLine(trips.size, ordered.count { it.second != TripState.FINISHED }),
                                fontSize = 12.sp,
                                color = ComicMuted,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {},
                actions = {
                    IconButton(onClick = onNavigateToPlanner) {
                        Icon(Icons.Default.Add, contentDescription = "New trip", tint = ComicRed)
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    TrippinSegmentedTabs(
                        options = STATE_FILTERS,
                        selectedIndex = selectedFilter,
                        onOptionSelected = { index ->
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedFilter = index
                        }
                    )
                }

                when {
                    isLoading && trips.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = ComicRed)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Loading your trips", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    trips.isEmpty() -> EmptyTripsPanel(
                        title = "No trips yet",
                        body = "Start one and invite your friends.",
                        onPlan = onNavigateToPlanner
                    )

                    visible.isEmpty() -> EmptyTripsPanel(
                        title = "Nothing in $filterLabel",
                        body = "No trip of yours is in this state right now.",
                        onPlan = onNavigateToPlanner
                    )

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visible, key = { it.first.id }) { (trip, state) ->
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            tripToDelete = trip
                                        }
                                        false
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
                                                    contentDescription = null,
                                                    tint = ComicPaper,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    "Delete",
                                                    color = ComicPaper,
                                                    fontWeight = FontWeight.Black,
                                                    fontSize = 14.sp
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    TripCard(
                                        trip = trip,
                                        state = state,
                                        today = today,
                                        onOpen = { onNavigateToTrip(trip.id) },
                                        onToday = { onNavigateToToday(trip.id) },
                                        onDelete = { tripToDelete = trip }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val pendingDelete = tripToDelete
        if (pendingDelete != null) {
            AlertDialog(
                onDismissRequest = { if (!isDeleting) tripToDelete = null },
                title = { Text("Delete this trip", fontWeight = FontWeight.Black, fontSize = 18.sp) },
                text = {
                    Text(
                        "Delete the trip to ${pendingDelete.destination}? Its plan goes with it.",
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        enabled = !isDeleting,
                        onClick = { handleDeleteTrip(pendingDelete.id) }
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Delete", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isDeleting, onClick = { tripToDelete = null }) {
                        Text("Keep", fontSize = 14.sp)
                    }
                }
            )
        }
    }
}

/**
 * A stock destination photo keyed off the city name. The Trips card no longer uses this, because
 * the engine does not supply these images and a stand-in photo reads as if the app had one. It is
 * kept only because the old Home screen still imports it, and Home is off the navigation graph.
 */
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
private fun EmptyTripsPanel(title: String, body: String, onPlan: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(2.5.dp, ComicInk, RoundedCornerShape(12.dp)),
            color = ComicPanel,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = ComicInk
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    fontSize = 14.sp,
                    color = ComicMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .border(2.dp, ComicInk, RoundedCornerShape(8.dp))
                ) {
                    Text("Plan a trip", fontWeight = FontWeight.Black, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TripCard(
    trip: TripSummaryDto,
    state: TripState,
    today: LocalDate,
    onOpen: () -> Unit,
    onToday: () -> Unit,
    onDelete: () -> Unit
) {
    val heroImageUrl = trip.heroImageUrl?.takeIf { it.isNotBlank() }
    val countdown = countdownLine(trip, today)
    val cost = costLine(trip)
    val needsYou = needsYouLine(trip, state)
    val isLive = parseDate(trip.startDate)?.let { start ->
        parseDate(trip.endDate)?.let { end -> !today.isBefore(start) && !today.isAfter(end) }
    } ?: false

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
                // The top band is ink when the engine sent no photo, so the card never leans on a
                // stand-in image. Destination and dates sit on a dark band either way.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(if (heroImageUrl != null) 132.dp else 104.dp)
                        .background(ComicInk)
                ) {
                    if (heroImageUrl != null) {
                        AsyncImage(
                            model = heroImageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                        )
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
                    }

                    TripStateChip(
                        state = state,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = trip.destination.uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = ComicPaper
                        )
                        Text(
                            text = "${dateRangeLine(trip)} · ${travellersLine(trip)}",
                            fontSize = 12.sp,
                            color = ComicPaper.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                HorizontalDivider(thickness = 2.dp, color = ComicInk)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpen() }
                        .padding(14.dp)
                ) {
                    if (countdown != null) {
                        Text(
                            text = countdown,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = ComicRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    if (cost != null) {
                        Text(
                            text = cost,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = ComicInk
                        )
                        Text(
                            text = if (trip.currency.isNullOrBlank()) {
                                "Per person, from the engine's day rates and entry fees. The trip does " +
                                    "not state a currency, so these are plain numbers."
                            } else {
                                "Per person, from the engine's day rates and entry fees."
                            },
                            fontSize = 12.sp,
                            color = ComicMuted
                        )
                    } else {
                        Text(
                            text = "Cost not estimated yet",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ComicMuted
                        )
                    }

                    if (needsYou != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = needsYou,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = ComicRed
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                            Text("Open trip", fontWeight = FontWeight.Black, fontSize = 13.sp)
                        }

                        if (isLive) {
                            OutlinedButton(
                                onClick = onToday,
                                modifier = Modifier
                                    .height(44.dp)
                                    .border(1.5.dp, ComicInk, RoundedCornerShape(8.dp)),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ComicRed)
                            ) {
                                Text("Today", fontWeight = FontWeight.Black, fontSize = 13.sp)
                            }
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier
                                .size(44.dp)
                                .border(1.5.dp, ComicInk.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "Delete trip", tint = ComicRed)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The state chip. The label is one of the four derived states and nothing else, so a card can never
 * print a status the engine did not earn.
 */
@Composable
private fun TripStateChip(state: TripState, modifier: Modifier = Modifier) {
    val background = when (state) {
        TripState.DRAFT -> ComicYellow
        TripState.DECIDING -> ComicRed
        TripState.LOCKED -> ComicPaper
        TripState.FINISHED -> ComicMuted
    }
    val textColor = when (state) {
        TripState.DRAFT -> ComicInk
        TripState.DECIDING -> ComicPaper
        TripState.LOCKED -> ComicInk
        TripState.FINISHED -> ComicPaper
    }

    Box(
        modifier = modifier
            .background(background, RoundedCornerShape(4.dp))
            .border(1.5.dp, ComicInk, RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = state.label,
            color = textColor,
            fontWeight = FontWeight.Black,
            fontSize = 12.sp,
            letterSpacing = 0.5.sp
        )
    }
}

/** A real date, or null when the server sent something this build cannot read as one. */
private fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { LocalDate.parse(value) }.getOrNull()
}

private fun tripStateOf(trip: TripSummaryDto, today: LocalDate): TripState {
    val end = parseDate(trip.endDate)
    if (end != null && end.isBefore(today)) return TripState.FINISHED
    if (trip.isLocked) return TripState.LOCKED
    if (trip.totalActivitiesCount == 0) return TripState.DRAFT
    return TripState.DECIDING
}

/** How far off the trip is, or that it is running now, or that it is behind us. Never a guess. */
private fun countdownLine(trip: TripSummaryDto, today: LocalDate): String? {
    val start = parseDate(trip.startDate) ?: return null
    val end = parseDate(trip.endDate) ?: return null
    return when {
        end.isBefore(today) -> "Finished"
        !start.isAfter(today) -> "Happening now"
        else -> {
            val days = ChronoUnit.DAYS.between(today, start)
            if (days == 1L) "Starts tomorrow" else "In $days days"
        }
    }
}

/** "12 to 16 Oct", or the raw dates when this build cannot read them. */
private fun dateRangeLine(trip: TripSummaryDto): String {
    val start = parseDate(trip.startDate)
    val end = parseDate(trip.endDate)
    if (start == null || end == null) return "${trip.startDate} to ${trip.endDate}"
    val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    return if (start.year == end.year && start.month == end.month) {
        "${start.dayOfMonth} to ${end.format(dayMonth)}"
    } else {
        "${start.format(dayMonth)} to ${end.format(dayMonth)}"
    }
}

private fun travellersLine(trip: TripSummaryDto): String =
    if (trip.travelersCount == 1) "1 traveller" else "${trip.travelersCount} travellers"

/**
 * The header count says "shown" whenever the list came back at the feed's cap, because then the app
 * cannot know how many trips exist in total and must not imply it does.
 */
private fun tripCountLine(loaded: Int, upcoming: Int): String {
    val trips = if (loaded == 1) "1 trip" else "$loaded trips"
    val counted = if (loaded >= HOME_FEED_TRIP_LIMIT) "$trips shown" else trips
    return "$counted · $upcoming upcoming"
}

/**
 * The one line that brings someone back. Only real triggers count, and every one of them is
 * something the app can read off the trip: no plan exists yet, or people the trip was set up for
 * have not added their details. Where nothing is waiting, there is no line.
 */
private fun needsYouLine(trip: TripSummaryDto, state: TripState): String? {
    if (state == TripState.FINISHED) return null
    if (state == TripState.DRAFT) return "Needs you: no plan yet"
    val missing = trip.travelersCount - trip.travellers.size
    if (missing > 0) {
        val people = if (missing == 1) "1 person" else "$missing people"
        return "Needs you: $people have not joined yet"
    }
    return null
}

/**
 * The per-person range, straight off the server's perTravellerCost. Null means the server sent no
 * cost estimate for this trip, and then the card says so rather than printing a number.
 */
private fun costLine(trip: TripSummaryDto): String? {
    val shares = trip.perTravellerCost
    if (shares.isEmpty()) return null
    val low = shares.minOf { it.shareMin }
    val high = shares.maxOf { it.shareMax }
    val currency = trip.currency
    return if (high.roundToLong() == low.roundToLong()) {
        "${formatAmount(low, currency)} per person"
    } else {
        "${formatAmount(low, currency)} to ${formatAmount(high, currency)} per person"
    }
}

/**
 * A whole amount with its currency, and no symbol at all when the trip does not state one. The
 * Group screen carries its own copy of this on purpose: both files stayed independent this tick.
 */
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
