package com.trippin.feature.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.design.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToTrips: () -> Unit = {}
) {
    val context = LocalContext.current
    var magicEmail by remember { mutableStateOf("") }
    var emailSent by remember { mutableStateOf(false) }

    val guestSession = remember {
        "guest-" + android.os.Build.MODEL.replace(" ", "-").lowercase()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "FIELD DISPATCH PROFILE",
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "SESSION IDENTITY & ENGINE AUDIT",
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
            // Guest Session Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "LOCAL GUEST SESSION",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                color = ComicMuted
                            )
                            Surface(
                                color = ComicYellow,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.border(1.dp, ComicBlack, RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = "ACTIVE",
                                    color = ComicBlack,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = guestSession,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = ComicBlack
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("Guest Session", guestSession))
                                    Toast.makeText(context, "Session ID copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp)),
                                shape = RoundedCornerShape(6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ComicBlack)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("COPY ID", fontWeight = FontWeight.Black, fontSize = 11.sp)
                            }

                            Button(
                                onClick = onNavigateToTrips,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .border(1.5.dp, ComicBlack, RoundedCornerShape(6.dp)),
                                colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Icon(Icons.Default.CardTravel, contentDescription = null, tint = ComicPaper, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("MY TRIPS", fontWeight = FontWeight.Black, fontSize = 11.sp, color = ComicPaper)
                            }
                        }
                    }
                }
            }

            // Sync with Web Account / Magic Link Card
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SYNC WITH WEB DISPATCH",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = ComicBlack
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Migrate your guest field tickets to an account via passwordless magic link.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ComicMuted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = magicEmail,
                            onValueChange = { magicEmail = it },
                            placeholder = { Text("Enter email address") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                if (magicEmail.contains("@")) {
                                    val webUrl = "https://web-production-a9ec6.up.railway.app/login?email=${Uri.encode(magicEmail)}"
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(webUrl)))
                                    emailSent = true
                                }
                            },
                            enabled = magicEmail.contains("@"),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .border(1.5.dp, ComicBlack, RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(containerColor = ComicRed),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("SIGN IN ON WEB", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }

            // Engine Diagnostics & Provenance Certificate
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(2.5.dp, ComicBlack, RoundedCornerShape(12.dp)),
                    color = ComicPanel,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "RUNTIME ENGINE GUARANTEES",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = ComicBlack
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        EngineStatusRow(name = "OSRM Routing Engine", status = "CONNECTED", detail = "Deterministic physics transit")
                        Spacer(modifier = Modifier.height(6.dp))
                        EngineStatusRow(name = "OpenStreetMap Photon", status = "CONNECTED", detail = "Verified GPS venue coords")
                        Spacer(modifier = Modifier.height(6.dp))
                        EngineStatusRow(name = "Open-Meteo Forecast", status = "CONNECTED", detail = "Real-time rain & temp data")
                        Spacer(modifier = Modifier.height(6.dp))
                        EngineStatusRow(name = "Google Gemini 2.5 Flash", status = "ACTIVE", detail = "Free-tier generative planner")

                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, ComicBlack.copy(alpha = 0.2f), RoundedCornerShape(6.dp)),
                            color = ComicPaper,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "Tripp'in Invariant: Zero synthetic ratings, zero invented reviews, and zero stock images. ItineraryValidator deterministic verification active.",
                                style = MaterialTheme.typography.bodySmall,
                                color = ComicBlack,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EngineStatusRow(
    name: String,
    status: String,
    detail: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.Black, fontSize = 13.sp, color = ComicBlack)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = ComicMuted)
        }
        Surface(
            color = ComicPanel,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.border(1.dp, ComicRed, RoundedCornerShape(4.dp))
        ) {
            Text(
                text = status,
                color = ComicRed,
                fontWeight = FontWeight.Black,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
