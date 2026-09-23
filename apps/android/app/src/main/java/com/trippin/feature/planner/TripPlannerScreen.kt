package com.trippin.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.trippin.core.design.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The currencies the picker offers, with the symbol the traveller actually reads.
 *
 * The default is INR because the production plan says so (section 7: "default currency ₹ with a
 * picker"). Nothing here guesses a currency from the destination: this app cannot know that a trip
 * to Tokyo is priced in yen, so the traveller states it and the trip is created in what they chose.
 */
private val plannerCurrencies = listOf(
    "INR" to "₹",
    "EUR" to "€",
    "GBP" to "£",
    "JPY" to "¥",
    "USD" to "$"
)

private val plannerDateLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TripPlannerScreen(
    initialDestination: String = "",
    onNavigateBack: () -> Unit,
    onTripCreated: (String) -> Unit
) {
    val today = remember { LocalDate.now() }

    // Nothing on this form starts pre-filled with a value the traveller did not choose. The
    // destination starts empty, both dates start empty, the budget starts empty and no interest is
    // ticked, because every one of those feeds the plan and a default here is a claim about the
    // traveller. The only starting values left are the traveller count and the pace, both visible
    // and both one tap to change.
    var destination by remember { mutableStateOf(initialDestination.trim()) }
    var travelersCount by remember { mutableIntStateOf(2) }
    var currency by remember { mutableStateOf("INR") }
    var budget by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf<LocalDate?>(null) }
    var endDate by remember { mutableStateOf<LocalDate?>(null) }
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var selectedPace by remember { mutableStateOf("MODERATE") }
    val availableInterests = listOf("Art", "Food", "History", "Architecture", "Nightlife", "Nature", "Shopping")
    val selectedInterests = remember { mutableStateListOf<String>() }

    val chosenStart = startDate
    val chosenEnd = endDate
    val currencySymbol = plannerCurrencies.firstOrNull { it.first == currency }?.second.orEmpty()

    val budgetValue = parseStatedAmount(budget)
    val budgetProblem = when {
        budget.isBlank() -> null
        budgetValue == null || budgetValue <= 0.0 -> "Enter your budget as a number, or leave the field blank."
        else -> null
    }
    val dateProblem: String? = if (chosenStart == null || chosenEnd == null) {
        "Pick the first and the last day of the trip."
    } else if (chosenEnd.isBefore(chosenStart)) {
        "The last day cannot be before the first day."
    } else if (chosenStart.isBefore(today)) {
        "Pick a first day from today onwards."
    } else {
        null
    }
    val destinationProblem = if (destination.trim().length >= 2) null else "Type where you are going."
    val canBuild = destinationProblem == null && dateProblem == null && budgetProblem == null
    // The line names the first thing still missing, in the order the form asks for it, so it never
    // points at a field further down the screen than the one the traveller is looking at.
    val guidance = destinationProblem ?: dateProblem ?: budgetProblem

    Scaffold(
        topBar = {
            TopAppBar(
                colors = trippinTopBarColors(),
                title = {
                    Column {
                        Text(
                            text = "Plan a trip",
                            style = TrippinType.Title,
                            color = Ink
                        )
                        Text(
                            text = "Checked against opening hours and travel times",
                            style = TrippinType.Caption,
                            color = InkMuted
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
                .background(Paper),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                PlannerCard(title = "Where to") {
                    OutlinedTextField(
                        value = destination,
                        onValueChange = { destination = it },
                        placeholder = { Text("e.g. Manali, Lisbon, Tokyo", style = TrippinType.Body, color = InkMuted) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = trippinFieldInk(),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true
                    )
                }
            }

            item {
                PlannerCard(
                    title = "When",
                    subtitle = "The plan runs from the first day to the last day you pick. Nothing is assumed."
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        PlannerDateField(
                            label = "First day",
                            date = chosenStart,
                            modifier = Modifier.weight(1f),
                            onClick = { pickingStart = true }
                        )
                        PlannerDateField(
                            label = "Last day",
                            date = chosenEnd,
                            modifier = Modifier.weight(1f),
                            onClick = { pickingEnd = true }
                        )
                    }
                }
            }

            item {
                PlannerCard(title = "Travellers and budget") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            // The count alone. The label already says Travellers, and the two
                            // steppers in the trailing slot are 44dp each because that is the
                            // project's touch target floor, so two of them take 88dp of this
                            // field and the words would be cut. The label carries the word.
                            value = "$travelersCount",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Travellers", style = TrippinType.Caption) },
                            modifier = Modifier.weight(1f),
                            colors = trippinFieldInk(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                Row {
                                    IconButton(
                                        onClick = { if (travelersCount > 1) travelersCount-- },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "One traveller fewer")
                                    }
                                    IconButton(
                                        onClick = { travelersCount++ },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "One traveller more")
                                    }
                                }
                            }
                        )
                        OutlinedTextField(
                            value = budget,
                            onValueChange = { typed ->
                                if (typed.all { it.isDigit() || it == '.' || it == ',' }) budget = typed
                            },
                            label = { Text("Budget per person ($currencySymbol)", style = TrippinType.Caption) },
                            placeholder = { Text("Optional", style = TrippinType.Body, color = InkMuted) },
                            modifier = Modifier.weight(1f),
                            colors = trippinFieldInk(),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Currency",
                        style = TrippinType.Caption,
                        color = InkMuted
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        plannerCurrencies.forEach { (code, symbol) ->
                            TrippinChoiceChip(
                                text = "$symbol $code",
                                selected = currency == code,
                                onClick = { currency = code }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "The trip is created in the currency you pick here, and every amount in " +
                            "it is stated in that currency. Leave the budget blank and the plan is not " +
                            "checked against one.",
                        style = TrippinType.Caption,
                        color = InkMuted
                    )
                }
            }

            item {
                PlannerCard(title = "Pace") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("RELAXED", "MODERATE", "FAST").forEach { pace ->
                            Box(modifier = Modifier.weight(1f)) {
                                TrippinChoiceChip(
                                    text = pace,
                                    selected = selectedPace == pace,
                                    onClick = { selectedPace = pace }
                                )
                            }
                        }
                    }
                }
            }

            item {
                PlannerCard(
                    title = "Your interests",
                    subtitle = "Pick what you want out of the trip. Nothing is picked for you."
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableInterests.forEach { interest ->
                            TrippinChoiceChip(
                                text = interest,
                                selected = selectedInterests.contains(interest),
                                onClick = {
                                    if (selectedInterests.contains(interest)) selectedInterests.remove(interest)
                                    else selectedInterests.add(interest)
                                }
                            )
                        }
                    }
                }
            }

            item {
                var isSubmitting by remember { mutableStateOf(false) }
                var submitError by remember { mutableStateOf<String?>(null) }
                val scope = rememberCoroutineScope()

                if (submitError != null) {
                    Text(
                        text = submitError!!,
                        style = TrippinType.Body,
                        color = DangerCrimson,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                Button(
                    onClick = {
                        isSubmitting = true
                        submitError = null
                        scope.launch {
                            try {
                                val req = com.trippin.core.network.CreateTripDto(
                                    destination = destination.trim(),
                                    startDate = startDate!!.toString(),
                                    endDate = endDate!!.toString(),
                                    travelersCount = travelersCount,
                                    // Only sent when the traveller typed one. There is no stand-in
                                    // figure, because a budget the traveller did not set would be
                                    // read by the engine as their own.
                                    budgetTotal = budgetValue,
                                    currency = currency,
                                    pace = selectedPace,
                                    interests = selectedInterests.toList()
                                )
                                val res = com.trippin.core.network.NetworkModule.apiService.createTrip(req)
                                com.trippin.core.network.NetworkModule.apiService.triggerGeneration(res.tripId)
                                onTripCreated(res.tripId)
                            } catch (_: Exception) {
                                // One fixed sentence, and no cause named. The app cannot tell a
                                // dead network from a rejected trip from here, so it claims
                                // neither, the same shape Map, Today and Plan use on a failure.
                                submitError = "Could not create the trip"
                                isSubmitting = false
                            }
                        }
                    },
                    enabled = !isSubmitting && canBuild,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .border(2.dp, Ink, RoundedCornerShape(8.dp)),
                    colors = trippinButtonColors(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            color = LocalContentColor.current,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Building your plan",
                            style = TrippinType.Label
                        )
                    } else {
                        Icon(Icons.Default.Bolt, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Build my plan",
                            style = TrippinType.Label
                        )
                    }
                }

                if (guidance != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = guidance,
                        style = TrippinType.Caption,
                        color = InkMuted
                    )
                }
            }
        }
    }

    if (pickingStart) {
        PlannerDatePickerDialog(
            initial = chosenStart,
            onDismiss = { pickingStart = false },
            onPicked = { picked ->
                startDate = picked
                // A first day after the chosen last day would leave the form in a state the server
                // would reject, so the last day is cleared rather than quietly moved.
                if (endDate?.isBefore(picked) == true) endDate = null
            }
        )
    }

    if (pickingEnd) {
        PlannerDatePickerDialog(
            initial = chosenEnd,
            onDismiss = { pickingEnd = false },
            onPicked = { picked -> endDate = picked }
        )
    }
}

