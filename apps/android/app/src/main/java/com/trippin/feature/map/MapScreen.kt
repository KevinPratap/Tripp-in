package com.trippin.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trippin.core.design.EmeraldTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    tripId: String,
    onNavigateBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Route & Places Map", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            // Simulated Map Canvas Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFE8ECEF)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Interactive Map View", fontWeight = FontWeight.SemiBold, color = Color.Gray)
                    Text("Pins & Polyline connecting Day 1 activities", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }

            // Bottom Floating Activity Preview Card (Section 22)
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Louvre Museum → Café de Flore",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "2.1 km · 15 min via Metro Line 1",
                            style = MaterialTheme.typography.bodyMedium,
                            color = EmeraldTeal,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Button(
                        onClick = {
                            // Deep-links into Google Maps intent for turn-by-turn navigation
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Directions, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Navigate")
                    }
                }
            }
        }
    }
}
