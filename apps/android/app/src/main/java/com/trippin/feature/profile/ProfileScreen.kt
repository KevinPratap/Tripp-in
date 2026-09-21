package com.trippin.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.cache.SavedSpotsManager
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.*
import com.trippin.core.network.SessionStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {}
) {
    val context = LocalContext.current
    val commitHaptic = rememberCommitHaptic()
    var showEngineDetails by remember { mutableStateOf(false) }

    // The account this app is signed in as. It comes from POST /auth/verify and it is the only
    // identity there is: the per-device guest id this screen used to show is gone.
    val account = SessionStore.account.value
    val accountEmail = account?.email ?: "Not signed in"
    val accountName = account?.displayName ?: "Traveller"

    val feed = TripCacheManager.homeFeedState.value
    val trips = feed?.recentTrips ?: emptyList()
    // The count the server reports, falling back to what is on this device. The list in the feed is
    // capped, so the fallback is only ever a lower bound.
    val tripCount = feed?.totalTripsCount ?: trips.size
    val tripWord = if (tripCount == 1) "trip" else "trips"
    val placeWord = if (SavedSpotsManager.savedSpots.size == 1) "saved place" else "saved places"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "You",
                            style = TrippinType.Heading
                        )
                        Text(
                            // The session is the only source of truth here, so this line cannot
                            // report a sign in that has not happened.
                            text = if (account == null) "Not signed in, guest on this device" else "Signed in",
                            style = TrippinType.Caption,
                            color = InkMuted
                        )
                    }
                },
                // Trips is a top-level tab now, so there is nothing behind it to go back to.
                navigationIcon = {}
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ComicPaper),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Official Passport Booklet Card
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, ComicInk),
                        colors = CardDefaults.cardColors(containerColor = ComicPanel),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(Ink)
                                            .border(1.5.dp, ComicInk, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Public, contentDescription = null, tint = ComicPaper, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Your trips",
                                            style = TrippinType.Heading,
                                            color = ComicInk
                                        )
                                        Text(
                                            text = "$tripCount $tripWord · ${SavedSpotsManager.savedSpots.size} $placeWord",
                                            style = TrippinType.Caption,
                                            color = ComicMuted
                                        )
                                    }
                                }

                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Collectible Stamps
                            Text(
                                text = "Where you have been",
                                style = TrippinType.Caption,
                                color = ComicMuted,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            /*
                             * One stamp per trip that exists, and nothing else. Offering a stamp for a
                             * city nobody has been to is a promise the app cannot keep, so there is no
                             * standing list of city names here any more.
                             */
                            if (trips.isEmpty()) {
                                Text(
                                    text = "Trips you take show up here as stamps.",
                                    style = TrippinType.Label,
                                    color = ComicMuted
                                )
                            } else {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    itemsIndexed(trips) { index, trip ->
                                        /*
                                         * A stamp lands when the trip is real: the same 600ms settle the
                                         * rest of the app uses, staggered so they fill in order.
                                         */
                                        StampLanding(delayMillis = index * 40) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = AccentCrimson.copy(alpha = 0.08f),
                                                modifier = Modifier.border(
                                                    width = 2.dp,
                                                    color = AccentCrimson,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.CheckCircle,
                                                        contentDescription = null,
                                                        tint = AccentCrimson,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = trip.destination.uppercase(),
                                                        style = TrippinType.Caption,
                                                        color = ComicInk
                                                    )
                                                    Text(
                                                        text = monthYear(trip.startDate),
                                                        style = TrippinType.Caption,
                                                        color = AccentCrimson
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

            // 2. Traveler Identity & Session
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, ComicInk, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "This device",
                                style = TrippinType.Caption,
                                color = ComicMuted,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = accountName,
                            style = TrippinType.Title,
                            color = ComicInk
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = accountEmail,
                            style = TrippinType.Body,
                            color = ComicMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your trips are kept on this account, so the same account sees the " +
                                "same trips on any phone. Nothing is tied to this device.",
                            style = TrippinType.Body,
                            color = ComicMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    copyToClipboard(context, "My Tripp'in account", accountEmail)
                                    commitHaptic()
                                    Toast.makeText(context, "Email copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ComicInk)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy email", style = TrippinType.Label, color = ComicInk)
                            }

                            Button(
                                onClick = onNavigateToTrips,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentCrimson),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.CardTravel, contentDescription = null, tint = ComicPaper, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("My trips", style = TrippinType.Label, color = ComicPaper)
                            }
                        }
                    }
                }
            }

            // 3. Account
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, ComicInk, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Account",
                            style = TrippinType.Label,
                            color = ComicMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Signing in is how a trip belongs to you rather than to a phone. " +
                                "There is no password to remember: the server issues a single use link.",
                            style = TrippinType.Body,
                            color = ComicMuted
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                commitHaptic()
                                SessionStore.clear()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("Sign out", style = TrippinType.Label, color = ComicInk)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Signing out removes the session from this phone. Your trips stay on " +
                                "the account and come back when you sign in again.",
                            style = TrippinType.Caption,
                            color = ComicMuted
                        )
                    }
                }
            }

            // 4. Engine Verification Guarantees (Collapsible)
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, ComicInk, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showEngineDetails = !showEngineDetails },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Where our numbers come from",
                                    style = TrippinType.Caption,
                                    color = ComicMuted,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Built from map data, opening hours and travel times",
                                    style = TrippinType.Caption,
                                    color = ComicInk
                                )
                            }
                            Icon(
                                imageVector = if (showEngineDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                tint = ComicInk
                            )
                        }

                        if (showEngineDetails) {
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(thickness = 1.dp, color = ComicInk.copy(alpha = 0.15f))
                            Spacer(modifier = Modifier.height(12.dp))

                            EngineRow(title = "Travel times", desc = "Drive times and distances, from OSRM")
                            Spacer(modifier = Modifier.height(10.dp))
                            EngineRow(title = "Places and opening hours", desc = "Real venues with their coordinates and hours, from OpenStreetMap")
                            Spacer(modifier = Modifier.height(10.dp))
                            EngineRow(title = "Weather", desc = "Forecasts for dates inside the forecast window, from Open-Meteo")
                            Spacer(modifier = Modifier.height(10.dp))
                            EngineRow(title = "The plan itself", desc = "Written by Google Gemini 2.5 Flash from the checked data above")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineRow(title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = TrippinType.Label, color = ComicInk)
            Text(desc, style = TrippinType.Caption, color = ComicMuted)
        }
    }
}

/** Sep 2026 out of 2026-09-27. Formatting only: nothing is claimed that the date does not say. */
private fun monthYear(date: String): String {
    val parts = date.split("-")
    val year = parts.getOrNull(0) ?: return date
    val monthNumber = parts.getOrNull(1)?.toIntOrNull() ?: return date
    val monthName = MONTH_NAMES.getOrNull(monthNumber - 1) ?: return date
    return "$monthName $year"
}

private val MONTH_NAMES = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
)

/** One string on the clipboard, and nothing else. */
private fun copyToClipboard(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}
