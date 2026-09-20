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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.cache.SavedSpotsManager
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var magicEmail by remember { mutableStateOf("") }
    var emailSent by remember { mutableStateOf(false) }
    var showEngineDetails by remember { mutableStateOf(false) }

    val guestSession = remember {
        "guest-" + android.os.Build.MODEL.replace(" ", "-").lowercase()
    }

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
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Not signed in, guest on this device",
                            style = MaterialTheme.typography.labelSmall,
                            color = ComicRed,
                            fontWeight = FontWeight.Bold
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
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 4.dp, y = 4.dp)
                            .background(ComicInk, RoundedCornerShape(12.dp))
                    )
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
                                            .background(ComicRed)
                                            .border(1.5.dp, ComicInk, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.Public, contentDescription = null, tint = ComicPaper, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Your trips",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = ComicInk
                                        )
                                        Text(
                                            text = "$tripCount $tripWord · ${SavedSpotsManager.savedSpots.size} $placeWord",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ComicMuted
                                        )
                                    }
                                }

                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Collectible Stamps
                            Text(
                                text = "Where you have been",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
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
                                    fontSize = 13.sp,
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
                                                color = ComicRed.copy(alpha = 0.08f),
                                                modifier = Modifier.border(
                                                    width = 2.dp,
                                                    color = ComicRed,
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
                                                        tint = ComicRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(
                                                        text = trip.destination.uppercase(),
                                                        fontWeight = FontWeight.Black,
                                                        fontSize = 12.sp,
                                                        color = ComicInk
                                                    )
                                                    Text(
                                                        text = monthYear(trip.startDate),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp,
                                                        color = ComicRed
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
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = ComicMuted,
                                letterSpacing = 1.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = guestSession,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ComicInk
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Not signed in. Trips made on this phone are kept under this id.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ComicMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("This device", guestSession))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "Id copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ComicInk)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy id", fontWeight = FontWeight.Black, fontSize = 12.sp, color = ComicInk)
                            }

                            Button(
                                onClick = onNavigateToTrips,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.CardTravel, contentDescription = null, tint = ComicPaper, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("My trips", fontWeight = FontWeight.Black, fontSize = 12.sp, color = ComicPaper)
                            }
                        }
                    }
                }
            }

            // 3. Sync with Web Dispatch (Magic Link)
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
                            text = "Sign in",
                            fontWeight = FontWeight.Black,
                            fontSize = 12.sp,
                            color = ComicMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "See your plans on the web. We email you a link, so there is nothing to remember.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ComicMuted
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (emailSent) {
                            Surface(
                                color = ComicYellow,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth().border(1.dp, ComicInk, RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = "Link sent to $magicEmail. Check your inbox.",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = ComicInk,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        } else {
                            TextField(
                                value = magicEmail,
                                onValueChange = { magicEmail = it },
                                placeholder = { Text("Enter your email address...", fontSize = 13.sp) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = ComicPaper,
                                    unfocusedContainerColor = ComicPaper,
                                    disabledContainerColor = ComicPaper,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = {
                                    if (magicEmail.contains("@")) {
                                        emailSent = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val webUrl = "https://web-production-a9ec6.up.railway.app/login?email=${Uri.encode(magicEmail)}"
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                                        context.startActivity(intent)
                                    }
                                },
                                enabled = magicEmail.contains("@"),
                                colors = ButtonDefaults.buttonColors(containerColor = ComicInk),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                            ) {
                                Text("Send link", fontWeight = FontWeight.Black, fontSize = 12.sp, color = ComicPaper)
                            }
                        }
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
                                    fontWeight = FontWeight.Black,
                                    fontSize = 12.sp,
                                    color = ComicMuted,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Built from map data, opening hours and travel times",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
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
            Text(title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = ComicInk)
            Text(desc, fontSize = 12.sp, color = ComicMuted)
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
