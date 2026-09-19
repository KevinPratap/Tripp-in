package com.trippin.feature.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.trippin.core.design.*
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.PlaceSearchResultDto
import kotlinx.coroutines.launch

/**
 * Place search over the real places API, which is backed by OpenStreetMap.
 *
 * Deliberately shows only what the API can prove: the venue name, its address and its
 * category. There are no ratings, no review counts and no stock photography, because
 * the API does not return them and inventing them would break the product's one
 * promise. Empty states say so instead of filling the screen with sample data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(onNavigateBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var results by remember { mutableStateOf<List<PlaceSearchResultDto>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var hasSearched by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories = listOf("All", "Food", "Attractions", "Nature", "Nightlife", "Shopping")

    // Category keywords are matched against the place types OpenStreetMap returns.
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

    fun runSearch() {
        val query = searchQuery.trim()
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
                errorMessage = e.message ?: "The places lookup failed. Check your connection and try again."
            } finally {
                isSearching = false
            }
        }
    }

    val visibleResults = results.filter { matchesCategory(it, selectedCategory) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore Places", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search a city, museum or restaurant") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { runSearch() }) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Search")
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Real venues from OpenStreetMap",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Names, addresses and categories come straight from the map data. " +
                        "No ratings and no stock photos, because we cannot verify them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when {
                isSearching -> item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ComicRed)
                    }
                }

                errorMessage != null -> item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                visibleResults.isNotEmpty() -> items(visibleResults) { place ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = place.name.ifBlank { "Unnamed place" },
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (place.formattedAddress.isNotBlank()) {
                                    Text(
                                        text = place.formattedAddress,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val typeLabel = place.types.firstOrNull()
                                if (!typeLabel.isNullOrBlank()) {
                                    Text(
                                        text = typeLabel.replace('_', ' ').uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ComicRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                hasSearched -> item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "No places matched that search for this category.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                else -> item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Text(
                            text = "Search for a place to see real venues, or open a trip to browse the ones already in it.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
