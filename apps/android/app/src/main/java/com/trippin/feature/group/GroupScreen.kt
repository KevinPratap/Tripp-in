package com.trippin.feature.group

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.trippin.core.cache.TripCacheManager
import com.trippin.core.design.AccentCrimson
import com.trippin.core.design.ArriveOnEnter
import com.trippin.core.design.DangerCrimson
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.design.parseStatedAmount
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
import com.trippin.core.design.trippinButtonColors
import com.trippin.core.design.trippinFieldInk
import com.trippin.core.design.trippinTextButtonColors
import com.trippin.core.network.CreateTravellerRequestDto
import com.trippin.core.network.NetworkModule
import com.trippin.core.network.PerTravellerCostDto
import com.trippin.core.network.TravellerDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.network.UpdateTravellerRequestDto
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
 *
 * Each person's own row now carries the two ways off the wrong state, because a cap typed wrong was
 * permanent from the app and so was a person added twice. Changing them sends a PATCH holding only
 * the fields the owner changed, and the reason it is a diff rather than the whole form is recorded on
 * savePerson below. Removing them is the app's own destructive recipe, a confirm that fills with
 * DangerCrimson and names what goes with them.
 */
@Composable
fun GroupScreen(tripId: String) {
    var details by remember { mutableStateOf(TripCacheManager.getTrip(tripId)) }
    var isLoading by remember { mutableStateOf(details == null) }
    var showAddPerson by remember { mutableStateOf(false) }
    var isAdding by remember { mutableStateOf(false) }
    var addError by remember { mutableStateOf<String?>(null) }
    // The person whose own answers are being changed, and the state of that write. Same shape as the
    // add path above: the sheet is its own composable and this screen holds only the request state.
    var editing by remember { mutableStateOf<TravellerDto?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    // The person about to be taken off the trip, and the state of that write. Same shape again: the
    // confirm is its own composable and this screen holds only the request state.
    var removing by remember { mutableStateOf<TravellerDto?>(null) }
    var isRemoving by remember { mutableStateOf(false) }
    var removeError by remember { mutableStateOf<String?>(null) }
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

    /**
     * Changes one person's own answers. The body carries only the fields the owner actually changed,
     * and that is a rule rather than a preference: a field left out of a PATCH keeps the value it has,
     * while a field sent as null clears it, so sending the whole form would wipe anything this sheet
     * does not show. Dislikes are the live example, they are on the person's row and this sheet does
     * not ask about them, so they are left out of the body and the person keeps them.
     *
     * The other half of the same rule is why the sheet refuses to save an emptied cap or an unpicked
     * pace: this app cannot put an explicit null on the wire at all. Its serializer omits every
     * property that still holds its default, so budgetCap = null and pace = null are written as
     * nothing at all and the server reads that as "leave it alone". A clearance typed here would look
     * saved and would not be, so the sheet says a cap or a pace can be changed but not removed yet,
     * and holds Save until the field holds a real value.
     */
    fun savePerson(travellerId: String, body: UpdateTravellerRequestDto) {
        scope.launch {
            isSaving = true
            saveError = null
            try {
                NetworkModule.apiService.updateTraveller(tripId, travellerId, body)
                editing = null
                refreshTick++
            } catch (e: HttpException) {
                saveError = when (e.code()) {
                    401 -> "Sign in again, then try this."
                    403 -> "Only the person who created this trip can change people on it."
                    404 -> "That person is no longer on this trip."
                    else -> "The server did not accept that, so nothing changed."
                }
            } catch (_: Exception) {
                saveError = "Could not reach the server. Check the connection and try again."
            } finally {
                isSaving = false
            }
        }
    }

    /**
     * Takes one person off the trip, which is the only way off it: nothing else in the app can clean
     * up somebody added twice or a name typed wrong.
     *
     * This call is checked differently from the two writes above and the difference matters.
     * deleteTraveller answers with a bare response rather than a body, so a 401 or a 404 comes back
     * as an ordinary value instead of being thrown, and a call that read it as "no exception, so it
     * worked" would close the confirm and refetch while the person was still on the trip. The
     * response's own status decides here, the dialog stays open with the reason when it failed, and
     * the screen only refreshes from the server once the delete is confirmed.
     */
    fun removePerson(travellerId: String) {
        scope.launch {
            isRemoving = true
            removeError = null
            try {
                val response = NetworkModule.apiService.deleteTraveller(tripId, travellerId)
                if (response.isSuccessful) {
                    removing = null
                    refreshTick++
                } else {
                    removeError = when (response.code()) {
                        401 -> "Sign in again, then remove them."
                        404 -> "That person is already off this trip."
                        else -> "The server did not accept that, so they are still on the trip."
                    }
                }
            } catch (_: Exception) {
                removeError = "Could not reach the server. Check the connection and try again."
            } finally {
                isRemoving = false
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
        /*
         * The trip is not on this device, which is either the first read still running or a read
         * that failed with nothing cached. Both are drawn as the app's one shape for this state,
         * the same one the Map, Today and Plan screens draw, and nothing below it is drawn at all:
         * every card on this screen states something about a real trip, so the people count, the
         * empty interests card, the group's decisions and the cost footnote would each be a claim
         * about a trip that is not here. The failure names no cause, because the catch above covers
         * being offline and a trip that is not there alike, and it offers the one thing the person
         * can act on, which is to try the read again.
         */
        if (trip == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = AccentCrimson)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading this trip", style = TrippinType.Body, color = InkMuted)
                } else {
                    Text(
                        text = "Could not load this trip",
                        style = TrippinType.Title,
                        color = DangerCrimson
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        colors = trippinButtonColors(),
                        onClick = {
                            isLoading = true
                            refreshTick++
                        }
                    ) {
                        Text("Retry", style = TrippinType.Label)
                    }
                }
            }
            return@Column
        }

        ArriveOnEnter {
            Column {
                Text(
                    text = "Group",
                    style = TrippinType.Title,
                    color = Ink
                )
                Text(
                    /* Only ever the destination the server sent. There is no stand-in, because a
                     * bar that names a place the trip does not have is worse than one that names
                     * none, which is the rule the Map and Today bars already follow. */
                    text = trip.destination,
                    style = TrippinType.Label,
                    color = Ink.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = "${trip.startDate} to ${trip.endDate}",
                    style = TrippinType.Label,
                    color = InkMuted,
                    modifier = Modifier.padding(top = 2.dp)
                )
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
                            // plannedFor is above joined here, so a set up size of one means
                            // nobody has joined yet. The word is singular in that case only.
                            text = if (plannedFor == 1) {
                                "The trip was set up for 1 person, so 1 more can still add theirs."
                            } else {
                                "The trip was set up for $plannedFor people, so " +
                                    "${plannedFor - joined} more can still add theirs."
                            },
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
                    Spacer(modifier = Modifier.height(14.dp))
                    TrippinButton(
                        text = "Add someone",
                        onClick = { showAddPerson = true }
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
                    costKnown = costKnown,
                    onEdit = {
                        saveError = null
                        editing = traveller
                    },
                    onRemove = {
                        removeError = null
                        removing = traveller
                    }
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

        Spacer(modifier = Modifier.height(12.dp))
        ArriveOnEnter(delayMillis = 280) {
            TrippinCard {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "This trip",
                        style = TrippinType.Body,
                        color = Ink
                    )
                    /* The plan's version and whether the trip is locked, and nothing else. The
                     * server's own status word used to be appended here, which printed "Ready"
                     * on every trip whose generation job had finished, and READY is a label on
                     * the plan's delete list because it is not derived from anything the app
                     * can see. The Trips card already derives its own state from the trip
                     * (Draft, Deciding, Locked, Finished) instead of printing this word, so the
                     * two screens said different things about the same trip. The lock state is
                     * a real value off the wire and stays; the build state is drawn where it
                     * belongs, on Plan. */
                    Text(
                        text = "Plan version ${trip.currentVersion} · " +
                            if (trip.isLocked) "Locked" else "Not locked",
                        style = TrippinType.Caption,
                        color = InkMuted,
                        modifier = Modifier.padding(top = 6.dp)
                    )
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

    editing?.let { person ->
        EditPersonDialog(
            traveller = person,
            currency = currency,
            isBusy = isSaving,
            error = saveError,
            onDismiss = {
                if (!isSaving) {
                    editing = null
                    saveError = null
                }
            },
            onSave = { body -> savePerson(person.id, body) }
        )
    }

    removing?.let { person ->
        RemovePersonDialog(
            name = person.name.trim(),
            isBusy = isRemoving,
            error = removeError,
            onDismiss = {
                if (!isRemoving) {
                    removing = null
                    removeError = null
                }
            },
            onRemove = { removePerson(person.id) }
        )
    }
}

/**
 * The words the engine matches interests on: the frozen vocabulary the backend validates a traveller
 * against, and the same words it looks for on a stop when it counts support. The server trims and
 * lowercases whatever is sent and then keeps only the words on this list, so a word that is not here
 * is dropped rather than stored, and the value that is drawn capitalised is sent exactly as it is
 * written here. The chips offer the whole list rather than a guess at what a person might want.
 *
 * This is the app's one home for the traveller vocabulary, so the join sheet on the Trips tab reads
 * it from here rather than carrying a second copy that could drift from this one.
 */
internal val TRAVELLER_INTEREST_WORDS = listOf(
    "culture", "food", "nightlife", "nature", "adventure", "shopping",
    "museums", "history", "photography", "wellness", "relaxation", "landmark"
)

/** The pace values the backend accepts, in the wording a person reads. */
internal val TRAVELLER_PACE_CHOICES = listOf(
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

    val cap = parseStatedAmount(capText)
    val capProblem = when {
        capText.isBlank() -> null
        cap == null -> "Enter their cap as a number, or leave the field blank."
        cap <= 0.0 -> "A cap has to be more than zero."
        else -> null
    }
    val canAdd = name.isNotBlank() && capProblem == null

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = Ink,
        textContentColor = Ink,
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
                    colors = trippinFieldInk(),
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
                    colors = trippinFieldInk(),
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
                    TRAVELLER_PACE_CHOICES.forEach { (value, label) ->
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
                    TRAVELLER_INTEREST_WORDS.forEach { word ->
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
                colors = trippinButtonColors(),
                enabled = !isBusy && canAdd,
                onClick = { onAdd(name, cap, pace, interests) }
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text("Add", style = TrippinType.Label)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                colors = trippinTextButtonColors()
            ) {
                Text("Cancel", style = TrippinType.Label)
            }
        }
    )
}

/**
 * Changes one person's own answers. Every field starts at what that person set, which is the truth
 * about them and not a guess at it: a cap they set is drawn in the cap field, their pace is the
 * selected chip and their interests are the selected words. Only the fields the owner actually
 * changes are sent, so nothing this sheet does not show is touched.
 *
 * The cap and the pace can be changed here but not removed, and the sheet says so only when the
 * owner tries to empty one rather than apologising in advance. The reason is on savePerson: a null
 * never reaches the wire from this app, so a cleared field would be a save that saved nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditPersonDialog(
    traveller: TravellerDto,
    currency: String?,
    isBusy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (UpdateTravellerRequestDto) -> Unit
) {
    var name by remember { mutableStateOf(traveller.name) }
    var capText by remember {
        mutableStateOf(traveller.budgetCap?.let { capFieldText(it) } ?: "")
    }
    var pace by remember { mutableStateOf(traveller.pace?.takeIf { it.isNotBlank() }) }
    var interests by remember { mutableStateOf(traveller.interests) }

    val cap = parseStatedAmount(capText)
    val capProblem = when {
        capText.isBlank() && traveller.budgetCap != null ->
            "A cap can be changed here but not removed yet, so enter the cap they should have."
        capText.isBlank() -> null
        cap == null -> "Enter their cap as a number, or leave the field blank."
        cap <= 0.0 -> "A cap has to be more than zero."
        else -> null
    }
    val paceProblem = if (pace == null && traveller.pace != null) {
        "A pace can be changed here but not removed yet, so pick one of the three."
    } else {
        null
    }

    // Only the differences travel, and interests are compared as sets so that tapping a word off and
    // back on is not mistaken for a change.
    val body = UpdateTravellerRequestDto(
        name = name.trim().takeIf { it != traveller.name },
        budgetCap = cap.takeIf { it != null && it != traveller.budgetCap },
        interests = interests.takeIf { it.toSet() != traveller.interests.toSet() },
        dislikes = null,
        pace = pace.takeIf { it != traveller.pace }
    )
    val changed = body.name != null || body.budgetCap != null ||
        body.interests != null || body.pace != null
    val canSave = name.isNotBlank() && capProblem == null && paceProblem == null && changed

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        titleContentColor = Ink,
        textContentColor = Ink,
        title = {
            Text(
                text = "Edit " + traveller.name.ifBlank { "this person" },
                style = TrippinType.Heading
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "These are the answers they set. Only what you change here is saved.",
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
                    colors = trippinFieldInk(),
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
                    colors = trippinFieldInk(),
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
                    TRAVELLER_PACE_CHOICES.forEach { (value, label) ->
                        TrippinChoiceChip(
                            text = label,
                            selected = pace == value,
                            onClick = { pace = if (pace == value) null else value }
                        )
                    }
                }
                paceProblem?.let {
                    Text(text = it, style = TrippinType.Caption, color = DangerCrimson)
                }
                Text(text = "Wants", style = TrippinType.Caption, color = InkMuted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TRAVELLER_INTEREST_WORDS.forEach { word ->
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
                colors = trippinButtonColors(),
                enabled = !isBusy && canSave,
                onClick = { onSave(body) }
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text("Save", style = TrippinType.Label)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                colors = trippinTextButtonColors()
            ) {
                Text("Cancel", style = TrippinType.Label)
            }
        }
    )
}

/**
 * Takes one person off the trip. The sentence states what goes with them and nothing more: their cap,
 * wants and pace, and their own share row, which the server computes from the people on the trip. It
 * does not promise that the plan itself is rebuilt, because removing a person does not rebuild it.
 *
 * The confirm fills with DangerCrimson and its dismiss reads Keep, which is the pair the Trips tab
 * uses for a delete, and the failed sentence sits in the dialog rather than closing it, so a removal
 * that did not happen never looks like one that did.
 */
@Composable
private fun RemovePersonDialog(
    name: String,
    isBusy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        containerColor = Panel,
        titleContentColor = Ink,
        textContentColor = Ink,
        title = { Text("Remove this person", style = TrippinType.Heading) },
        text = {
            Column {
                Text(
                    text = if (name.isEmpty()) {
                        "Remove this person from the trip?"
                    } else {
                        "Remove $name from the trip?"
                    } + " The budget cap, wants and pace they set go with them, and their own " +
                        "share of the plan is not counted any more.",
                    style = TrippinType.Label,
                    color = Ink
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = TrippinType.Label,
                        color = DangerCrimson,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                colors = trippinButtonColors(DangerCrimson),
                enabled = !isBusy,
                onClick = onRemove
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                } else {
                    Text("Remove", style = TrippinType.Label)
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isBusy,
                onClick = onDismiss,
                colors = trippinTextButtonColors()
            ) {
                Text("Keep", style = TrippinType.Label)
            }
        }
    )
}

/**
 * A cap put back into a field: no currency symbol, because the field's own label names the currency,
 * and no decimal point on a whole number, because 4500.0 is not how anybody writes a cap.
 */
private fun capFieldText(cap: Double): String {
    val whole = cap.roundToLong()
    return if (cap == whole.toDouble()) whole.toString() else cap.toString()
}

/** One person, with everything they set themselves and their own share of the plan. */
@Composable
private fun TravellerCard(
    traveller: TravellerDto,
    share: PerTravellerCostDto?,
    currency: String?,
    costKnown: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit
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

            // The two ways to fix a wrong person, drawn the way the Trips row draws Invite: not a
            // filled control, just a word with padding that makes the touch target 44dp. Remove is
            // the one destructive label in the app with a colour rather than a fill, which is what
            // the Plan screen's own menu entry for Delete this trip already does.
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                Text(
                    text = "Edit",
                    style = TrippinType.Label,
                    color = Ink,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { onEdit() }
                        .padding(horizontal = 8.dp, vertical = 14.dp)
                )
                Text(
                    text = "Remove",
                    style = TrippinType.Label,
                    color = DangerCrimson,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clickable { onRemove() }
                        .padding(horizontal = 8.dp, vertical = 14.dp)
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

