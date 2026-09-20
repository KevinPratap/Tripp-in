package com.trippin.feature.trips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.AccentCrimson
import com.trippin.core.design.DangerCrimson
import com.trippin.core.design.GoodInk
import com.trippin.core.design.GoodInkSurface
import com.trippin.core.design.Ink
import com.trippin.core.design.InkMuted
import com.trippin.core.design.NeutralInk
import com.trippin.core.design.NeutralInkSurface
import com.trippin.core.design.Panel
import com.trippin.core.design.WarnAmber
import com.trippin.core.design.WarnAmberSurface
import com.trippin.core.design.ComicInk
import com.trippin.core.design.ComicMuted
import com.trippin.core.design.ComicPanel
import com.trippin.core.design.ComicPaper
import com.trippin.core.design.TrippinSegmentedTabs
import com.trippin.core.design.TrippinType
import com.trippin.core.network.DestinationCardDto
import com.trippin.core.network.JoinTripRequestDto
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripSummaryDto
import kotlinx.coroutines.launch
import retrofit2.HttpException
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
    onNavigateToPlanner: (String?) -> Unit
) {
    val cachedFeed = TripCacheManager.homeFeedState.value
    var trips by remember { mutableStateOf(cachedFeed?.recentTrips ?: emptyList()) }
    var suggestions by remember {
        mutableStateOf(
            (cachedFeed?.popularDestinations.orEmpty() + cachedFeed?.recommendedDestinations.orEmpty())
                .distinctBy { it.id }
        )
    }
    var isLoading by remember { mutableStateOf(trips.isEmpty()) }
    var isRefreshing by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(0) }
    var tripToDelete by remember { mutableStateOf<TripSummaryDto?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var showJoinSheet by remember { mutableStateOf(false) }
    var inviteTrip by remember { mutableStateOf<TripSummaryDto?>(null) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var inviteError by remember { mutableStateOf<String?>(null) }
    var joinBusy by remember { mutableStateOf(false) }
    var joinError by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
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
                suggestions = (feed.popularDestinations + feed.recommendedDestinations)
                    .distinctBy { it.id }
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

    /**
     * The invite code for a trip, created on demand by its owner. Creating it is a deliberate
     * action rather than a side effect of reading the trip, and the code it returns is the same one
     * POST /trips/join accepts, which is what makes the card's "invite" line a real offer.
     */
    fun requestInviteCode(trip: TripSummaryDto) {
        scope.launch {
            inviteError = null
            inviteCode = null
            try {
                val link = NetworkModule.apiService.createShareLink(trip.id)
                inviteCode = link.token
                copyToClipboard(context, "Tripp'in invite code", link.token)
            } catch (e: HttpException) {
                inviteError = when (e.code()) {
                    403 -> "Only the person who created this trip can invite people to it."
                    401 -> "Sign in again to invite people."
                    else -> "The server answered ${e.code()} and no code was created."
                }
            } catch (_: Exception) {
                inviteError = "Could not reach the server, so no invite code was created."
            }
        }
    }

    fun join(tripCode: String, name: String) {
        scope.launch {
            joinBusy = true
            joinError = null
            try {
                val joined = NetworkModule.apiService.joinTrip(
                    JoinTripRequestDto(token = tripCode.trim(), name = name.trim())
                )
                showJoinSheet = false
                loadTrips(true)
                onNavigateToTrip(joined.tripId)
            } catch (e: HttpException) {
                joinError = when (e.code()) {
                    404 -> "That invite code is not valid, or the trip's owner revoked it."
                    401 -> "Sign in again, then enter the code."
                    else -> "The server answered ${e.code()}. Try again."
                }
            } catch (_: Exception) {
                joinError = "Could not reach the server. Check the connection and try again."
            } finally {
                joinBusy = false
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
            // One black block per screen, carrying the display-size word and the only
            // always-available action. Everything below it is a quiet white surface, so there is
            // exactly one loud object on this screen for the eye to land on first.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ComicInk)
                    .padding(start = 20.dp, end = 14.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "TRIPS", style = TrippinType.Title, color = ComicPaper)
                        if (trips.isNotEmpty()) {
                            Text(
                                text = tripCountLine(
                                    trips.size,
                                    ordered.count { it.second != TripState.FINISHED }
                                ),
                                style = TrippinType.Caption,
                                color = ComicPaper.copy(alpha = 0.72f),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onNavigateToPlanner(null) },
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.5.dp, ComicPaper.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New trip", tint = AccentCrimson)
                    }
                }
            }
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
                // The same four states as words with a rule under the selected one. The control
                // this replaces was a 2dp box holding a 2dp box holding a pill: three frames to say
                // which filter is on.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    STATE_FILTERS.forEachIndexed { index, label ->
                        val isOn = index == selectedFilter
                        Column(
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedFilter = index
                                }
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = label.uppercase(),
                                style = TrippinType.Label,
                                color = if (isOn) ComicInk else ComicMuted
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .width(if (isOn) 22.dp else 0.dp)
                                    .height(3.dp)
                                    .background(AccentCrimson)
                            )
                        }
                    }
                }

                when {
                    isLoading && trips.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = AccentCrimson)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Loading your trips", style = TrippinType.Label)
                            }
                        }
                    }

                    trips.isEmpty() -> {
                        // Nothing on this account yet. The screen still has a job: the way in for
                        // someone who was invited, and the feed's own destinations to start from.
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            EmptyTripsPanel(
                                title = "No trips yet",
                                body = "Start one and invite your friends. Signing in keeps it on your " +
                                    "account rather than on this phone.",
                                onPlan = { onNavigateToPlanner(null) },
                                fillHeight = false
                            )
                            JoinPrompt(onClick = { showJoinSheet = true })
                            if (suggestions.isNotEmpty()) {
                                Text(
                                    text = "WHERE NEXT",
                                    style = TrippinType.Label,
                                    color = ComicMuted
                                )
                                suggestions.take(4).forEach { destination ->
                                    SuggestionCard(
                                        destination = destination,
                                        onClick = { onNavigateToPlanner(destination.name) }
                                    )
                                }
                            }
                        }
                    }

                    visible.isEmpty() -> EmptyTripsPanel(
                        title = "Nothing in $filterLabel",
                        body = "No trip of yours is in this state right now.",
                        onPlan = { onNavigateToPlanner(null) }
                    )

                    else -> {
                        LazyColumn(
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                                .background(DangerCrimson)
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
                                                    style = TrippinType.Label,
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    TripRow(
                                        trip = trip,
                                        state = state,
                                        today = today,
                                        onOpen = { onNavigateToTrip(trip.id) },
                                        onToday = { onNavigateToToday(trip.id) },
                                        onDelete = { tripToDelete = trip },
                                        onInvite = { inviteTrip = trip }
                                    )
                                }
                            }

                            // The empty half of this screen gets a job. A list with one trip on it
                            // used to leave the rest of the screen as bare paper; now the next useful
                            // thing sits under it, and every card in it comes from the feed the app
                            // already loaded rather than being written into the UI.
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    if (suggestions.isNotEmpty()) {
                                        Text(
                                            text = "WHERE NEXT",
                                            style = TrippinType.Label,
                                            color = ComicMuted,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                        suggestions.take(4).forEach { destination ->
                                            SuggestionCard(
                                                destination = destination,
                                                onClick = { onNavigateToPlanner(destination.name) }
                                            )
                                        }
                                    }

                                    JoinPrompt(onClick = { showJoinSheet = true })
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
                title = { Text("Delete this trip", style = TrippinType.Heading) },
                text = {
                    Text(
                        "Delete the trip to ${pendingDelete.destination}? Its plan goes with it.",
                        style = TrippinType.Label,
                    )
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = DangerCrimson),
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
                            Text("Delete", style = TrippinType.Label)
                        }
                    }
                },
                dismissButton = {
                    TextButton(enabled = !isDeleting, onClick = { tripToDelete = null }) {
                        Text("Keep", style = TrippinType.Label)
                    }
                }
            )
        }

        // The invite sheet. It asks the server for a code only when someone actually wants one, and
        // it shows the code rather than claiming a message was sent, because nothing is sent.
        val inviting = inviteTrip
        LaunchedEffect(inviting?.id) {
            if (inviting != null) requestInviteCode(inviting)
        }
        if (inviting != null) {
            AlertDialog(
                onDismissRequest = { inviteTrip = null; inviteCode = null; inviteError = null },
                title = {
                    Text("Invite people to ${inviting.destination}", style = TrippinType.Heading)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            inviteError != null -> Text(
                                text = inviteError.orEmpty(),
                                style = TrippinType.Body,
                                color = ComicInk
                            )
                            inviteCode == null -> Text(
                                text = "Asking the server for this trip's invite code.",
                                style = TrippinType.Body,
                                color = ComicMuted
                            )
                            else -> {
                                Text(
                                    text = inviteCode.orEmpty(),
                                    style = TrippinType.Heading,
                                    color = ComicInk
                                )
                                Text(
                                    text = "Copied to the clipboard. Send it to your friends, and they " +
                                        "tap Join a trip on the Trips screen and enter it.",
                                    style = TrippinType.Body,
                                    color = ComicMuted
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (inviteCode != null) {
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCrimson),
                            onClick = {
                                copyToClipboard(context, "Tripp'in invite code", inviteCode.orEmpty())
                            }
                        ) {
                            Text("Copy code", style = TrippinType.Label)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { inviteTrip = null; inviteCode = null; inviteError = null }) {
                        Text("Done", style = TrippinType.Label)
                    }
                }
            )
        }

        // Joining a trip someone else invited you to. This is the reachable join action the Trips
        // card promises when it says people have not joined yet.
        if (showJoinSheet) {
            var joinCode by remember { mutableStateOf("") }
            var joinName by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { if (!joinBusy) showJoinSheet = false },
                title = { Text("Join a trip", style = TrippinType.Heading) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Enter the invite code the trip's owner sent you, and the name the " +
                                "others will see on this trip.",
                            style = TrippinType.Caption,
                            color = ComicMuted
                        )
                        OutlinedTextField(
                            value = joinCode,
                            onValueChange = { joinCode = it },
                            singleLine = true,
                            enabled = !joinBusy,
                            label = { Text("Invite code", style = TrippinType.Label) },
                            textStyle = TrippinType.Body,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().border(2.dp, ComicInk, RoundedCornerShape(8.dp))
                        )
                        OutlinedTextField(
                            value = joinName,
                            onValueChange = { joinName = it },
                            singleLine = true,
                            enabled = !joinBusy,
                            label = { Text("Your name", style = TrippinType.Label) },
                            textStyle = TrippinType.Body,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().border(2.dp, ComicInk, RoundedCornerShape(8.dp))
                        )
                        joinError?.let {
                            Text(text = it, style = TrippinType.Body, color = ComicInk)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCrimson),
                        enabled = !joinBusy && joinCode.isNotBlank() && joinName.isNotBlank(),
                        onClick = { join(joinCode, joinName) }
                    ) {
                        if (joinBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                        } else {
                            Text("Join", style = TrippinType.Label)
                        }
                    }
                },
                dismissButton = {
                    TextButton(enabled = !joinBusy, onClick = { showJoinSheet = false }) {
                        Text("Cancel", style = TrippinType.Label)
                    }
                }
            )
        }
    }
}

