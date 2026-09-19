package com.trippin.feature.explore

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.*
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.PlaceSearchResultDto
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onNavigateBack: () -> Unit,
    onPlanCity: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var results by remember { mutableStateOf<List<PlaceSearchResultDto>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("All", "Food", "Attractions", "Nature", "Nightlife", "Shopping")
    val curatedQueries = listOf("Tokyo Ramen", "Paris Art", "Rome Colosseum", "Kyoto Temples", "Lisbon Cafes", "London Pubs")

    val categoryKeywords = mapOf(
        "Food" to listOf("restaurant", "cafe", "bar", "food", "bakery", "pub"),
        "Attractions" to listOf("museum", "attraction", "monument", "gallery", "theatre", "temple", "church"),
        "Nature" to listOf("park", "garden", "nature", "beach", "forest", "viewpoint"),
        "Nightlife" to listOf("nightclub", "bar", "pub", "night"),
        "Shopping" to listOf("shop", "market", "mall", "store", "boutique")
    )

    fun matchesCategory(place: PlaceSearchResultDto, category: String): Boolean {
        if (category == "All") return true
        val keywords = categoryKeywords[category] ?: return true
        val haystack = (place.types + place.formattedAddress).joinToString(" ").lowercase()
        return keywords.any { haystack.contains(it) }
    }

    fun executeSearch(queryToRun: String) {
        val query = queryToRun.trim()
        if (query.length < 2) {
            errorMessage = "Type at least two characters to search."
            return
        }
        scope.launch {
            isSearching = true
            errorMessage = null
            try {
                results = NetworkModule.apiService.searchPlaces(query)
                hasSearched = true
            } catch (e: Exception) {
                results = emptyList()
                hasSearched = true
                errorMessage = e.message ?: "The places lookup failed. Check your connection."
            } finally {
                isSearching = false
            }
        }
    }

    val visibleResults = results.filter { matchesCategory(it, selectedCategory) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "EXPLORE VENUES",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "VERIFIED OPENSTREETMAP DATA",
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
            // Search Input Box
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.5.dp, ComicBlack, RoundedCornerShape(10.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Search city, museum or restaurant...") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
                                unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent
                            )
                        )
                        Button(
                            onClick = { executeSearch(searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .border(1.5.dp, ComicBlack, RoundedCornerShape(8.dp))
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = ComicPaper)
                        }
                    }
                }
            }

            // Curated Quick-Search Pills
            item {
                Column {
                    Text(
                        text = "POPULAR DISPATCH QUERIES",
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = ComicMuted,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(curatedQueries) { query ->
                            Surface(
                                onClick = {
                                    searchQuery = query
                                    executeSearch(query)
                                },
                                shape = RoundedCornerShape(6.dp),
                                color = ComicPanel,
                                modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp))
                            ) {
                                Text(
                                    text = query,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ComicBlack,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Category Filter Pills
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            onClick = { selectedCategory = cat },
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSelected) ComicBlack else ComicPanel,
                            modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = cat,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) ComicPaper else ComicBlack,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            when {
                isSearching -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ComicRed)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Querying OpenStreetMap Photon...", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                errorMessage != null -> item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, ComicRed, RoundedCornerShape(10.dp)),
                        color = ComicPanel,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ComicRed,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                visibleResults.isNotEmpty() -> items(visibleResults) { place ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.5.dp, ComicBlack, RoundedCornerShape(10.dp)),
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
                                    text = place.name.ifBlank { "Unnamed Venue" },
                                    fontWeight = FontWeight.Black,
                                    fontSize = 17.sp,
                                    color = ComicBlack,
                                    modifier = Modifier.weight(1f)
                                )
                                val typeLabel = place.types.firstOrNull()
                                if (!typeLabel.isNullOrBlank()) {
                                    Surface(
                                        color = ComicRed,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.border(1.dp, ComicBlack, RoundedCornerShape(4.dp))
                                    ) {
                                        Text(
                                            text = typeLabel.replace('_', ' ').uppercase(),
                                            color = ComicPaper,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            if (place.formattedAddress.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = place.formattedAddress,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ComicMuted
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        val query = Uri.encode("${place.name}, ${place.formattedAddress}")
                                        val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, gmmIntentUri))
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp)),
                                    shape = RoundedCornerShape(6.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ComicBlack)
                                ) {
                                    Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("MAP", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                }

                                Button(
                                    onClick = { onPlanCity(place.name) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp)),
                                    colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Icon(Icons.Default.Bolt, contentDescription = null, tint = ComicPaper, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PLAN HERE", fontWeight = FontWeight.Black, fontSize = 11.sp, color = ComicPaper)
                                }
                            }
                        }
                    }
                }

                hasSearched -> item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                        color = ComicPanel,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "No places matched this category query.",
                                fontWeight = FontWeight.Bold,
                                color = ComicBlack
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Try selecting \"All\" category or checking the spelling of the location.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ComicMuted
                            )
                        }
                    }
                }

                else -> item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(2.dp, ComicBlack, RoundedCornerShape(10.dp)),
                        color = ComicPanel,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Text(
                                "LIVE OSM LOOKUP",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = ComicRed
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Search any city or venue to explore real physical locations, or tap one of the popular queries above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ComicBlack
                            )
                        }
                    }
                }
            }
        }
    }
}