@Composable
private fun PlannerCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    TrippinCard {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = TrippinType.Heading,
                color = Ink
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = TrippinType.Caption,
                    color = InkMuted
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

/**
 * A date as a field the traveller opens. It shows what was chosen or says to choose a day, and it
 * never carries a date nobody picked.
 */
@Composable
private fun PlannerDateField(
    label: String,
    date: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = TrippinType.Caption,
            color = InkMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .border(2.dp, Ink, RoundedCornerShape(8.dp))
                .background(Panel, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = date?.format(plannerDateLabel) ?: "Choose a day",
                style = if (date == null) TrippinType.Body else TrippinType.Label,
                color = if (date == null) InkMuted else Ink
            )
            Icon(
                imageVector = Icons.Default.DateRange,
                contentDescription = null,
                tint = Ink,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * The day picker, in this app's own colours rather than the system theme's.
 *
 * Material's date picker resolves every colour it draws through its own tokens to the scheme, and
 * [com.trippin.core.design.TrippinTheme] maps five rows of that scheme, so this dialog was drawing
 * Material's colours inside a parchment app: on a phone in dark mode a dark card with pale greys,
 * and even in light mode a lavender grey card (#ECE6F0) with #49454F ink rather than this app's
 * white and ink. Every colour this dialog is able to draw is on a token below.
 *
 * The values that are primary derived keep the value the light scheme already resolved to, because
 * this app's primary is [AccentCrimson] and its onPrimary is white, so those pixels do not move in
 * light mode and stop following the dark scheme's own red. The rest are the app's ink and surface,
 * which in light mode do move: the card is [Panel] rather than Material's lavender grey and the
 * digits, weekdays, months and rule are [Ink] or [InkMuted] rather than #49454F.
 *
 * Deliberately NOT named, because this dialog cannot draw them: the six disabled colours, the two
 * selection range colours, and the month subhead, which Material draws only in a range picker. The
 * keyboard half is not a colour this file owns either, so the date field in Input mode takes the
 * same [trippinFieldInk] the other twelve fields in the app take rather than the scheme's own field
 * colours.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerDatePickerDialog(
    initial: LocalDate?,
    onDismiss: () -> Unit,
    onPicked: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    val pickerColors = DatePickerDefaults.colors(
        containerColor = Panel,
        titleContentColor = Ink,
        headlineContentColor = Ink,
        weekdayContentColor = InkMuted,
        navigationContentColor = Ink,
        yearContentColor = Ink,
        currentYearContentColor = AccentCrimson,
        selectedYearContentColor = OnCrimson,
        selectedYearContainerColor = AccentCrimson,
        dayContentColor = Ink,
        selectedDayContentColor = OnCrimson,
        selectedDayContainerColor = AccentCrimson,
        todayContentColor = AccentCrimson,
        todayDateBorderColor = AccentCrimson,
        dividerColor = Ink,
        dateTextFieldColors = trippinFieldInk()
    )
    DatePickerDialog(
        colors = pickerColors,
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    onDismiss()
                },
                colors = trippinTextButtonColors(AccentCrimson)
            ) {
                Text("Set", style = TrippinType.Label)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = trippinTextButtonColors()) {
                Text("Cancel", style = TrippinType.Label)
            }
        }
    ) {
        DatePicker(state = state, colors = pickerColors)
    }
}
