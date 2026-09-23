package com.trippin.feature.trips

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.AccentCrimson
import com.trippin.core.design.ArriveOnEnter
import com.trippin.core.design.DangerCrimson
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.GoodInk
import com.trippin.core.design.GoodInkSurface
import com.trippin.core.design.Ink
import com.trippin.core.design.InkMuted
import com.trippin.core.design.NeutralInk
import com.trippin.core.design.NeutralInkSurface
import com.trippin.core.design.OnCrimson
import com.trippin.core.design.Panel
import com.trippin.core.design.WarnAmber
import com.trippin.core.design.WarnAmberSurface
import com.trippin.core.design.Paper
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinRefreshIndicator
import com.trippin.core.design.TrippinSegmentedTabs
import com.trippin.core.design.TrippinType
import com.trippin.core.design.rememberCommitHaptic
import com.trippin.core.design.trippinButtonColors
import com.trippin.core.design.trippinFieldInk
import com.trippin.core.design.trippinTextButtonColors
import com.trippin.core.network.DestinationCardDto
import com.trippin.core.network.JoinTripRequestDto
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripSummaryDto
import com.trippin.feature.group.TRAVELLER_INTEREST_WORDS
import com.trippin.feature.group.TRAVELLER_PACE_CHOICES
import kotlinx.coroutines.launch
import retrofit2.HttpException
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
    val commitHaptic = rememberCommitHaptic()
    val context = LocalContext.current
    val today = LocalDate.now()
    // The pull to refresh indicator follows the finger through this state and PullToRefreshBox makes
    // its own when none is passed, so the state is hoisted here and handed to the box and to the
    // indicator it draws.
    val refreshState = rememberPullToRefreshState()

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
                commitHaptic()
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

    /**
     * Joins a trip from an invite code. Only what the person filled in is sent: a blank cap is left
     * out of the request rather than sent as a zero, because a zero would be stored as a real cap of
     * zero and would read as that person's own decision, and the fields nobody touched are left out
     * entirely, so a person who joins without deciding anything still joins and their row says Not
     * set until they fill it in. The name and the code are the only two the server insists on.
     */
    fun join(tripCode: String, name: String, cap: Double?, pace: String?, interests: List<String>) {
        scope.launch {
            joinBusy = true
            joinError = null
            try {
                val joined = NetworkModule.apiService.joinTrip(
                    JoinTripRequestDto(
                        token = tripCode.trim(),
                        name = name.trim(),
                        budgetCap = cap,
                        interests = interests,
                        pace = pace
                    )
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
                    .background(Ink)
                    .padding(start = 20.dp, end = 14.dp, top = 20.dp, bottom = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "TRIPS", style = TrippinType.Title, color = Paper)
                        if (trips.isNotEmpty()) {
                            Text(
                                text = tripCountLine(
                                    trips.size,
                                    ordered.count { it.second != TripState.FINISHED }
                                ),
                                style = TrippinType.Caption,
                                color = Paper.copy(alpha = 0.72f),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                    IconButton(
                        onClick = { onNavigateToPlanner(null) },
                        modifier = Modifier
                            .size(44.dp)
                            .border(1.5.dp, Paper.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
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
            state = refreshState,
            indicator = { TrippinRefreshIndicator(state = refreshState, isRefreshing = isRefreshing) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Paper)
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
                                    commitHaptic()
                                    selectedFilter = index
                                }
                                .padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = label.uppercase(),
                                style = TrippinType.Label,
                                color = if (isOn) Ink else InkMuted
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
                                    color = InkMuted
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
                            itemsIndexed(
                                items = visible,
                                key = { _, item -> item.first.id }
                            ) { index, (trip, state) ->
                                // Design system section 4: a list assembles rather than appearing, so
                                // each card settles 60ms after the one above it. Capped at six, because
                                // past that the last card would still be arriving after the first one
                                // has already been read.
                                val arrivalDelay = index.coerceAtMost(6) * 60
                                val dismissState = rememberSwipeToDismissBoxState(
                                    confirmValueChange = { value ->
                                        if (value == SwipeToDismissBoxValue.EndToStart) {
                                            commitHaptic()
                                            tripToDelete = trip
                                        }
                                        false
                                    }
                                )

                                ArriveOnEnter(delayMillis = arrivalDelay) {
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
                                                    // The reveal is a filled crimson control, so the icon and the
                                                    // word on it take OnCrimson, the token for ink on a crimson
                                                    // fill, rather than the page's Paper.
                                                    Icon(
                                                        Icons.Default.DeleteOutline,
                                                        contentDescription = null,
                                                        tint = OnCrimson,
                                                        modifier = Modifier.size(24.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Text(
                                                        "Delete",
                                                        color = OnCrimson,
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
                                            color = InkMuted,
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
                containerColor = Panel,
                titleContentColor = Ink,
                textContentColor = Ink,
                title = { Text("Delete this trip", style = TrippinType.Heading) },
                text = {
                    Text(
                        "Delete the trip to ${pendingDelete.destination}? Its plan goes with it.",
                        style = TrippinType.Label,
                    )
                },
                confirmButton = {
                    Button(
                        colors = trippinButtonColors(DangerCrimson),
                        enabled = !isDeleting,
                        onClick = { handleDeleteTrip(pendingDelete.id) }
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current
                            )
                        } else {
                            Text("Delete", style = TrippinType.Label)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !isDeleting,
                        onClick = { tripToDelete = null },
                        colors = trippinTextButtonColors()
                    ) {
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
                containerColor = Panel,
                titleContentColor = Ink,
                textContentColor = Ink,
                title = {
                    Text("Invite people to ${inviting.destination}", style = TrippinType.Heading)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        when {
                            inviteError != null -> Text(
                                text = inviteError.orEmpty(),
                                style = TrippinType.Body,
                                color = Ink
                            )
                            inviteCode == null -> Text(
                                text = "Asking the server for this trip's invite code.",
                                style = TrippinType.Body,
                                color = InkMuted
                            )
                            else -> {
                                Text(
                                    text = inviteCode.orEmpty(),
                                    style = TrippinType.Heading,
                                    color = Ink
                                )
                                Text(
                                    text = "Copied to the clipboard. Send it to your friends, and they " +
                                        "tap Join a trip on the Trips screen and enter it.",
                                    style = TrippinType.Body,
                                    color = InkMuted
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    if (inviteCode != null) {
                        Button(
                            colors = trippinButtonColors(),
                            onClick = {
                                copyToClipboard(context, "Tripp'in invite code", inviteCode.orEmpty())
                            }
                        ) {
                            Text("Copy code", style = TrippinType.Label)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { inviteTrip = null; inviteCode = null; inviteError = null },
                        colors = trippinTextButtonColors()
                    ) {
                        Text("Done", style = TrippinType.Label)
                    }
                }
            )
        }

        // Joining a trip someone else invited you to. This is the reachable join action the Trips
        // card promises when it says people have not joined yet.
        if (showJoinSheet) {
            JoinTripDialog(
                isBusy = joinBusy,
                error = joinError,
                onDismiss = {
                    if (!joinBusy) {
                        showJoinSheet = false
                        joinError = null
                    }
                },
                onJoin = { code, name, cap, pace, interests ->
                    join(code, name, cap, pace, interests)
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
            .border(2.dp, Ink, RoundedCornerShape(12.dp)),
        color = Panel,
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
                    .background(Ink)
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
                        color = Paper,
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
                    color = Ink
                )
                Text(
                    text = destination.country,
                    style = TrippinType.Caption,
                    color = InkMuted
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
 * Joining a trip. The code and the name are the only two fields the server insists on; the cap, the
 * pace and the interests are optional, and a field left blank is left out of the request rather than
 * sent as a zero or an empty word, so somebody who has not decided anything still joins and their row
 * reads Not set until they fill it in themselves.
 *
 * The cap is a number in the trip's own currency and the label carries no symbol, because a person
 * holding only an invite code cannot see the trip's currency until the join succeeds, and guessing
 * one from the phone would put a currency on a number that was never priced in it.
 *
 * The words offered are the app's one copy of the traveller vocabulary, read off the Group tab's list
 * rather than repeated here; the server keeps only the words on that list.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun JoinTripDialog(
    isBusy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onJoin: (String, String, Double?, String?, List<String>) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var capText by remember { mutableStateOf("") }
    var pace by remember { mutableStateOf<String?>(null) }
    var interests by remember { mutableStateOf(emptyList<String>()) }

    val cap = capText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val capProblem = when {
        capText.isBlank() -> null
        cap == null || cap <= 0.0 -> "Enter your cap as a number, or leave the field blank."
        else -> null
    }
    val canJoin = code.isNotBlank() && name.isNotBlank() && capProblem == null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = Ink,
        textContentColor = Ink,
        title = { Text("Join a trip", style = TrippinType.Heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Enter the invite code the trip's owner sent you, and the name the " +
                        "others will see on this trip. Only what you fill in here is saved.",
                    style = TrippinType.Caption,
                    color = InkMuted
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    enabled = !isBusy,
                    label = { Text("Invite code", style = TrippinType.Label) },
                    textStyle = TrippinType.Body,
                    colors = trippinFieldInk(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(8.dp))
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { typed -> if (typed.length <= 60) name = typed },
                    singleLine = true,
                    enabled = !isBusy,
                    label = { Text("Your name", style = TrippinType.Label) },
                    textStyle = TrippinType.Body,
                    colors = trippinFieldInk(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(8.dp))
                )
                OutlinedTextField(
                    value = capText,
                    onValueChange = { typed ->
                        if (typed.all { it.isDigit() || it == '.' || it == ',' }) capText = typed
                    },
                    singleLine = true,
                    enabled = !isBusy,
                    label = { Text("Your budget cap (optional)", style = TrippinType.Label) },
                    textStyle = TrippinType.Body,
                    colors = trippinFieldInk(),
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(8.dp))
                )
                Text(
                    text = "Your cap is a number in the trip's own currency.",
                    style = TrippinType.Caption,
                    color = InkMuted
                )
                capProblem?.let {
                    Text(text = it, style = TrippinType.Caption, color = DangerCrimson)
                }
                Text(text = "Pace", style = TrippinType.Caption, color = InkMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TRAVELLER_PACE_CHOICES.forEach { (value, label) ->
                        TrippinChoiceChip(
                            text = label,
                            selected = pace == value,
                            onClick = { pace = if (pace == value) null else value }
                        )
                    }
                }
                Text(text = "What you want to see", style = TrippinType.Caption, color = InkMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TRAVELLER_INTEREST_WORDS.forEach { word ->
                        TrippinChoiceChip(
                            text = word,
                            selected = interests.contains(word),
                            onClick = {
                                interests = if (interests.contains(word)) {
                                    interests - word
                                } else {
                                    interests + word
                                }
                            }
                        )
                    }
                }
                if (error != null) {
                    Text(text = error, style = TrippinType.Body, color = DangerCrimson)
                }
            }
        },
        confirmButton = {
            Button(
                colors = trippinButtonColors(),
                enabled = !isBusy && canJoin,
                onClick = { onJoin(code, name, cap, pace, interests) }
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text("Join", style = TrippinType.Label)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                colors = trippinTextButtonColors()
            ) {
                Text("Cancel", style = TrippinType.Label)
            }
        }
    )
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
            color = InkMuted,
            modifier = Modifier.padding(top = 8.dp)
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .border(2.dp, Ink, RoundedCornerShape(12.dp)),
            color = Paper,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Join a trip with a code",
                    style = TrippinType.Heading,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Someone planned a trip and sent you a code. Enter it and your name, and the " +
                        "trip opens with their plan in it.",
                    style = TrippinType.Body,
                    color = InkMuted
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
                .border(2.5.dp, Ink, RoundedCornerShape(12.dp)),
            color = Panel,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = title,
                    style = TrippinType.Heading,
                    color = Ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    style = TrippinType.Label,
                    color = InkMuted
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onPlan,
                    colors = trippinButtonColors(),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .height(44.dp)
                        .border(2.dp, Ink, RoundedCornerShape(8.dp))
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
                    style = TrippinType.Numeric,
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
                        // Reachable from here, so the row may mention it. Set in ink with an
                        // underline rather than crimson: five crimson words stacked down the list is
                        // the same shouting this screen just lost, one size smaller.
                        Text(
                            text = "Invite",
                            style = TrippinType.Label,
                            color = Ink,
                            textDecoration = TextDecoration.Underline,
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

/**
 * Who is on this trip, counted from the traveller list the server sends, never from travelersCount.
 *
 * travelersCount is the size the trip was set up for and not a headcount: the frozen interface says
 * it is derived from the traveller list, and on the live wire every trip carries 2 while its
 * travellers array is empty, so printing it as people would claim two travellers who never joined
 * and would disagree with the Group tab, which already counts the list. So the line counts the list,
 * and the set-up size is stated as a plan only while nobody has added their details, in the words
 * GroupScreen already uses for the same fact.
 */
private fun travellersLine(trip: TripSummaryDto): String {
    val added = trip.travellers.size
    return when {
        added == 1 -> "1 traveller"
        added > 1 -> "$added travellers"
        trip.travelersCount == 1 -> "Set up for 1 traveller"
        trip.travelersCount > 1 -> "Set up for ${trip.travelersCount} travellers"
        else -> "Nobody has added their details yet"
    }
}

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
        "${formatStatedAmount(low, currency)} per person"
    } else {
        "${formatStatedAmount(low, currency)} to ${formatStatedAmount(high, currency)} per person"
    }
}


