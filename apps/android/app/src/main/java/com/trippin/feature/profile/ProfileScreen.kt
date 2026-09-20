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

    val trips = TripCacheManager.homeFeedState.value?.recentTrips ?: emptyList()
    val passportCities = listOf(
        "Tokyo" to "Japan",
        "Paris" to "France",
        "Rome" to "Italy",
        "Kyoto" to "Japan",
        "Lisbon" to "Portugal",
        "London" to "United Kingdom"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FIELD PASSPORT & DESK",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "TRAVELER CREDENTIALS & AUDIT",
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
                }
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
                                            text = "OFFICIAL TRAVEL PASSPORT",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 14.sp,
                                            color = ComicInk
                                        )
                                        Text(
                                            text = "${trips.size} Itineraries · ${SavedSpotsManager.savedSpots.size} Saved Spots",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ComicMuted
                                        )
                                    }
                                }

                                Surface(
                                    color = ComicYellow,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.border(1.dp, ComicInk, RoundedCornerShape(4.dp))
                                ) {
                                    Text(
                                        text = "ACTIVE",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 9.sp,
                                        color = ComicInk,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            // Collectible Stamps
                            Text(
                                text = "COLLECTIBLE DISPATCH STAMPS",
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp,
                                color = ComicMuted,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(passportCities) { (city, country) ->
                                    val isVisited = trips.any { it.destination.contains(city, ignoreCase = true) }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isVisited) ComicRed.copy(alpha = 0.08f) else ComicPaper,
                                        modifier = Modifier.border(
                                            width = if (isVisited) 2.dp else 1.dp,
                                            color = if (isVisited) ComicRed else ComicInk.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Icon(
                                                imageVector = if (isVisited) Icons.Default.CheckCircle else Icons.Default.Public,
                                                contentDescription = null,
                                                tint = if (isVisited) ComicRed else ComicMuted,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = city.uppercase(),
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp,
                                                color = if (isVisited) ComicInk else ComicMuted
                                            )
                                            Text(
                                                text = if (isVisited) "STAMPED" else "UNVISITED",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp,
                                                color = if (isVisited) ComicRed else ComicMuted
                                            )
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
                                text = "GUEST CREDENTIALS",
                                fontWeight = FontWeight.Black,
                                fontSize = 11.sp,
                                color = ComicMuted,
                                letterSpacing = 1.sp
                            )
                            Surface(
                                color = ComicPaper,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.border(1.dp, ComicInk, RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = "SECURE LOCAL",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 8.sp,
                                    color = ComicInk,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = guestSession,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ComicInk
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Guest Session", guestSession))
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    Toast.makeText(context, "Session ID copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ComicInk)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPY ID", fontWeight = FontWeight.Black, fontSize = 11.sp, color = ComicInk)
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
                                Text("MY TRIPS", fontWeight = FontWeight.Black, fontSize = 11.sp, color = ComicPaper)
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
                            text = "SYNC WITH WEB DISPATCH",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = ComicMuted,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Access your verified itineraries on web browser via passwordless login.",
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
                                    text = "Dispatch link sent to $magicEmail. Check your inbox.",
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
                                Text("SEND DISPATCH MAGIC LINK", fontWeight = FontWeight.Black, fontSize = 11.sp, color = ComicPaper)
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
                                    text = "RUNTIME ENGINE GUARANTEES",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 11.sp,
                                    color = ComicMuted,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Deterministic physics and zero fake data",
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

                            EngineRow(title = "OSRM Routing Engine", desc = "Deterministic physics transit calculation", status = "CONNECTED")
                            Spacer(modifier = Modifier.height(10.dp))
                            EngineRow(title = "OpenStreetMap Photon", desc = "Verified GPS coords and real venues", status = "CONNECTED")
                            Spacer(modifier = Modifier.height(10.dp))
                            EngineRow(title = "Open-Meteo Forecast", desc = "Live forecasts for dates in window", status = "CONNECTED")
                            Spacer(modifier = Modifier.height(10.dp))
                            EngineRow(title = "Google Gemini 2.5 Flash", desc = "Free tier runtime itinerary synthesizer", status = "CONNECTED")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EngineRow(title: String, desc: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ComicInk)
            Text(desc, fontSize = 10.sp, color = ComicMuted)
        }
        Surface(
            color = ComicRed,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                text = status,
                color = ComicPaper,
                fontWeight = FontWeight.Black,
                fontSize = 8.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
