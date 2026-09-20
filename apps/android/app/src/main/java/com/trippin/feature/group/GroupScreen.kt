package com.trippin.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.ArriveOnEnter
import com.trippin.core.design.ComicInk
import com.trippin.core.design.ComicMuted
import com.trippin.core.design.ComicPaper
import com.trippin.core.design.ComicRed
import com.trippin.core.design.TrippinCard
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.TripDetailsDto

/**
 * The people you are going with.
 *
 * This screen exists because it is the one thing the product is actually about and it had nowhere to
 * live. It shows what is known today and says plainly what is not: a trip currently carries a count
 * of travellers and nothing else, so there is nothing honest to render per person yet beyond that
 * count. When per-person budgets and interests exist, they appear in the section below in place of
 * the empty state, and nothing else on this screen has to change.
 */
@Composable
fun GroupScreen(tripId: String) {
    var details by remember { mutableStateOf(TripCacheManager.getTrip(tripId)) }
    var isLoading by remember { mutableStateOf(details == null) }

    LaunchedEffect(tripId) {
        try {
            val fresh = NetworkModule.apiService.getTripDetails(tripId)
            TripCacheManager.putTrip(tripId, fresh)
            details = fresh
        } catch (_: Exception) {
            // Offline, or the trip is gone. Whatever was cached stays on screen.
        }
        isLoading = false
    }

    val trip = details?.trip

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ComicPaper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        ArriveOnEnter {
            Column {
                Text(
                    text = "Group",
                    fontWeight = FontWeight.Black,
                    fontSize = 26.sp,
                    color = ComicInk
                )
                Text(
                    text = when {
                        trip == null && isLoading -> "Loading this trip"
                        trip == null -> "This trip is not available offline yet"
                        else -> trip.destination
                    },
                    fontSize = 14.sp,
                    color = ComicInk.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (trip != null) {
                    Text(
                        text = "${trip.startDate} to ${trip.endDate}",
                        fontSize = 13.sp,
                        color = ComicMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        ArriveOnEnter(delayMillis = 60) {
            TrippinCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Travellers",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = ComicInk
                    )
                    Text(
                        text = when (val count = trip?.travelersCount) {
                            null -> "Not known offline"
                            1 -> "1 person on this trip"
                            else -> "$count people on this trip"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = ComicRed,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Text(
                        text = "Nobody has set their own budget or interests yet. Each person sets these for themselves.",
                        fontSize = 12.sp,
                        color = ComicMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        ArriveOnEnter(delayMillis = 120) {
            TrippinCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "What each person wants",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = ComicInk
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Budget", fontSize = 12.sp, color = ComicMuted)
                        Text(text = "Not set for anyone yet", fontSize = 12.sp, color = ComicInk)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Interests", fontSize = 12.sp, color = ComicMuted)
                        Text(text = "Not set for anyone yet", fontSize = 12.sp, color = ComicInk)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Pace", fontSize = 12.sp, color = ComicMuted)
                        Text(text = "Not set for anyone yet", fontSize = 12.sp, color = ComicInk)
                    }
                }
            }
        }

        if (trip != null) {
            Spacer(modifier = Modifier.height(12.dp))
            ArriveOnEnter(delayMillis = 180) {
                TrippinCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "This trip",
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            color = ComicInk
                        )
                        Text(
                            text = buildString {
                                append("Plan version ${trip.currentVersion}")
                                append(" · ")
                                append(if (trip.isLocked) "Locked" else "Not locked")
                                append(" · ")
                                append(trip.status.lowercase().replaceFirstChar { it.uppercase() })
                            },
                            fontSize = 12.sp,
                            color = ComicMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
