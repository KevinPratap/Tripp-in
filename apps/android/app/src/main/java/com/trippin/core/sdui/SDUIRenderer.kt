package com.trippin.core.sdui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.*

@Composable
fun SDUIRenderer(
    sections: List<SDUISectionDto>,
    onAction: (SDUIActionDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(sections.sortedBy { it.orderIndex }, key = { it.id }) { section ->
            when (section.type) {
                "HEADER_GREETING" -> RenderHeaderGreeting(section)
                "SEARCH_BAR" -> RenderSearchBar(section, onAction)
                "HERO_BANNER" -> RenderHeroBanner(section, onAction)
                "QUICK_ACTIONS" -> RenderQuickActions(section, onAction)
                "WEATHER_BANNER" -> RenderWeatherBanner(section)
                "CALL_TO_ACTION" -> RenderCTA(section, onAction)
                else -> {
                    // Fallback generic card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            section.title?.let { Text(it, fontWeight = FontWeight.Bold) }
                            section.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RenderHeaderGreeting(section: SDUISectionDto) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = section.subtitle ?: "Hi, Traveler 👋",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = section.title ?: "Where will you go?",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "AR",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun RenderSearchBar(section: SDUISectionDto, onAction: (SDUIActionDto) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { section.action?.let { onAction(it) } },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = section.subtitle ?: "Search destinations, landmarks...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RenderHeroBanner(section: SDUISectionDto, onAction: (SDUIActionDto) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { section.action?.let { onAction(it) } },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "✨ AI POWERED",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PureWhite.copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = section.title ?: "Generate Your Custom Itinerary",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = PureWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = section.subtitle ?: "Physics-checked schedule with opening hours and routes in seconds.",
                style = MaterialTheme.typography.bodyMedium,
                color = PureWhite.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = { section.action?.let { onAction(it) } },
                colors = ButtonDefaults.buttonColors(containerColor = PureWhite, contentColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Plan a Trip", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun RenderQuickActions(section: SDUISectionDto, onAction: (SDUIActionDto) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        listOf(
            Triple("Weather", Icons.Default.WbSunny, "weather"),
            Triple("Explore", Icons.Default.Explore, "explore"),
            Triple("My Trips", Icons.Default.CardTravel, "trips")
        ).forEach { (label, icon, route) ->
            Card(
                modifier = Modifier
                    .width(100.dp)
                    .clickable { onAction(SDUIActionDto("NAVIGATE", route)) },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun RenderWeatherBanner(section: SDUISectionDto) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.WbSunny, contentDescription = null, tint = SunsetCoral)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(section.title ?: "19°C · Mild & Sunny", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(section.subtitle ?: "Great for walking and outdoor monuments", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun RenderCTA(section: SDUISectionDto, onAction: (SDUIActionDto) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { section.action?.let { onAction(it) } },
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = EmeraldTeal.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.AutoFixHigh, contentDescription = null, tint = EmeraldTeal)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(section.title ?: "Modify with AI ✨", fontWeight = FontWeight.Bold, color = EmeraldTeal)
                Text(section.subtitle ?: "Tap to adjust schedule or change activities", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
