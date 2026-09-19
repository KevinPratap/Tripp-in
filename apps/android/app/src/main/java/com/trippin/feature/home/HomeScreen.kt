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
    onNavigateToPlanner: () -> Unit,
    onNavigateToTrip: (String) -> Unit,
    onNavigateToExplore: () -> Unit,
    onNavigateToProfile: () -> Unit = {}
) {
    var homeFeed by remember { mutableStateOf<com.trippin.core.network.HomeFeedDto?>(null) }

    LaunchedEffect(Unit) {
        try {
            homeFeed = com.trippin.core.network.NetworkModule.apiService.getHome()
        } catch (_: Exception) {
            // Keep default display if offline
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home") },
                    selected = true,
                    onClick = {}
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CardTravel, contentDescription = "Trips") },
                    label = { Text("Trips") },
                    selected = false,
                    onClick = {
                        val firstTripId = homeFeed?.recentTrips?.firstOrNull()?.id
                        if (firstTripId != null) {
                            onNavigateToTrip(firstTripId)
                        } else {
                            onNavigateToPlanner()
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Explore, contentDescription = "Explore") },
                    label = { Text("Explore") },
                    selected = false,
                    onClick = onNavigateToExplore
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                    label = { Text("Profile") },
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
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Where will you go?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
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
                            .clickable { onNavigateToPlanner() },
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
                                    fontWeight = FontWeight.Bold,
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
                                    onClick = onNavigateToPlanner,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(2.dp, ComicInk),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = PureWhite,
                                        contentColor = ComicRed
                                    )
                                ) {
                                    Text(
                                        "PLAN A TRIP",
                                        fontWeight = FontWeight.Bold,
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
                        onClick = {}
                    )
                    QuickActionButton(
                        icon = Icons.Default.Explore,
                        label = "Explore",
                        onClick = onNavigateToExplore
                    )
                    QuickActionButton(
                        icon = Icons.Default.FlightTakeoff,
                        label = "My Trips",
                        onClick = {}
                    )
                }
            }

            // 4. Popular Destinations Section
            item {
                Text(
                    text = "Popular Destinations",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
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
                        DestinationCard(city = city, country = country, imageUrl = img, onClick = onNavigateToPlanner)
                    }
                }
            }

            // 5. Recent Trips Section
            item {
                Text(
                    text = "Recent Trips",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                val recentList = homeFeed?.recentTrips ?: emptyList()
                if (recentList.isEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToPlanner() },
                        shape = RoundedCornerShape(16.dp),
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
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ComicRed.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AddLocationAlt, contentDescription = null, tint = ComicRed)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Ready to Explore?",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
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
                        recentList.forEach { trip ->
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .offset(x = 3.dp, y = 3.dp)
                                        .background(ComicInk, RoundedCornerShape(12.dp))
                                )
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
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${trip.startDate} - ${trip.endDate} · ${trip.travelersCount} Travelers",
                                                style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    AssistChip(
                                        onClick = { onNavigateToTrip(trip.id) },
                                        label = { Text(trip.status, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                        colors = AssistChipDefaults.assistChipColors(
                                            labelColor = ComicRed
                                        )
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

@Composable
private fun QuickActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Box(modifier = Modifier.width(100.dp)) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(ComicInk, RoundedCornerShape(12.dp))
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() },
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(2.dp, ComicInk),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DestinationCard(
    city: String,
    country: String,
    imageUrl: String,
    onClick: () -> Unit
) {
    // Ink border plus a hard offset shadow, and a gradient scrim so the label stays readable over
    // a bright photograph instead of sitting on a flat grey wash.
    Box(
        modifier = Modifier
            .width(164.dp)
            .height(204.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 4.dp, y = 4.dp)
                .background(ComicInk, RoundedCornerShape(12.dp))
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(ComicPanel)
                .border(2.dp, ComicInk, RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = city,
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
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.8f)
                            )
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
                    fontWeight = FontWeight.Bold,
                    color = PureWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = country.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
