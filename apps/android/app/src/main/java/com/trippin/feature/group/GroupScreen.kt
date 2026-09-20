package com.trippin.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.ArriveOnEnter
import com.trippin.core.design.TrippinType
import com.trippin.core.design.ComicInk
import com.trippin.core.design.ComicMuted
import com.trippin.core.design.ComicPanel
import com.trippin.core.design.ComicPaper
import com.trippin.core.design.WarnAmber
import com.trippin.core.design.WarnAmberSurface
import com.trippin.core.design.TrippinCard
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.PerTravellerCostDto
import com.trippin.core.network.TravellerDto
import com.trippin.core.network.TripDetailsDto
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.roundToLong

/**
 * The people you are going with, and what each of them wants.
 *
 * This screen is the one the product is actually about, so it renders real data and nothing else.
 * Every row comes from the trip payload: the travellers the server holds, each one's own budget cap,
 * interests, dislikes and pace, and the server's per-person share of the plan. There is no sample
 * person, no placeholder money and no count that was not sent. Where a person has not set something,
 * the row says Not set. Where the trip has no cost estimate yet, the share row says so instead of
 * showing a number, because a number here would be invented.
 *
 * The conflicts are the whole point: a cap below that person's share, and a stop somebody listed as
 * a dislike. Both are shown with the real figures attached so the group can decide something.
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
    val travellers = trip?.travellers.orEmpty()
    val shares = trip?.perTravellerCost.orEmpty()
    val currency = currencyOf(details)
    val joined = travellers.size
    val plannedFor = trip?.travelersCount
    val costKnown = shares.isNotEmpty()

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
                    style = TrippinType.Title,
                    color = ComicInk
                )
                Text(
                    text = when {
                        trip == null && isLoading -> "Loading this trip"
                        trip == null -> "This trip is not available offline yet"
                        else -> trip.destination
                    },
                    style = TrippinType.Label,
                    color = ComicInk.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (trip != null) {
                    Text(
                        text = "${trip.startDate} to ${trip.endDate}",
                        style = TrippinType.Label,
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
                        text = "People on this trip",
                        style = TrippinType.Body,
                        color = ComicInk
                    )
                    Text(
                        text = when {
                            trip == null -> "Not known offline"
                            joined == 0 -> "Nobody has added their details yet"
                            joined == 1 -> "1 person has added their details"
                            else -> "$joined people have added their details"
                        },
                        style = TrippinType.Title,
                        color = ComicInk,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (plannedFor != null && plannedFor > joined) {
                        Text(
                            text = "The trip was set up for $plannedFor people, so " +
                                "${plannedFor - joined} more can still add theirs.",
                            style = TrippinType.Body,
                            color = ComicMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        text = "Each person sets their own budget cap, interests and pace. " +
                            "Nothing is guessed on their behalf.",
                        style = TrippinType.Body,
                        color = ComicMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (joined == 0) {
            Spacer(modifier = Modifier.height(12.dp))
            ArriveOnEnter(delayMillis = 90) {
                TrippinCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "What each person wants",
                            style = TrippinType.Body,
                            color = ComicInk
                        )
                        Text(
                            text = "Nothing is set yet. Once someone sets a budget cap and their " +
                                "interests, they appear here and the group's conflicts appear below.",
                            style = TrippinType.Caption,
                            color = ComicMuted,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }

        travellers.forEachIndexed { index, traveller ->
            Spacer(modifier = Modifier.height(12.dp))
            val share = shares.firstOrNull { it.travellerId == traveller.id }
            ArriveOnEnter(delayMillis = 120 + index * 40) {
                TravellerCard(
                    traveller = traveller,
                    share = share,
                    currency = currency,
                    costKnown = costKnown
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        ArriveOnEnter(delayMillis = 200) {
            DecisionsCard(
                travellers = travellers,
                shares = shares,
                details = details,
                currency = currency
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        ArriveOnEnter(delayMillis = 240) {
            Text(
                text = buildString {
                    append(
                        "Costs here are the plan's own estimate per person, built from the day " +
                            "rates the engine uses for stay, food, local travel and entry fees. " +
                            "They are a range, not a quote, and not a booking price."
                    )
                    if (currency == null && costKnown) {
                        append(
                            " The plan does not state a currency, so amounts are shown as plain " +
                                "numbers."
                        )
                    }
                },
                style = TrippinType.Body,
                color = ComicMuted
            )
        }

        if (trip != null) {
            Spacer(modifier = Modifier.height(12.dp))
            ArriveOnEnter(delayMillis = 280) {
                TrippinCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "This trip",
                            style = TrippinType.Body,
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
                            style = TrippinType.Caption,
                            color = ComicMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

/** One person, with everything they set themselves and their own share of the plan. */
@Composable
private fun TravellerCard(
    traveller: TravellerDto,
    share: PerTravellerCostDto?,
    currency: String?,
    costKnown: Boolean
) {
    val cap = traveller.budgetCap
    val overCap = share?.overCap == true && cap != null

    TrippinCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = traveller.name.ifBlank { "No name set" },
                    style = TrippinType.Body,
                    color = ComicInk
                )
                if (overCap) {
                    Box(
                        modifier = Modifier
                            .background(WarnAmberSurface, RoundedCornerShape(4.dp))
                            .border(1.5.dp, ComicInk, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Over their cap",
                            color = WarnAmber,
                            style = TrippinType.Caption,
                        )
                    }
                }
            }

            DetailRow(
                label = "Budget cap",
                value = if (cap == null) "Not set" else formatAmount(cap, currency)
            )
            DetailRow(
                label = "Wants",
                value = if (traveller.interests.isEmpty()) {
                    "Not set"
                } else {
                    traveller.interests.joinToString(", ")
                }
            )
            DetailRow(
                label = "Avoids",
                value = if (traveller.dislikes.isEmpty()) {
                    "Not set"
                } else {
                    traveller.dislikes.joinToString(", ")
                }
            )
            DetailRow(
                label = "Pace",
                value = traveller.pace?.takeIf { it.isNotBlank() } ?: "Not set"
            )

            Text(
                text = "Their share of this plan",
                style = TrippinType.Caption,
                color = ComicMuted,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = when {
                    share == null && costKnown -> "Not worked out for them yet"
                    share == null -> "This trip has no cost estimate yet"
                    else -> "${formatAmount(share.shareMin, currency)} to " +
                        formatAmount(share.shareMax, currency)
                },
                style = TrippinType.Body,
                color = ComicInk,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (cap != null && share != null && share.overCap) {
                Text(
                    text = overByLine(cap, share.shareMax, currency),
                    style = TrippinType.Caption,
                    color = ComicInk,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

/**
 * The decision list. Every line here is computed from data the server sent: a cap below a real
 * share, a stop with a real dislike against it, two real pace settings that differ. When there is
 * nothing to decide the card says so instead of inventing a tension.
 */
@Composable
private fun DecisionsCard(
    travellers: List<TravellerDto>,
    shares: List<PerTravellerCostDto>,
    details: TripDetailsDto?,
    currency: String?
) {
    val budgetConflicts = travellers.mapNotNull { traveller ->
        val share = shares.firstOrNull { it.travellerId == traveller.id }
        val cap = traveller.budgetCap
        if (share != null && cap != null && share.overCap) {
            BudgetConflict(traveller.name, cap, share)
        } else {
            null
        }
    }

    val stopConflicts = mutableListOf<StopConflict>()
    details?.itinerary?.days?.forEach { day ->
        day.activities.forEach { activity ->
            val support = activity.support
            if (support != null && support.against.isNotEmpty()) {
                stopConflicts.add(
                    StopConflict(
                        dayLabel = "Day ${day.dayIndex}",
                        title = activity.title,
                        who = support.against.joinToString(", "),
                        counts = "${support.want} of ${support.total} want this"
                    )
                )
            }
        }
    }

    val paces = travellers.mapNotNull { t ->
        t.pace?.takeIf { it.isNotBlank() }?.let { t.name.ifBlank { "Someone" } to it }
    }
    val paceConflict = paces.size >= 2 && paces.map { it.second.lowercase() }.distinct().size > 1

    val idle = budgetConflicts.isEmpty() && stopConflicts.isEmpty() && !paceConflict

    TrippinCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Decisions needing the group",
                style = TrippinType.Body,
                color = ComicInk
            )

            if (idle) {
                Text(
                    text = if (travellers.isEmpty()) {
                        "Nothing to decide yet. When people set their budgets and interests, " +
                            "anything they disagree about shows up here."
                    } else {
                        "Nothing is in conflict right now. Nobody's cap is below their share, " +
                            "and no stop is on anyone's avoid list."
                    },
                    style = TrippinType.Body,
                    color = ComicMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            budgetConflicts.forEach { conflict ->
                Text(
                    text = "${conflict.name.ifBlank { "This person" }} set a cap of " +
                        "${formatAmount(conflict.cap, currency)}. This plan comes to " +
                        "${formatAmount(conflict.share.shareMax, currency)} for them at the top " +
                        "of the range.",
                    style = TrippinType.Label,
                    color = ComicInk,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = overByLine(conflict.cap, conflict.share.shareMax, currency) +
                        " The plan can be trimmed, their cap can be raised, or they can skip a stop.",
                    style = TrippinType.Body,
                    color = ComicMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            stopConflicts.forEach { conflict ->
                Text(
                    text = "${conflict.dayLabel}: ${conflict.title}",
                    style = TrippinType.Label,
                    color = ComicInk,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "${conflict.counts}, and ${conflict.who} listed this as something to avoid.",
                    style = TrippinType.Caption,
                    color = ComicMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (paceConflict) {
                Text(
                    text = "Pace differs: " + paces.joinToString(", ") { "${it.first} is ${it.second}" } + ".",
                    style = TrippinType.Label,
                    color = ComicInk,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "A day that suits one pace will feel long or short to the other, so " +
                        "agree on a pace before the days are rebuilt.",
                    style = TrippinType.Body,
                    color = ComicMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (travellers.isNotEmpty() && shares.isEmpty() && stopConflicts.isEmpty()) {
                Text(
                    text = "This trip has no cost estimate yet, so a cap cannot be checked " +
                        "against a share.",
                    style = TrippinType.Body,
                    color = ComicMuted,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = TrippinType.Caption,
            color = ComicMuted,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = TrippinType.Caption,
            color = ComicInk,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** A cap that the top of that person's own range goes past. Real cap, real share, nothing inferred. */
private data class BudgetConflict(
    val name: String,
    val cap: Double,
    val share: PerTravellerCostDto
)

/** A stop somebody listed as a dislike, with the real support counts for that stop. */
private data class StopConflict(
    val dayLabel: String,
    val title: String,
    val who: String,
    val counts: String
)

/** How far past their own cap the top of their range is, in plain words and real figures. */
private fun overByLine(cap: Double, shareMax: Double, currency: String?): String {
    val over = shareMax - cap
    return if (over.roundToLong() <= 0L) {
        "Their share goes just past the cap they set."
    } else {
        "That is ${formatAmount(over, currency)} past the cap they set."
    }
}

/**
 * The currency of the trip, taken from the plan's own stop prices. The server sends the trip
 * currency on the requirements block, which the Android client does not carry yet, so this reads
 * the same value off the priced stops instead of assuming one. Null means no stop stated a
 * currency, and then no symbol is printed.
 */
private fun currencyOf(details: TripDetailsDto?): String? =
    details?.itinerary?.days
        ?.flatMap { it.activities }
        ?.firstNotNullOfOrNull { activity ->
            activity.currency?.takeIf { it.isNotBlank() }
        }

/** A whole amount with its currency, and no symbol at all when the currency is not known. */
private fun formatAmount(value: Double, currency: String?): String {
    val grouped = NumberFormat.getIntegerInstance(Locale.US).format(value.roundToLong())
    val code = currency?.trim()?.uppercase()
    val prefix = when (code) {
        null, "" -> ""
        "INR" -> "₹"
        "EUR" -> "€"
        "GBP" -> "£"
        "JPY" -> "¥"
        "USD" -> "$"
        else -> "$code "
    }
    return "$prefix$grouped"
}
