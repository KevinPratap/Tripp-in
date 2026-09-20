package com.trippin.feature.explore

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trippin.core.cache.SavedSpotsManager
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
    val clipboardManager = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current

    // Tabs: 0 -> Discover Places, 1 -> Saved Spots
    var selectedTab by remember { mutableIntStateOf(0) }

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
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
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

    val savedSpots = SavedSpotsManager.savedSpots
    val displayedSavedPlaces = savedSpots.filter { matchesCategory(it, selectedCategory) }
    val displayedSearchResults = results.filter { matchesCategory(it, selectedCategory) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (selectedTab == 0) "DISCOVER VENUES" else "SAVED SPOTS BUCKET",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = if (selectedTab == 0) "VERIFIED OPENSTREETMAP DATA" else "${savedSpots.size} BOOKMARKED PLACES",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(ComicPaper)
        ) {
            // Segmented Tab Switcher (Discover vs Saved Spots)
            Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                TrippinSegmentedTabs(
                    options = listOf("DISCOVER PLACES", "SAVED SPOTS (${savedSpots.size})"),
                    selectedIndex = selectedTab,
                    onOptionSelected = { index ->
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTab = index
                    }
                )
            }

            if (selectedTab == 0) {
                // DISCOVER TAB CONTENT
                PullToRefreshBox(
                    isRefreshing = isSearching,
                    onRefresh = { if (searchQuery.isNotBlank()) executeSearch(searchQuery) },
                    modifier = Modifier.fillMaxSize()
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Search Bar
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(2.dp, ComicInk, RoundedCornerShape(8.dp)),
                                color = ComicPanel,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        placeholder = { Text("Search city, museum or restaurant...", fontSize = 13.sp) },
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = Color.Transparent,
                                            unfocusedContainerColor = Color.Transparent,
                                            disabledContainerColor = Color.Transparent,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { executeSearch(searchQuery) },
                                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.dp, ComicInk),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Default.Search, contentDescription = "Search", tint = ComicPaper, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }

                        // Curated Query Pills
                        item {
                            Column {
                                Text(
                                    text = "POPULAR DISPATCH QUERIES",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    color = ComicMuted,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(curatedQueries) { query ->
                                        Surface(
                                            onClick = {
                                                searchQuery = query
                                                executeSearch(query)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = ComicPanel,
                                            modifier = Modifier.border(1.5.dp, ComicInk, RoundedCornerShape(6.dp))
                                        ) {
                                            Text(
                                                text = query,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = ComicInk,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Category Filter Pills
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(categories) { cat ->
                                    val isSelected = selectedCategory == cat
                                    Surface(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            selectedCategory = cat
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isSelected) ComicInk else ComicPanel,
                                        modifier = Modifier.border(1.5.dp, ComicInk, RoundedCornerShape(6.dp))
                                    ) {
                                        Text(
                                            text = cat,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) ComicPaper else ComicInk,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Error Banner
                        if (errorMessage != null) {
                            item {
                                Surface(
                                    color = ComicRed.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, ComicRed, RoundedCornerShape(8.dp))
                                ) {
                                    Text(
                                        text = errorMessage ?: "",
                                        color = ComicRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                        }

                        // Results Header
                        if (hasSearched) {
                            item {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "FOUND ${displayedSearchResults.size} VENUES",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = ComicMuted,
                                        letterSpacing = 1.sp
                                    )
                                    Surface(
                                        color = ComicYellow,
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.border(1.dp, ComicInk, RoundedCornerShape(4.dp))
                                    ) {
                                        Text(
                                            text = "OSM PHOTON",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            color = ComicInk,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Search Results List
                        items(displayedSearchResults) { place ->
                            val isBookmarked = SavedSpotsManager.isSaved(place.id)
                            VenueCard(
                                place = place,
                                isBookmarked = isBookmarked,
                                onToggleBookmark = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    SavedSpotsManager.toggleSave(place)
                                },
                                onCopyAddress = {
                                    clipboardManager.setText(AnnotatedString("${place.name}, ${place.formattedAddress}"))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                },
                                onOpenMap = {
                                    val query = Uri.encode("${place.name}, ${place.formattedAddress}")
                                    val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    context.startActivity(mapIntent)
                                },
                                onPlanTrip = {
                                    onPlanCity(place.name)
                                }
                            )
                        }

                        // Empty State if search executed and no results
                        if (hasSearched && displayedSearchResults.isEmpty()) {
                            item {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(2.dp, ComicInk, RoundedCornerShape(10.dp)),
                                    color = ComicPanel,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.SearchOff, contentDescription = null, tint = ComicMuted, modifier = Modifier.size(36.dp))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("NO VENUES MATCHED", fontWeight = FontWeight.Black, fontSize = 14.sp)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            "Try searching for another neighborhood or category.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ComicMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // SAVED SPOTS TAB CONTENT
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Category filter for saved spots
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(categories) { cat ->
                                val isSelected = selectedCategory == cat
                                Surface(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedCategory = cat
                                    },
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) ComicInk else ComicPanel,
                                    modifier = Modifier.border(1.5.dp, ComicInk, RoundedCornerShape(6.dp))
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) ComicPaper else ComicInk,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (displayedSavedPlaces.isEmpty()) {
                        item {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(2.dp, ComicInk, RoundedCornerShape(10.dp)),
                                color = ComicPanel,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .background(ComicRed.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                            .border(1.5.dp, ComicRed, RoundedCornerShape(12.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = ComicRed, modifier = Modifier.size(28.dp))
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "YOUR BUCKET IS EMPTY",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 15.sp,
                                        color = ComicInk
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Tap DISCOVER PLACES to find cafes, museums, and landmarks. Bookmark any spot to collect it here.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = ComicMuted,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = { selectedTab = 0 },
                                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(1.5.dp, ComicInk)
                                    ) {
                                        Text("SEARCH VENUES NOW", fontWeight = FontWeight.Black, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        items(displayedSavedPlaces, key = { it.id }) { place ->
                            VenueCard(
                                place = place,
                                isBookmarked = true,
                                onToggleBookmark = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    SavedSpotsManager.toggleSave(place)
                                },
                                onCopyAddress = {
                                    clipboardManager.setText(AnnotatedString("${place.name}, ${place.formattedAddress}"))
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                },
                                onOpenMap = {
                                    val query = Uri.encode("${place.name}, ${place.formattedAddress}")
                                    val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                    context.startActivity(mapIntent)
                                },
                                onPlanTrip = {
                                    onPlanCity(place.name)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Editorial Venue Card with high contrast and fast actions.
 */
@Composable
private fun VenueCard(
    place: PlaceSearchResultDto,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onCopyAddress: () -> Unit,
    onOpenMap: () -> Unit,
    onPlanTrip: () -> Unit
) {
    val categoryLabel = place.types.firstOrNull()?.replace("_", " ")?.uppercase() ?: "VENUE"

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(x = 3.dp, y = 3.dp)
                .background(ComicInk, RoundedCornerShape(10.dp))
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(2.dp, ComicInk),
            colors = CardDefaults.cardColors(containerColor = ComicPanel),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Header: Name & Category Pill & Bookmark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = place.name,
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = ComicInk
                        )
                        if (place.formattedAddress.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = place.formattedAddress,
                                style = MaterialTheme.typography.bodySmall,
                                color = ComicMuted
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = ComicRed,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = categoryLabel,
                                color = ComicPaper,
                                fontWeight = FontWeight.Black,
                                fontSize = 9.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (isBookmarked) ComicRed else ComicInk
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions: 1-Tap Copy, Map, Plan
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyAddress,
                        modifier = Modifier
                            .height(36.dp)
                            .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ComicInk)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("COPY", fontWeight = FontWeight.Black, fontSize = 10.sp, color = ComicInk)
                    }

                    OutlinedButton(
                        onClick = onOpenMap,
                        modifier = Modifier
                            .height(36.dp)
                            .border(1.5.dp, ComicInk, RoundedCornerShape(6.dp)),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp)
                    ) {
                        Icon(Icons.Default.Map, contentDescription = null, modifier = Modifier.size(14.dp), tint = ComicInk)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("MAP", fontWeight = FontWeight.Black, fontSize = 10.sp, color = ComicInk)
                    }

                    Button(
                        onClick = onPlanTrip,
                        colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.5.dp, ComicInk),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                    ) {
                        Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PLAN HERE", fontWeight = FontWeight.Black, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