/**
 * A destination from the home feed, offered under the trips that exist. It is the feed's own card,
 * including the photograph the server sent for it, so nothing here is invented by the UI.
 */
@Composable
private fun SuggestionCard(
    destination: DestinationCardDto,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(2.dp, ComicInk, RoundedCornerShape(12.dp)),
        color = ComicPanel,
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(84.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val photo = destination.imageUrl.takeIf { it.isNotBlank() }
            Box(
                modifier = Modifier
                    .width(96.dp)
                    .fillMaxHeight()
                    .background(ComicInk)
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    // No photograph means no photograph. The initials are the fallback everywhere.
                    Text(
                        text = destination.name.take(2).uppercase(),
                        style = TrippinType.Title,
                        color = ComicPaper,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = destination.name,
                    style = TrippinType.Heading,
                    color = ComicInk
                )
                Text(
                    text = destination.country,
                    style = TrippinType.Caption,
                    color = ComicMuted
                )
                Text(
                    text = "Plan a trip here",
                    style = TrippinType.Label,
                    color = AccentCrimson
                )
            }
        }
    }
}

/**
 * The way in for someone who was invited. Without this the card's "people have not joined yet" line
 * would be a promise with no door, which is the defect the audit named.
 */
@Composable
private fun JoinPrompt(onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "JOINED BY INVITE?",
            style = TrippinType.Label,
            color = ComicMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .border(2.dp, ComicInk, RoundedCornerShape(12.dp)),
            color = ComicPaper,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Join a trip with a code",
                    style = TrippinType.Heading,
                    color = ComicInk
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Someone planned a trip and sent you a code. Enter it and your name, and the " +
                        "trip opens with their plan in it.",
                    style = TrippinType.Body,
                    color = ComicMuted
                )
            }
        }
    }
}

