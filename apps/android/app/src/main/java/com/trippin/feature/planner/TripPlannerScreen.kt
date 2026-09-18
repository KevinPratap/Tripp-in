package com.trippin.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.trippin.core.design.TrippinButton

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripPlannerScreen(
    onNavigateBack: () -> Unit,
    onTripCreated: (String) -> Unit
) {
    var destination by remember { mutableStateOf("Paris") }
    var travelersCount by remember { mutableIntStateOf(2) }
    var budget by remember { mutableStateOf("1500") }
    var selectedPace by remember { mutableStateOf("MODERATE") }
    val availableInterests = listOf("Art", "Food", "History", "Architecture", "Nightlife", "Nature", "Shopping")
    val selectedInterests = remember { mutableStateListOf("Art", "Food", "History") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plan New Trip", fontWeight = FontWeight.Bold) },
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
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Destination input
            item {
                Text(
                    text = "Where are you heading?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = destination,
                    onValueChange = { destination = it },
                    label = { Text("Destination city") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )
            }

            // Dates & Travelers
            item {
                Text(
                    text = "Travelers & Budget",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = "$travelersCount travelers",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Travelers") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = {
                            Row {
                                TextButton(onClick = { if (travelersCount > 1) travelersCount-- }) { Text("-") }
                                TextButton(onClick = { travelersCount++ }) { Text("+") }
                            }
                        }
                    )
                    OutlinedTextField(
                        value = budget,
                        onValueChange = { budget = it },
                        label = { Text("Budget (USD)") },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // Pace selector
            item {
                Text(
                    text = "Travel Pace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("RELAXED", "MODERATE", "FAST").forEach { pace ->
                        FilterChip(
                            selected = selectedPace == pace,
                            onClick = { selectedPace = pace },
                            label = { Text(pace) }
                        )
                    }
                }
            }

            // Interests chips
            item {
                Text(
                    text = "Interests & Themes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    availableInterests.forEach { interest ->
                        val isSelected = selectedInterests.contains(interest)
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) selectedInterests.remove(interest)
                                else selectedInterests.add(interest)
                            },
                            label = { Text(interest) }
                        )
                    }
                }
            }

            // Generate CTA
            item {
                Spacer(modifier = Modifier.height(16.dp))
                var isSubmitting by remember { mutableStateOf(false) }
                var submitError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                if (submitError != null) {
                    Text(
                        text = submitError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                TrippinButton(
                    text = if (isSubmitting) "DEPLOYING ENGINE..." else "BUILD FIELD SCHEDULE",
                    enabled = !isSubmitting && destination.isNotBlank(),
                    onClick = {
                        isSubmitting = true
                        submitError = null
                        scope.launch {
                            try {
                                val today = java.time.LocalDate.now()
                                val start = today.plusDays(7).toString()
                                val end = today.plusDays(10).toString()
                                val req = com.trippin.core.network.CreateTripDto(
                                    destination = destination.trim(),
                                    startDate = start,
                                    endDate = end,
                                    travelersCount = travelersCount,
                                    budgetTotal = budget.toDoubleOrNull() ?: 1500.0,
                                    pace = selectedPace,
                                    interests = selectedInterests.toList()
                                )
                                val res = com.trippin.core.network.NetworkModule.apiService.createTrip(req)
                                com.trippin.core.network.NetworkModule.apiService.triggerGeneration(res.tripId)
                                onTripCreated(res.tripId)
                            } catch (e: Exception) {
                                submitError = "Connection error: ${e.message ?: "Failed to deploy"}"
                                isSubmitting = false
                            }
                        }
                    }
                )
            }
        }
    }
}
