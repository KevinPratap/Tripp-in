package com.trippin.feature.home

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trippin.core.cache.SavedSpotsManager
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.*
import com.trippin.core.network.DestinationCardDto
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripSummaryDto
import com.trippin.feature.trips.getDestinationHeroFallback
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlanner: (String?) -> Unit,
    onNavigateToTrip: (String) -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToExplore: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    var homeFeed by remember { mutableStateOf(TripCacheManager.homeFeedState.value) }
    var isRefreshing by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val loadHomeData: (isManual: Boolean) -> Unit = { isManual ->
        scope.launch {
            if (isManual) isRefreshing = true
            try {
                val feed = NetworkModule.apiService.getHome()
                homeFeed = feed
                TripCacheManager.homeFeedState.value = feed
            } catch (_: Exception) {
                // Keep cached data
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadHomeData(false)
    }

    val recentTrips = homeFeed?.recentTrips ?: emptyList()
    val activeTrip = recentTrips.firstOrNull()

    val curatedDestinations = remember(homeFeed) {
        val remote = homeFeed?.popularDestinations ?: emptyList()
        if (remote.isNotEmpty()) remote else listOf(
            DestinationCardDto("1", "Paris", "France", "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=600", "City of Light", 0.0, listOf("Culture", "Walkable")),
            DestinationCardDto("2", "Tokyo", "Japan", "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=600", "Metropolis of traditions", 0.0, listOf("Transit", "Food")),
            DestinationCardDto("3", "Rome", "Italy", "https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=600", "Open air history", 0.0, listOf("Ancient", "History")),
            DestinationCardDto("4", "Kyoto", "Japan", "https://images.unsplash.com/photo-1493976040374-85c8e12f0c0e?w=600", "Heritage temples", 0.0, listOf("Temples", "Gardens")),
            DestinationCardDto("5", "Lisbon", "Portugal", "https://images.unsplash.com/photo-1588614959060-4d144f28b207?w=600", "Coastal capital", 0.0, listOf("Coastal", "Hills")),
            DestinationCardDto("6", "London", "United Kingdom", "https://images.unsplash.com/photo-1513635269975-59663e0ac1ad?w=600", "Iconic landmarks", 0.0, listOf("Museums", "Theatre"))
        )
    }

    val quickPicks = listOf(
        Pair("Tokyo", "5 Days / Ramen & Culture"),
        Pair("Paris", "3 Days / Art & Cafes"),
        Pair("Rome", "Weekend / History Walk"),
        Pair("Kyoto", "4 Days / Temples & Nature")
    )

    Scaffold(
        bottomBar = {
            val navItemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = ComicRed,
                selectedTextColor = ComicInk,
                indicatorColor = ComicRed.copy(alpha = 0.15f),
                unselectedIconColor = ComicInk.copy(alpha = 0.55f),
                unselectedTextColor = ComicInk.copy(alpha = 0.55f)
            )
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.border(width = 1.dp, color = ComicInk.copy(alpha = 0.15f))
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontWeight = FontWeight.Bold) },
                    selected = true,
                    onClick = {},
                    colors = navItemColors
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CardTravel, contentDescription = "Trips") },
                    colors = navItemColors,
                    label = { Text("Trips", fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = onNavigateToTrips
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                    colors = navItemColors,
                    label = { Text("Explore", fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = onNavigateToExplore
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    colors = navItemColors,
                    label = { Text("Profile", fontWeight = FontWeight.Bold) },
                    selected = false,
                    onClick = onNavigateToProfile
                )
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { loadHomeData(true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ComicPaper)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
                }

                // 1. Editorial Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "TRIPP'IN AI / FIELD DESK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = ComicRed
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Where to next?",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = ComicInk
                            )
                        }

                        // Avatar / Profile Shortcut
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(ComicPanel)
                                .border(2.dp, ComicInk, CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onNavigateToProfile()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = (homeFeed?.user?.displayName?.trim()
                                    ?.split(" ")
                                    ?.filter { it.isNotBlank() }
                                    ?.take(2)
                                    ?.map { it.first().uppercase() }
                                    ?.joinToString(""))
                                    ?.ifBlank { null } ?: "YOU",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Black,
                                color = ComicRed
                            )
                        }
                    }
                }

                // 2. HERO STAGE: Active Trip Boarding Pass or Quick Plan Launchpad
                if (activeTrip != null) {
                    item {
                        ArriveOnEnter {
                            ActiveTripHeroTicket(
                                trip = activeTrip,
                                onOpen = { onNavigateToTrip(activeTrip.id) },
                                onNewTrip = { onNavigateToPlanner(null) }
                            )
                        }
                    }
                } else {
                    item {
                        PlanTripLaunchpad(
                            quickPicks = quickPicks,
                            onPick = { city ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigateToPlanner(city)
                            },
                            onCustomPlan = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onNavigateToPlanner(null)
                            }
                        )
                    }
                }

                // 3. Traveler Passport & Saved Spots Ribbon (Non-intrusive, integrated)
                item {
                    PassportRibbon(
                        savedSpotsCount = SavedSpotsManager.savedSpots.size,
                        plannedTripsCount = recentTrips.size,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onNavigateToProfile()
                        }
                    )
                }

                // 4. Curated Field Destinations (Visual Photography)
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "FEATURED DESTINATIONS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = ComicMuted
                                )
                                Text(
                                    text = "Curated Field Guides",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = ComicInk
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(curatedDestinations) { dest ->
                                EditorialCityCard(
                                    destination = dest,
                                    onPlan = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onNavigateToPlanner(dest.name)
                                    }
                                )
                            }
                        }
                    }
                }

                // 5. Other Recent Trips (if multiple trips exist)
                if (recentTrips.size > 1) {
                    item {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "OTHER FIELD MANIFESTS",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = ComicMuted
                                )
                                TextButton(onClick = onNavigateToTrips) {
                                    Text(
                                        text = "VIEW ALL (${recentTrips.size})",
                                        fontWeight = FontWeight.Black,
                                        color = ComicRed,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            recentTrips.drop(1).take(2).forEach { trip ->
                                CompactTripRow(
                                    trip = trip,
                                    onClick = { onNavigateToTrip(trip.id) }
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * Hero Active Trip Boarding Pass. Gives user instant access to their itinerary.
 */
@Composable
private fun ActiveTripHeroTicket(
    trip: TripSummaryDto,
    onOpen: () -> Unit,
    onNewTrip: () -> Unit
) {
    val heroImg = trip.heroImageUrl ?: getDestinationHeroFallback(trip.destination)

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(ComicInk, RoundedCornerShape(12.dp))
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onOpen() },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, ComicInk),
            colors = CardDefaults.cardColors(containerColor = ComicPanel),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                // Photo Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                ) {
                    AsyncImage(
                        model = heroImg,
                        contentDescription = trip.destination,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        ComicInk.copy(alpha = 0.85f)
                                    ),
                                    startY = 60f
                                )
                            )
                    )

                    // Top badges
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = ComicInk,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "ACTIVE FIELD TICKET",
                                color = ComicPaper,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        TrippinStatusBadge(status = if (trip.isLocked) "LOCKED" else trip.status)
                    }

                    // Bottom title over photo
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                    ) {
                        Text(
                            text = trip.destination.uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Black,
                            color = PureWhite
                        )
                        Text(
                            text = "${trip.startDate} to ${trip.endDate} · ${trip.travelersCount} Travelers",
                            style = MaterialTheme.typography.bodySmall,
                            color = PureWhite.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onNewTrip,
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ComicInk, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DRAFT ANOTHER", fontWeight = FontWeight.Black, fontSize = 11.sp, color = ComicInk)
                    }

                    Button(
                        onClick = onOpen,
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.5.dp, ComicInk),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("RESUME ITINERARY", fontWeight = FontWeight.Black, fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/**
 * Inspiring Quick Plan Launchpad when no trips are active yet.
 */
@Composable
private fun PlanTripLaunchpad(
    quickPicks: List<Pair<String, String>>,
    onPick: (String) -> Unit,
    onCustomPlan: () -> Unit
) {
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
                    Text(
                        text = "PLAN A JOURNEY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = ComicRed
                    )
                    Surface(
                        color = ComicYellow,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.dp, ComicInk, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = "VERIFIED",
                            fontWeight = FontWeight.Black,
                            fontSize = 9.sp,
                            color = ComicInk,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Generate physics-verified itineraries with OSRM transit and Open-Meteo forecasts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ComicMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Quick Inspiration Chips
                Text(
                    text = "QUICK DISPATCH PROMPTS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = ComicInk,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    quickPicks.chunked(2).forEach { rowPicks ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPicks.forEach { pick ->
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onPick(pick.first) }
                                        .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                                    color = ComicPaper,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                        Text(
                                            text = pick.first,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 13.sp,
                                            color = ComicInk
                                        )
                                        Text(
                                            text = pick.second,
                                            fontSize = 9.sp,
                                            color = ComicMuted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Primary Custom Plan CTA
                Button(
                    onClick = onCustomPlan,
                    colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(2.dp, ComicInk),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(Icons.Default.AddLocationAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("CUSTOM DESTINATION PLAN", fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Non-intrusive, integrated Traveler Passport Ribbon.
 */
@Composable
private fun PassportRibbon(
    savedSpotsCount: Int,
    plannedTripsCount: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.5.dp, ComicInk, RoundedCornerShape(8.dp)),
        color = ComicPanel,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(ComicRed.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                        .border(1.dp, ComicRed, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Public, contentDescription = null, tint = ComicRed, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "FIELD TRAVEL PASSPORT",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = ComicInk
                    )
                    Text(
                        text = "$plannedTripsCount Trips Planned · $savedSpotsCount Bookmarked Spots",
                        fontSize = 11.sp,
                        color = ComicMuted
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "VIEW",
                    fontWeight = FontWeight.Black,
                    fontSize = 11.sp,
                    color = ComicRed
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = ComicRed, modifier = Modifier.size(12.dp))
            }
        }
    }
}

/**
 * Editorial Destination Card with real photography and a 1-tap "Plan" CTA.
 */
@Composable
private fun EditorialCityCard(
    destination: DestinationCardDto,
    onPlan: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(190.dp)
            .height(260.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(ComicInk, RoundedCornerShape(10.dp))
        )
        Card(
            modifier = Modifier
                .matchParentSize()
                .clickable { onPlan() },
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(2.dp, ComicInk),
            colors = CardDefaults.cardColors(containerColor = ComicPanel),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Photo
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                ) {
                    AsyncImage(
                        model = destination.imageUrl,
                        contentDescription = "${destination.name}, ${destination.country}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, ComicInk.copy(alpha = 0.6f)),
                                    startY = 60f
                                )
                            )
                    )
                }

                // Info & 1-tap Plan CTA
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = destination.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = ComicInk
                        )
                        Text(
                            text = destination.country,
                            fontSize = 11.sp,
                            color = ComicMuted
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            destination.tags.take(2).forEach { tag ->
                                Surface(
                                    color = ComicPaper,
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.border(1.dp, ComicInk.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                ) {
                                    Text(
                                        text = tag,
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = onPlan,
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, ComicInk),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                    ) {
                        Text(
                            text = "PLAN ${destination.name.uppercase()}",
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact secondary trip row for subsequent manifests.
 */
@Composable
private fun CompactTripRow(
    trip: TripSummaryDto,
    onClick: () -> Unit
) {
    val heroImg = trip.heroImageUrl ?: getDestinationHeroFallback(trip.destination)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(1.5.dp, ComicInk, RoundedCornerShape(8.dp)),
        color = ComicPanel,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(1.dp, ComicInk, RoundedCornerShape(6.dp))
            ) {
                AsyncImage(
                    model = heroImg,
                    contentDescription = trip.destination,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trip.destination,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = ComicInk
                )
                Text(
                    text = "${trip.startDate} to ${trip.endDate}",
                    fontSize = 11.sp,
                    color = ComicMuted
                )
            }
            TrippinStatusBadge(status = if (trip.isLocked) "LOCKED" else trip.status)
        }
    }
}