/** One code on the clipboard, and nothing else. */
private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

@Composable
private fun EmptyTripsPanel(
    title: String,
    body: String,
    onPlan: () -> Unit,
    fillHeight: Boolean = true
) {
    Box(
        modifier = Modifier
            .then(if (fillHeight) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
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
                    style = TrippinType.Heading,
                    color = ComicInk
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    style = TrippinType.Label,
                    color = ComicMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCrimson),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .border(2.dp, ComicInk, RoundedCornerShape(8.dp))
                ) {
                    Text("Plan a trip", style = TrippinType.Label)
                }
            }
        }
    }
}

@Composable
private fun TripRow(
    trip: TripSummaryDto,
    state: TripState,
    today: LocalDate,
    onOpen: () -> Unit,
    onToday: () -> Unit,
    onDelete: () -> Unit,
    onInvite: () -> Unit
) {
    val photo = trip.heroImageUrl?.takeIf { it.isNotBlank() }
    val countdown = countdownLine(trip, today)
    val cost = costLine(trip)
    val start = parseDate(trip.startDate)
    val facts = listOfNotNull(countdown, travellersLine(trip)).joinToString(" \u00b7 ")
    val needsYou = needsYouLine(trip, state)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() },
        color = Panel,
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // The date, because that is the fact a traveller scans a list for, in tabular figures so
            // a column of trips lines up instead of wobbling.
            Column(modifier = Modifier.width(52.dp)) {
                Text(
                    text = start?.dayOfMonth?.toString() ?: "--",
                    style = TrippinType.Numeric.copy(fontSize = 24.sp),
                    color = Ink
                )
                Text(
                    text = start?.month?.name?.take(3)?.uppercase() ?: "",
                    style = TrippinType.Caption,
                    color = InkMuted
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = trip.destination, style = TrippinType.Heading, color = Ink)
                if (facts.isNotBlank()) {
                    Text(
                        text = facts,
                        style = TrippinType.Caption,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RowStateChip(state)
                    Spacer(modifier = Modifier.weight(1f))
                    if (needsYou != null && state != TripState.DRAFT) {
                        // Reachable from here, so the row may mention it. A row must never promise
                        // something it cannot deliver.
                        Text(
                            text = "Invite",
                            style = TrippinType.Label,
                            color = AccentCrimson,
                            modifier = Modifier
                                .clickable { onInvite() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    } else if (cost != null) {
                        Text(text = cost, style = TrippinType.NumericSmall, color = Ink)
                    } else {
                        Text(
                            text = if (trip.totalActivitiesCount > 0) "Not priced yet" else "No plan yet",
                            style = TrippinType.Caption,
                            color = InkMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // A real photograph of the destination when the wire carries one, and the city's own two
            // letters when it does not. Never a blank box, and never somebody else's city.
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeutralInkSurface),
                contentAlignment = Alignment.Center
            ) {
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = initialsOf(trip.destination),
                        style = TrippinType.Heading,
                        color = InkMuted
                    )
                }
            }
        }
    }
}

