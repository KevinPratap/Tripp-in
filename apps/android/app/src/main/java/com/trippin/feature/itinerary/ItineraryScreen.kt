package com.trippin.feature.itinerary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.*

data class DisplayActivity(
    val time: String,
    val title: String,
    val subtitle: String,
    val duration: String,
    val transitFromPrev: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItineraryScreen(
    tripId: String,
    onNavigateBack: () -> Unit,
    onOpenMap: (String) -> Unit
) {
    var selectedDayIndex by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editInstruction by remember { mutableStateOf("") }
    var isModifying by remember { mutableStateOf(false) }

    val days = listOf("Day 1 (10 Apr)", "Day 2 (11 Apr)", "Day 3 (12 Apr)")

    val activitiesDay1 = listOf(
        DisplayActivity("10:00 - 12:30", "Louvre Museum", "World landmark art museum", "2h 30m"),
        DisplayActivity("13:00 - 14:15", "Café de Flore", "Authentic Parisian lunch & coffee", "1h 15m", transitFromPrev = "30m via Metro"),
        DisplayActivity("14:45 - 17:00", "Musée d'Orsay", "Impressionist masterworks", "2h 15m", transitFromPrev = "25m via Walking"),
        DisplayActivity("17:45 - 19:30", "Eiffel Tower", "Sunset views from Champ de Mars", "1h 45m", transitFromPrev = "40m via Transit")
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Paris Cultural Escape", fontWeight = FontWeight.Bold)
                        Text("v1 · Verified Schedule", style = MaterialTheme.typography.labelSmall, color = EmeraldTeal)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenMap(tripId) }) {
                        Icon(Icons.Default.Map, contentDescription = "Map")
                    }
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.AutoFixHigh, contentDescription = "AI Edit", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showEditDialog = true },
                icon = { Icon(Icons.Default.AutoFixHigh, contentDescription = null) },
                text = { Text("AI Edit") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Day selector tabs
            PrimaryTabRow(selectedTabIndex = selectedDayIndex) {
                days.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedDayIndex == index,
                        onClick = { selectedDayIndex = index },
                        text = { Text(title, fontWeight = if (selectedDayIndex == index) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }

            // Weather header banner for selected day
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
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
                        Text("19°C · Sunny & Mild", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text("Optimal weather for walking between museums", style = MaterialTheme.typography.bodyMedium, fontSize = 12.sp)
                    }
                }
            }

            // Timeline list of activities
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(activitiesDay1) { index, act ->
                    Column {
                        // Transit buffer chip if present
                        if (act.transitFromPrev != null) {
                            Row(
                                modifier = Modifier
                                    .padding(start = 24.dp, bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(CategoryTransit)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = act.transitFromPrev,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CategoryTransit,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Activity card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = act.time,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = act.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = act.subtitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                AssistChip(
                                    onClick = {},
                                    label = { Text(act.duration) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // AI Conversational Edit Modal (Phase 15, TRP-042)
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Modify with AI ✨") },
                text = {
                    Column {
                        Text(
                            "Tell Trippin' AI what to adjust (e.g. \"Make Day 2 less busy\", \"Replace museum with outdoor walk\"):",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editInstruction,
                            onValueChange = { editInstruction = it },
                            placeholder = { Text("e.g. Move Musée d'Orsay to Day 3") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isModifying = true
                            showEditDialog = false
                        }
                    ) {
                        Text("Apply Changes")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
