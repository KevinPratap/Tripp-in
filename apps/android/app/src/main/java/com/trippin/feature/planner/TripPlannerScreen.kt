package com.trippin.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.trippin.core.design.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripPlannerScreen(
    initialDestination: String = "",
    onNavigateBack: () -> Unit,
    onTripCreated: (String) -> Unit
) {
    var destination by remember {
        mutableStateOf(if (initialDestination.isNotBlank()) initialDestination else "Tokyo")
    }
    var travelersCount by remember { mutableIntStateOf(2) }
    var budget by remember { mutableStateOf("1500") }
    var selectedPace by remember { mutableStateOf("MODERATE") }
    val availableInterests = listOf("Art", "Food", "History", "Architecture", "Nightlife", "Nature", "Shopping")
    val selectedInterests = remember { mutableStateListOf("Art", "Food", "History") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Plan a trip",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Checked against opening hours and travel times",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 12.sp,
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
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Destination input
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Where to",
                            style = TrippinType.Label,
                            color = ComicBlack
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = destination,
                            onValueChange = { destination = it },
                            placeholder = { Text("e.g. Lisbon, Tokyo, Rome") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true
                        )
                    }
                }
            }

            // Dates & Travelers & Budget
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Travellers and budget",
                            style = TrippinType.Label,
                            color = ComicBlack
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = "$travelersCount ${if (travelersCount == 1) "traveller" else "travellers"}",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Travellers") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                trailingIcon = {
                                    Row {
                                        IconButton(
                                            onClick = { if (travelersCount > 1) travelersCount-- },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Remove, contentDescription = "Decrease")
                                        }
                                        IconButton(
                                            onClick = { travelersCount++ },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = "Increase")
                                        }
                                    }
                                }
                            )
                            OutlinedTextField(
                                value = budget,
                                onValueChange = { budget = it },
                                label = { Text("Budget (USD)") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Travel Pace selector
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pace",
                            style = TrippinType.Label,
                            color = ComicBlack
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("RELAXED", "MODERATE", "FAST").forEach { pace ->
                                val isSelected = selectedPace == pace
                                Surface(
                                    onClick = { selectedPace = pace },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) ComicBlack else ComicPaper,
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp))
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = pace,
                                            style = TrippinType.Caption,
                                            color = if (isSelected) ComicPaper else ComicBlack
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Interests chips
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Your interests",
                            style = TrippinType.Label,
                            color = ComicBlack
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            availableInterests.forEach { interest ->
                                val isSelected = selectedInterests.contains(interest)
                                Surface(
                                    onClick = {
                                        if (isSelected) selectedInterests.remove(interest)
                                        else selectedInterests.add(interest)
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) ComicRed else ComicPaper,
                                    modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp))
                                ) {
                                    Text(
                                        text = interest.uppercase(),
                                        style = TrippinType.Caption,
                                        color = if (isSelected) ComicPaper else ComicBlack,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Generate CTA
            item {
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

                Button(
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
                                submitError = "Could not create the trip: ${e.message ?: "the app could not reach the server"}"
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting && destination.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .border(2.5.dp, ComicBlack, RoundedCornerShape(10.dp)),
                    colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = ComicPaper,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Building your plan...",
                            style = TrippinType.Label,
                            color = ComicPaper
                        )
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null, tint = ComicPaper)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Build my plan",
                            style = TrippinType.Label,
                            letterSpacing = 1.sp,
                            color = ComicPaper
                        )
                    }
                }
            }
        }
    }
}