/** The two letters a row shows when there is no photograph, so the fallback stays a mark. */
private fun initialsOf(destination: String): String =
    destination.substringBefore(',')
        .trim()
        .take(2)
        .uppercase()
/**
 * The state chip. The label is one of the four derived states and nothing else, so a row can never
 * print a status the engine did not earn. None of these is crimson: crimson is for what you tap.
 * A locked plan is the named good state, so it takes the good ink on the good surface, the same
 * pair the locked banner on the Plan screen uses.
 */
@Composable
private fun RowStateChip(state: TripState) {
    val background = when (state) {
        TripState.DRAFT -> NeutralInkSurface
        TripState.DECIDING -> WarnAmberSurface
        TripState.LOCKED -> GoodInkSurface
        TripState.FINISHED -> NeutralInkSurface
    }
    val textColor = when (state) {
        TripState.DRAFT -> NeutralInk
        TripState.DECIDING -> WarnAmber
        TripState.LOCKED -> GoodInk
        TripState.FINISHED -> InkMuted
    }
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(5.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = state.label.uppercase(), style = TrippinType.Caption, color = textColor)
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
 * something the app can read off the trip.
 *
 * The audit's rule 3 applies here: never promise an action the app cannot take. So "have not joined
 * yet" may only appear when an invite can actually be sent, which is when the trip has a share token
 * or its owner can create one. Without that the line states the fact without promising a way out.
 */
private fun needsYouLine(trip: TripSummaryDto, state: TripState): String? {
    if (state == TripState.FINISHED) return null
    if (state == TripState.DRAFT) return "Needs you: no plan yet"
    val missing = trip.travelersCount - trip.travellers.size
    if (missing > 0) {
        val people = if (missing == 1) "1 person" else "$missing people"
        return if (!trip.shareToken.isNullOrBlank()) {
            "Needs you: invite $people"
        } else {
            "Needs you: $people have not added their details yet"
        }
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
