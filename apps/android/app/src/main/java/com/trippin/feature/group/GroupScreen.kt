package com.trippin.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.AccentCrimson
import com.trippin.core.design.ArriveOnEnter
import com.trippin.core.design.DangerCrimson
import com.trippin.core.design.OnCrimson
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinType
import com.trippin.core.design.Ink
import com.trippin.core.design.InkMuted
import com.trippin.core.design.Panel
import com.trippin.core.design.Paper
import com.trippin.core.design.WarnAmber
import com.trippin.core.design.WarnAmberSurface
import com.trippin.core.design.TrippinCard
import com.trippin.core.network.CreateTravellerRequestDto
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.PerTravellerCostDto
import com.trippin.core.network.TravellerDto
import com.trippin.core.network.TripDetailsDto
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
import retrofit2.HttpException

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
 *
 * The write half lives here too, because until 2026-09-21 this screen could only read: nothing in the
 * app could add a traveller, so every trip showed an empty group and every person on it read Not set
 * forever. Add someone sends only what was typed, and a field left blank is left out of the request
 * rather than filled with a stand-in, which is why a person's row still says Not set for anything
 * nobody has stated.
 */
@Composable
fun GroupScreen(tripId: String) {
    var details by remember { mutableStateOf(TripCacheManager.getTrip(tripId)) }
    var isLoading by remember { mutableStateOf(details == null) }
    var showAddPerson by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    // Bumped after a person is added, so the screen redraws from the server's own answer rather than
    // from the row the app hoped for.
    var refreshTick by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tripId, refreshTick) {
        try {
            val fresh = NetworkModule.apiService.getTripDetails(tripId)
            TripCacheManager.putTrip(tripId, fresh)
            details = fresh
        } catch (_: Exception) {
            // Offline, or the trip is gone. Whatever was cached stays on screen.
        }
        isLoading = false
    }

    /**
     * Adds one person to the trip. The cap is sent only when a number was typed, because a blank
     * field sent as a zero would be stored as a real cap of zero and would then read as that person's
     * own decision. The one fixed sentence for an unknown failure claims no cause, the same shape the
     * failed-load states use; the two status codes that mean something a person can act on are named.
     */
    fun addPerson(name: String, cap: Double?, pace: String?, interests: List<String>) {
        scope.launch {
            isAdding = true
            addError = null
            try {
                NetworkModule.apiService.addTraveller(
                    tripId,
                    CreateTravellerRequestDto(
                        name = name.trim(),
                        budgetCap = cap,
                        interests = interests,
                        dislikes = emptyList(),
                        pace = pace
                    )
                )
                showAddPerson = false
                refreshTick++
            } catch (e: HttpException) {
                addError = when (e.code()) {
                    401 -> "Sign in again, then add them."
                    403 -> "Only the person who created this trip can add people to it."
                    else -> "The server did not accept that, so they were not added."
                }
            } catch (_: Exception) {
                addError = "Could not reach the server. Check the connection and try again."
            } finally {
                isAdding = false
            }
        }
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
            .background(Paper)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        ArriveOnEnter {
            Column {
                Text(
                    text = "Group",
                    style = TrippinType.Title,
                    color = Ink
                )
                Text(
                    text = when {
                        trip == null && isLoading -> "Loading this trip"
                        trip == null -> "This trip is not available offline yet"
                        else -> trip.destination
                    },
                    style = TrippinType.Label,
                    color = Ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (trip != null) {
                    Text(
                        text = "${trip.startDate} to ${trip.endDate}",
                        style = TrippinType.Label,
                        color = InkMuted,
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
                        color = Ink
                    )
                    Text(
                        text = when {
                            trip == null -> "Not known offline"
                            joined == 0 -> "Nobody has added their details yet"
                            joined == 1 -> "1 person has added their details"
                            else -> "$joined people have added their details"
                        },
                        style = TrippinType.Title,
                        color = Ink,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    if (plannedFor != null && plannedFor > joined) {
                        Text(
                            text = "The trip was set up for $plannedFor people, so " +
                                "${plannedFor - joined} more can still add theirs.",
                            style = TrippinType.Body,
                            color = InkMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    Text(
                        text = "Each person sets their own budget cap, interests and pace. " +
                            "Nothing is guessed on their behalf.",
                        style = TrippinType.Body,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    if (trip != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        TrippinButton(
                            text = "Add someone",
                            onClick = { showAddPerson = true }
                        )
                    }
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
                            color = Ink
                        )
                        Text(
                            text = "Nothing is set yet. Once someone sets a budget cap and their " +
                                "interests, they appear here and the group's conflicts appear below.",
                            style = TrippinType.Caption,
                            color = InkMuted,
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
                            " Neither the trip nor the plan states a currency, so amounts are " +
                                "shown as plain numbers."
                        )
                    }
                },
                style = TrippinType.Body,
                color = InkMuted
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
                            color = Ink
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
                            color = InkMuted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAddPerson) {
        AddPersonDialog(
            currency = currency,
            isBusy = isAdding,
            error = addError,
            onDismiss = {
                if (!isAdding) {
                    showAddPerson = false
                    addError = null
                }
            },
            onAdd = { name, cap, pace, interests -> addPerson(name, cap, pace, interests) }
        )
    }
}

/**
 * The words the engine matches interests on: the frozen vocabulary the backend validates a traveller
 * against, and the same words it looks for on a stop when it counts support. The server trims and
 * lowercases whatever is sent and then keeps only the words on this list, so a word that is not here
 * is dropped rather than stored, and the value that is drawn capitalised is sent exactly as it is
 * written here. The chips offer the whole list rather than a guess at what a person might want.
 */
private val GROUP_INTEREST_WORDS = listOf(
    "culture", "food", "nightlife", "nature", "adventure", "shopping",
    "museums", "history", "photography", "wellness", "relaxation", "landmark"
)

/** The pace values the backend accepts, in the wording a person reads. */
private val GROUP_PACE_CHOICES = listOf(
    "relaxed" to "Relaxed",
    "balanced" to "Balanced",
    "packed" to "Packed"
)

/**
 * Adds one person to the trip. The name is the only field that is required, which is also the only
 * one the server insists on; everything else is optional and a blank field is left out of the request
 * rather than sent as a zero or an empty word. The cap is stated in the trip's own currency and its
 * label says which one, so a number here cannot be read as being in another currency.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPersonDialog(
    currency: String?,
    isBusy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAdd: (String, Double?, String?, List<String>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var capText by remember { mutableStateOf("") }
    var pace by remember { mutableStateOf<String?>(null) }
    var interests by remember { mutableStateOf(emptyList<String>()) }

    val cap = capText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
    val capProblem = when {
        capText.isBlank() -> null
        cap == null || cap <= 0.0 -> "Enter their cap as a number, or leave the field blank."
        else -> null
    }
    val canAdd = name.isNotBlank() && capProblem == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add someone to this trip", style = TrippinType.Heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Only what you type here is saved. Leave anything blank and their row " +
                        "says Not set until they fill it in themselves.",
                    style = TrippinType.Caption,
                    color = InkMuted
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { typed -> if (typed.length <= 60) name = typed },
                    singleLine = true,
                    enabled = !isBusy,
                    label = { Text("Their name", style = TrippinType.Label) },
                    textStyle = TrippinType.Body,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(8.dp))
                )
                OutlinedTextField(
                    value = capText,
                    onValueChange = { typed ->
                        if (typed.all { it.isDigit() || it == '.' || it == ',' }) capText = typed
                    },
                    singleLine = true,
                    enabled = !isBusy,
                    label = {
                        Text(
                            text = if (currency.isNullOrBlank()) {
                                "Budget cap (optional)"
                            } else {
                                "Budget cap in ${currency.uppercase()} (optional)"
                            },
                            style = TrippinType.Label
                        )
                    },
                    textStyle = TrippinType.Body,
                    shape = RoundedCornerShape(8.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().border(2.dp, Ink, RoundedCornerShape(8.dp))
                )
                capProblem?.let {
                    Text(text = it, style = TrippinType.Caption, color = DangerCrimson)
                }
                Text(text = "Pace", style = TrippinType.Caption, color = InkMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GROUP_PACE_CHOICES.forEach { (value, label) ->
                        TrippinChoiceChip(
                            text = label,
                            selected = pace == value,
                            onClick = { pace = if (pace == value) null else value }
                        )
                    }
                }
                Text(text = "Wants", style = TrippinType.Caption, color = InkMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GROUP_INTEREST_WORDS.forEach { word ->
                        TrippinChoiceChip(
                            text = word,
                            selected = interests.contains(word),
                            onClick = {
                                interests = if (interests.contains(word)) {
                                    interests - word
                                } else {
                                    interests + word
                                }
                            }
                        )
                    }
                }
                if (error != null) {
                    Text(text = error, style = TrippinType.Body, color = DangerCrimson)
                }
            }
        },
        confirmButton = {
            Button(
                colors = ButtonDefaults.buttonColors(
                    containerColor = AccentCrimson,
                    contentColor = OnCrimson
                ),
                enabled = !isBusy && canAdd,
                onClick = { onAdd(name, cap, pace, interests) }
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = OnCrimson
                    )
                } else {
                    Text("Add", style = TrippinType.Label)
                }
            }
        },
        dismissButton = {
            TextButton(enabled = !isBusy, onClick = onDismiss) {
                Text("Cancel", style = TrippinType.Label)
            }
        }
    )
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
                    color = Ink
                )
                if (overCap) {
                    Box(
                        modifier = Modifier
                            .background(WarnAmberSurface, RoundedCornerShape(4.dp))
                            .border(1.5.dp, Ink, RoundedCornerShape(4.dp))
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
                value = if (cap == null) "Not set" else formatStatedAmount(cap, currency)
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
                color = InkMuted,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = when {
                    share == null && costKnown -> "Not worked out for them yet"
                    share == null -> "This trip has no cost estimate yet"
                    else -> "${formatStatedAmount(share.shareMin, currency)} to " +
                        formatStatedAmount(share.shareMax, currency)
                },
                style = TrippinType.Body,
                color = Ink,
                modifier = Modifier.padding(top = 2.dp)
            )

            if (cap != null && share != null && share.overCap) {
                Text(
                    text = overByLine(cap, share.shareMax, currency),
                    style = TrippinType.Caption,
                    color = Ink,
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
                color = Ink
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
                    color = InkMuted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            budgetConflicts.forEach { conflict ->
                Text(
                    text = "${conflict.name.ifBlank { "This person" }} set a cap of " +
                        "${formatStatedAmount(conflict.cap, currency)}. This plan comes to " +
                        "${formatStatedAmount(conflict.share.shareMax, currency)} for them at the top " +
                        "of the range.",
                    style = TrippinType.Label,
                    color = Ink,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = overByLine(conflict.cap, conflict.share.shareMax, currency) +
                        " The plan can be trimmed, their cap can be raised, or they can skip a stop.",
                    style = TrippinType.Body,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            stopConflicts.forEach { conflict ->
                Text(
                    text = "${conflict.dayLabel}: ${conflict.title}",
                    style = TrippinType.Label,
                    color = Ink,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "${conflict.counts}, and ${conflict.who} listed this as something to avoid.",
                    style = TrippinType.Caption,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (paceConflict) {
                Text(
                    text = "Pace differs: " + paces.joinToString(", ") { "${it.first} is ${it.second}" } + ".",
                    style = TrippinType.Label,
                    color = Ink,
                    modifier = Modifier.padding(top = 12.dp)
                )
                Text(
                    text = "A day that suits one pace will feel long or short to the other, so " +
                        "agree on a pace before the days are rebuilt.",
                    style = TrippinType.Body,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            if (travellers.isNotEmpty() && shares.isEmpty() && stopConflicts.isEmpty()) {
                Text(
                    text = "This trip has no cost estimate yet, so a cap cannot be checked " +
                        "against a share.",
                    style = TrippinType.Body,
                    color = InkMuted,
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
            color = InkMuted,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            style = TrippinType.Caption,
            color = Ink,
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
        "That is ${formatStatedAmount(over, currency)} past the cap they set."
    }
}

/**
 * The currency of the trip. The server states it on the trip itself, and that is the currency it
 * priced the trip and each person's share in, which is also the currency a budget cap is stated in.
 * Only when the trip states none does this fall back to a currency on the plan's own priced stops.
 * Null means neither stated one, and then no symbol is printed and the footnote below says so.
 */
private fun currencyOf(details: TripDetailsDto?): String? =
    details?.trip?.currency?.takeIf { it.isNotBlank() }
        ?: details?.itinerary?.days
            ?.flatMap { it.activities }
            ?.firstNotNullOfOrNull { activity ->
                activity.currency?.takeIf { it.isNotBlank() }
            }

