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
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.*
import com.trippin.core.network.NetworkModule
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
    var loadFailed by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val commitHaptic = rememberCommitHaptic()
    // The pull to refresh indicator follows the finger through this state and PullToRefreshBox makes
    // its own when none is passed, so the state is hoisted here and handed to the box and to the
    // indicator it draws.
    val refreshState = rememberPullToRefreshState()

    val loadTripData: (isManual: Boolean) -> Unit = { isManual ->
        scope.launch {
            if (isManual) isRefreshing = true
            else if (tripDetails == null) isLoading = true
            loadFailed = false
            try {
                val fetched = NetworkModule.apiService.getTripDetails(tripId)
                tripDetails = fetched
                TripCacheManager.putTrip(tripId, fetched)
            } catch (_: Exception) {
                /* A failed load is stated on the screen rather than guessed at, and the cause is
                 * not claimed here: this one catch covers being offline and a trip that is not
                 * there alike. The exception's own message is deliberately not shown, because it
                 * reads as HTTP 404 or Unable to resolve host, and neither is something a person
                 * on a trip can act on. */
                if (tripDetails == null) loadFailed = true
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
    /* Only ever the destination the server sent. There is no stand-in, because a bar that names a
     * place the trip does not have is worse than one that names none, which is the rule the Map
     * screen already follows. */
    val destination = tripDetails?.trip?.destination

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
                colors = trippinTopBarColors(),
                title = {
                    Column {
                        Text(
                            // The bar may only say Today when the day under it really is today, and
                            // it may only name the destination once the trip carrying it has
                            // arrived. Until then it says what the screen is, not what the trip
                            // contains.
                            text = when {
                                destination == null -> "Today"
                                dayIsToday -> "Today · $destination"
                                activeDay != null -> "Day ${activeDay.dayIndex} · $destination"
                                else -> destination
                            }.uppercase(),
                            style = TrippinType.Heading
                        )
                        Text(
                            text = when {
                                tripDetails == null -> if (loadFailed) "Trip not loaded" else "Loading the trip"
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
                        Icon(Icons.Default.Map, contentDescription = "Map", tint = Ink)
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
                    CircularProgressIndicator(color = AccentCrimson)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Loading today's plan...",
                        style = TrippinType.Body,
                        color = Ink
                    )
                }
            }
        } else if (loadFailed && tripDetails == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    /* The app's one error recipe, the same one the Map screen draws. No cause is
                     * named, because a load this screen cannot complete is not necessarily a
                     * network problem: a trip this session does not own answers 404 the same way
                     * a dead connection does. */
                    Text(
                        "Could not load today's plan",
                        color = DangerCrimson,
                        style = TrippinType.Title
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { loadTripData(false) },
                        colors = trippinButtonColors()
                    ) {
                        Text("Retry", style = TrippinType.Label)
                    }
                }
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { loadTripData(true) },
                state = refreshState,
                indicator = { TrippinRefreshIndicator(state = refreshState, isRefreshing = isRefreshing) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(Paper)
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
                                .border(2.5.dp, Ink, RoundedCornerShape(8.dp)),
                            color = Panel,
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
                                            color = Ink
                                        )
                                    }
                                    if (!activeDay?.weatherSummary.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = activeDay.weatherSummary,
                                            style = TrippinType.Caption,
                                            color = InkMuted
                                        )
                                    }
                                }

                                /* Only when the day on screen really is today. Nothing else here
                                 * claims to be live. */
                                if (dayIsToday) {
                                    Surface(
                                        color = NeutralInkSurface,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.border(1.5.dp, Ink, RoundedCornerShape(4.dp))
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
                                    .border(2.5.dp, Ink, RoundedCornerShape(12.dp)),
                                color = Panel,
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
                                            // Photo Source Tag. The source is named only when the
                                            // app can read it off the URL's host; a host it does
                                            // not recognise gets no tag rather than a claim.
                                            val credit = photoCredit(photoUrl)
                                            if (credit != null) {
                                                Surface(
                                                    color = Ink.copy(alpha = 0.75f),
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier
                                                        .align(Alignment.BottomEnd)
                                                        .padding(8.dp)
                                                ) {
                                                    Text(
                                                        text = credit,
                                                        color = Paper,
                                                        style = TrippinType.Caption,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                        HorizontalDivider(thickness = 2.dp, color = Ink)
                                    } else {
                                        // Half of a real trip's stops have no genuine photograph of
                                        // the venue on Wikidata, and a stand-in image is not allowed.
                                        // The plate names the place in its own letters instead.
                                        PlacePlate(
                                            title = currentStop.title,
                                            category = currentStop.type,
                                            height = 160.dp
                                        )
                                        HorizontalDivider(thickness = 2.dp, color = Ink)
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
                                                    color = if (isRunningNow) AccentCrimson else NeutralInkSurface,
                                                    shape = RoundedCornerShape(4.dp),
                                                    modifier = Modifier.border(1.5.dp, Ink, RoundedCornerShape(4.dp))
                                                ) {
                                                    Text(
                                                        text = stopTimeLabel,
                                                        // A filled crimson chip takes OnCrimson, not a card
                                                        // surface: the same white reads 4.70 on the accent
                                                        // where the parchment page's Paper reads 4.43.
                                                        color = if (isRunningNow) OnCrimson else NeutralInk,
                                                        style = TrippinType.Caption,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                    )
                                                }
                                            }

                                            Text(
                                                text = "${currentStop.startTime} - ${currentStop.endTime}",
                                                style = TrippinType.NumericSmall,
                                                color = Ink
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = currentStop.title,
                                            style = TrippinType.Title,
                                            color = Ink
                                        )

                                        if (!currentStop.reason.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = currentStop.reason,
                                                style = TrippinType.Body,
                                                color = Ink.copy(alpha = 0.8f)
                                            )
                                        }

                                        // An address is either navigable or it is not printed as one,
                                        // which is the same rule the Plan stop card applies through
                                        // this same helper. The Today hero used to print the raw
                                        // field and offer to copy it, so a ward with no street was
                                        // shown as a place to go while the button beside it promised
                                        // to take somebody there. No street-level address means no
                                        // address line and no copy action, which is what this screen
                                        // already does when the map data holds none at all.
                                        val address = currentStop.place?.formattedAddress
                                            .orEmpty()
                                            .takeIf { isStreetLevelAddress(it) }
                                            .orEmpty()
                                        if (address.isNotBlank()) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = address,
                                                style = TrippinType.Body,
                                                color = InkMuted
                                            )
                                        }

                                        if (currentStop.travelTimeFromPreviousMinutes > 0) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Surface(
                                                color = Paper,
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.border(1.dp, Ink.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
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
                                                        color = Ink
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
                                                        commitHaptic()
                                                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                                    },
                                                    modifier = Modifier
                                                        .size(48.dp)
                                                        .border(1.5.dp, Ink, RoundedCornerShape(8.dp))
                                                ) {
                                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy address")
                                                }
                                            }

                                            Button(
                                                onClick = {
                                                    val query = Uri.encode(
                                                        listOf(currentStop.title, destination)
                                                            .filterNotNull()
                                                            .joinToString(" ")
                                                    )
                                                    val webMapsUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                                    context.startActivity(Intent(Intent.ACTION_VIEW, webMapsUri))
                                                },
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .border(2.dp, Ink, RoundedCornerShape(8.dp)),
                                                colors = trippinButtonColors(),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Default.Navigation, contentDescription = null)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = "Take me there",
                                                    style = TrippinType.Label
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
                            // "Later today" is only true on the day that is today. The role decides
                            // the tracking: this line used to override it to 1sp.
                            text = if (dayIsToday) "Later today" else "Later that day",
                            style = TrippinType.Label,
                            color = InkMuted,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    if (remainingStops.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, Ink.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                                color = Panel,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    // A day the wire sent no date for is not today either, so the
                                    // fallback is the same word the top bar already uses for a day
                                    // that is not today: "this day". Claiming today would put the
                                    // clock's own word on a day the clock has not reached.
                                    text = if (dayIsToday) "That is every stop for today." else "That is every stop for this day.",
                                    style = TrippinType.Body,
                                    color = InkMuted,
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
                                    .border(2.dp, Ink, RoundedCornerShape(8.dp)),
                                color = Panel,
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
                                                .border(1.dp, Ink, RoundedCornerShape(6.dp))
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
                                                .border(1.dp, Ink, RoundedCornerShape(6.dp))
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
                                            style = TrippinType.NumericSmall,
                                            color = Ink
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = act.title,
                                            style = TrippinType.Heading,
                                            color = Ink
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(act.title)}")
                                            context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                                        },
                                        modifier = Modifier
                                            .size(44.dp)
                                            .border(1.5.dp, Ink, RoundedCornerShape(6.dp))
                                    ) {
                                        Icon(
                                            Icons.Default.Directions,
                                            contentDescription = "Navigate",
                                            tint = AccentCrimson,
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
