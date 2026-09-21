package com.trippin.feature.map

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.*
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripDetailsDto
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    tripId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /* A trip that has already been opened is in the cache, so the bar and the preview card can be
     * drawn from it while the refetch runs instead of leaving the screen blank. */
    val cachedTrip = remember(tripId) { TripCacheManager.getTrip(tripId) }
    var tripDetails by remember { mutableStateOf(cachedTrip) }
    var isLoading by remember { mutableStateOf(cachedTrip == null) }
    var loadFailed by remember { mutableStateOf(false) }

    val loadTrip: () -> Unit = {
        scope.launch {
            if (tripDetails == null) isLoading = true
            loadFailed = false
            try {
                val fetched = NetworkModule.apiService.getTripDetails(tripId)
                tripDetails = fetched
                TripCacheManager.putTrip(tripId, fetched)
            } catch (_: Exception) {
                /* A failed load is stated on the screen rather than swallowed. Nothing is drawn to
                 * fill the gap: no title, no card, no place name. */
                if (tripDetails == null) loadFailed = true
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(tripId) {
        loadTrip()
    }

    /* Only ever the destination the server sent. There is no stand-in, because a screen that names
     * a place the trip does not have is worse than one that names none. */
    val destination = tripDetails?.trip?.destination
    val firstDay = tripDetails?.itinerary?.days?.firstOrNull()
    val activities = firstDay?.activities ?: emptyList()
    val firstAct = activities.firstOrNull()
    val secondAct = activities.getOrNull(1)?.takeIf { it.id != firstAct?.id }
    /* The day this card shows is read off the day itself. "Day 1" was a literal, which is only
     * ever true while the first day really is the first one. */
    val dayLabel = firstDay?.let { "Day ${it.dayIndex}" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = destination?.uppercase() ?: "Map",
                            style = TrippinType.Heading
                        )
                        Text(
                            // The route line is only true once the trip is here. Before that the
                            // bar says what the screen is doing, not what the trip contains.
                            text = when {
                                destination != null -> "OpenStreetMap route"
                                isLoading -> "Loading the trip"
                                else -> "Trip not loaded"
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
                    IconButton(onClick = {
                        val webUrl = "https://web-production-a9ec6.up.railway.app/trip/$tripId"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser", tint = AccentCrimson)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (loadFailed && tripDetails == null) {
                /* The one place this screen says a load did not happen. The cause is not claimed,
                 * because this catch covers both being offline and a trip that is not there. */
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Could not load this trip",
                        style = TrippinType.Title,
                        color = DangerCrimson
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCrimson),
                        onClick = { loadTrip() }
                    ) {
                        Text("Retry", style = TrippinType.Label, color = Paper)
                    }
                }
            } else {
                // Real OpenStreetMap Leaflet Map WebView
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            webViewClient = WebViewClient()
                            loadUrl("https://web-production-a9ec6.up.railway.app/trip/$tripId")
                        }
                    }
                )

                // Bottom Floating Activity Preview Card
                if (firstAct != null) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp)
                            .border(2.5.dp, Ink, RoundedCornerShape(12.dp)),
                        color = Paper,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    // The day's first two stops are joined with the word "to", so
                                    // every character this screen draws is plain English.
                                    text = if (secondAct != null) {
                                        "${firstAct.title} to ${secondAct.title}"
                                    } else {
                                        firstAct.title
                                    },
                                    style = TrippinType.Heading,
                                    color = Ink
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    // The day's own day index, then the stop's own times. No day
                                    // claim at all if the day states none.
                                    text = listOfNotNull(
                                        dayLabel,
                                        "${firstAct.startTime} to ${firstAct.endTime}"
                                    ).joinToString(", "),
                                    style = TrippinType.Caption,
                                    color = InkMuted
                                )
                            }
                            Button(
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCrimson),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.border(1.5.dp, Ink, RoundedCornerShape(8.dp)),
                                onClick = {
                                    val query = Uri.encode("${firstAct.title}, $destination")
                                    val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    context.startActivity(mapIntent)
                                }
                            ) {
                                Icon(Icons.Default.Directions, contentDescription = null, tint = Paper)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Take me there", style = TrippinType.Label, color = Paper)
                            }
                        }
                    }
                }
            }
        }
    }
}
