package com.trippin.feature.map

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.trippin.core.design.*
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripDetailsDto

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    tripId: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var tripDetails by remember { mutableStateOf<TripDetailsDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(tripId) {
        try {
            tripDetails = NetworkModule.apiService.getTripDetails(tripId)
        } catch (_: Exception) {}
        isLoading = false
    }

    val destination = tripDetails?.trip?.destination ?: "Destination"
    val activities = tripDetails?.itinerary?.days?.firstOrNull()?.activities ?: emptyList()
    val firstAct = activities.firstOrNull()
    val secondAct = activities.getOrNull(1) ?: firstAct

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = destination.uppercase(),
                            style = TrippinType.Heading
                        )
                        Text(
                            text = "OpenStreetMap route",
                            style = TrippinType.Caption,
                            color = InkMuted
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val webUrl = "https://web-production-a9ec6.up.railway.app/trip/$tripId"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webUrl))
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "Open in browser", tint = AccentCrimson)
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
            // Real OpenStreetMap Leaflet Map WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        webViewClient = WebViewClient()
                        loadUrl("https://web-production-a9ec6.up.railway.app/trip/$tripId")
                    }
                }
            )

            // Bottom Floating Activity Preview Card
            if (firstAct != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
                    color = ComicPaper,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${firstAct.title} ${if (secondAct != null && secondAct != firstAct) "→ " + secondAct.title else ""}",
                                style = TrippinType.Heading,
                                color = ComicBlack
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Day 1, ${firstAct.startTime} to ${firstAct.endTime}",
                                style = TrippinType.Caption,
                                color = InkMuted
                            )
                        }
                        Button(
                            colors = ButtonDefaults.buttonColors(containerColor = AccentCrimson),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.5.dp, ComicBlack, RoundedCornerShape(8.dp)),
                            onClick = {
                                val query = Uri.encode("${firstAct.title}, $destination")
                                val gmmIntentUri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$query")
                                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                                context.startActivity(mapIntent)
                            }
                        ) {
                            Icon(Icons.Default.Directions, contentDescription = null, tint = ComicPaper)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Take me there", style = TrippinType.Label, color = ComicPaper)
                        }
                    }
                }
            }
        }
    }
}
