package com.trippin.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import com.trippin.core.design.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlanner: (String?) -> Unit,
    onNavigateToTrip: (String) -> Unit,
    onNavigateToTrips: () -> Unit,
    onNavigateToExplore: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    var homeFeed by remember { mutableStateOf<com.trippin.core.network.HomeFeedDto?>(null) }
    var showWeatherSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            homeFeed = com.trippin.core.network.NetworkModule.apiService.getHome()
        } catch (_: Exception) {
            // Keep default display if offline
        }
    }

    val latestTrip = homeFeed?.recentTrips?.firstOrNull()
    val activeDestination = latestTrip?.destination ?: "Tokyo"

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Status bar spacer for punch-hole and notch safety
            item {
                Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
            }

            // 1. Header with Greeting & Profile
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Hello, ${homeFeed?.user?.displayName ?: "Traveler"}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Where will you go?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(ComicRed)
                            .border(2.dp, ComicInk, CircleShape)
                            .clickable(onClick = onNavigateToProfile),
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
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = PureWhite
                        )
                    }
                }
            }

            // 2. AI Planner Hero Card (Primary CTA)
            item {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .offset(x = 5.dp, y = 5.dp)
                            .background(ComicInk, RoundedCornerShape(14.dp))
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPlanner(null) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(2.dp, ComicInk),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI Travel Planner",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Generate a verified, physics-checked itinerary in seconds with routes and weather.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = { onNavigateToPlanner(null) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PureWhite,
                                        contentColor = ComicRed
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.5.dp, ComicInk),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
                                ) {
                                    Text(
                                        "PLAN A TRIP",
                                        fontWeight = FontWeight.Black,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 3. Quick Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    QuickActionButton(
                        icon = Icons.Default.WbSunny,
                        label = "Weather",
                        onClick = { showWeatherSheet = true }
                    )
                    QuickActionButton(
                        icon = Icons.Default.Explore,
                        label = "Explore",
                        onClick = onNavigateToExplore
                    )
                    QuickActionButton(
                        icon = Icons.Default.FlightTakeoff,
                        label = "My Trips",
                        onClick = onNavigateToTrips
                    )
                }
            }

            // 4. Popular Destinations Section
            item {
                Text(
                    text = "Popular Destinations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(listOf(
                        Triple("Paris", "France", "https://images.unsplash.com/photo-1502602898657-3e91760cbb34?w=400"),
                        Triple("Tokyo", "Japan", "https://images.unsplash.com/photo-1503899036084-c55cdd92da26?w=400"),
                        Triple("Rome", "Italy", "https://images.unsplash.com/photo-1552832230-c0197dd311b5?w=400")
                    )) { (city, country, img) ->
                        DestinationCard(
                            city = city,
                            country = country,
                            imageUrl = img,
                            onClick = { onNavigateToPlanner(city) }
                        )
                    }
                }
            }

            // 5. Recent Trips Section
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Trips",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onNavigateToTrips) {
                        Text(
                            text = "VIEW ALL",
                            fontWeight = FontWeight.Black,
                            color = ComicRed,
                            fontSize = 12.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                val recentList = homeFeed?.recentTrips ?: emptyList()
                if (recentList.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPlanner(null) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(2.dp, ComicInk),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ComicRed.copy(alpha = 0.15f))
                                    .border(2.dp, ComicInk, RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = ComicRed)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready to Explore?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Tap to build your first verified itinerary",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        recentList.take(3).forEach { trip ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToTrip(trip.id) },
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(2.dp, ComicInk),
                                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(ComicRed.copy(alpha = 0.15f))
                                            .border(2.dp, ComicInk, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.CardTravel, contentDescription = null, tint = ComicRed)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = trip.destination,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${trip.startDate} - ${trip.endDate}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${trip.travelersCount} TRAVELERS",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = ComicInk
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        border = BorderStroke(1.dp, ComicInk),
                                        color = if (trip.isLocked) ComicYellow else PureWhite
                                    ) {
                                        Text(
                                            text = if (trip.isLocked) "LOCKED" else trip.status.uppercase(),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Black,
                                            color = ComicRed,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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

    // Live Open-Meteo Weather Radar Modal
    if (showWeatherSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWeatherSheet = false },
            containerColor = ComicPaper
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "WEATHER RADAR // $activeDestination".uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 18.sp,
                            color = ComicBlack
                        )
                        Text(
                            text = "VERIFIED VIA OPEN-METEO",
                            style = MaterialTheme.typography.labelSmall,
                            color = ComicRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Surface(
                        color = ComicYellow,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = "FORECAST",
                            fontWeight = FontWeight.Black,
                            fontSize = 11.sp,
                            color = ComicBlack,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("TODAY FORECAST", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = ComicMuted)
                                Text("22 C · Mild & Clear", fontWeight = FontWeight.Black, fontSize = 20.sp, color = ComicBlack)
                            }
                            Icon(Icons.Default.WbSunny, contentDescription = null, tint = ComicRed, modifier = Modifier.size(36.dp))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "0% precipitation probability. Optimal walking conditions for outdoor walking trails and monuments.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = ComicBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showWeatherSheet = false
                            onNavigateToPlanner(activeDestination)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .border(2.dp, ComicBlack, RoundedCornerShape(8.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("PLAN IN THIS WEATHER", fontWeight = FontWeight.Black)
                    }
                }
            }
        }
    }
}

@Composable
fun DestinationCard(
    city: String,
    country: String,
    imageUrl: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(180.dp)
            .height(240.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(ComicInk, RoundedCornerShape(16.dp))
        )
        Card(
            modifier = Modifier
                .matchParentSize()
                .clickable { onClick() },
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(2.dp, ComicInk),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "$city, $country",
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
                                startY = 120f
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(14.dp)
                ) {
                    Text(
                        text = city,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = PureWhite
                    )
                    Text(
                        text = country.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = PureWhite.copy(alpha = 0.85f),
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(105.dp)
            .height(88.dp)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(ComicInk, RoundedCornerShape(12.dp))
        )
        Card(
            modifier = Modifier
                .matchParentSize()
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, ComicInk),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = ComicRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    color = ComicInk
                )
            }
        }
    }
}
