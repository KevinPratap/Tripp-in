package com.trippin.feature.planner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.common.InterestWords
import com.trippin.core.common.TripPaceChoices
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTextField
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.network.DestinationSuggestionDto
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.design.currencySymbol
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val plannerDateLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(
    initialDestination: String,
    onBack: () -> Unit,
    onTripCreated: (String) -> Unit,
    viewModel: PlannerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }

    LaunchedEffect(state.createdTripId) {
        state.createdTripId?.let(onTripCreated)
    }

    TrippinScaffold(
        topBar = {
            TrippinTopBar(
                title = "Plan a trip",
                subtitle = "Checked against opening hours and travel times",
                onBack = onBack
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                PlannerCard("Where to") {
                    DestinationField(
                        value = state.destination,
                        suggestions = state.suggestions,
                        hint = state.suggestionHint,
                        loading = state.suggestionsLoading,
                        resolvedLabel = state.resolved?.label,
                        onValueChange = viewModel::onDestinationChange,
                        onPick = viewModel::pickSuggestion,
                        onDismiss = viewModel::dismissSuggestions
                    )
                    if (state.destinationIsUnresolved) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Pick a place from the list so the plan is built around the right one. " +
                                "Typed text still works, but a name we cannot find will fail once the plan starts.",
                            style = TrippinType.Caption,
                            color = colors.inkMuted
                        )
                    }
                }
            }

            item {
                PlannerCard("When", "The plan runs from the first day to the last day you pick. Nothing is assumed.") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateField("First day", state.startDate, Modifier.weight(1f)) { pickingStart = true }
                        DateField("Last day", state.endDate, Modifier.weight(1f)) { pickingEnd = true }
                    }
                }
            }

            item {
                PlannerCard("Travellers and budget") {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Travellers", style = TrippinType.Label, color = colors.ink, modifier = Modifier.weight(1f))
                        Stepper(
                            value = state.travelers,
                            onMinus = viewModel::decTravelers,
                            onPlus = viewModel::incTravelers
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    TrippinTextField(
                        value = state.budget,
                        onValueChange = viewModel::onBudgetChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = if (state.currency == null) {
                            "Budget per person"
                        } else {
                            "Budget per person (${currencySymbol(state.currency)})"
                        },
                        placeholder = "Optional",
                        keyboardType = KeyboardType.Decimal
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Currency", style = TrippinType.Caption, color = colors.inkMuted)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.offeredCurrencies.forEach { (code, symbol) ->
                            TrippinChoiceChip("$symbol $code", state.currency == code) { viewModel.setCurrency(code) }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (state.resolved?.currency != null && !state.currencyTouched) {
                            "Set from ${state.resolved?.country ?: "the destination"}, because that is where you are spending. " +
                                "Change it if you would rather budget in something else."
                        } else {
                            "The trip is created in the currency you pick, and every amount in it is stated in that currency. " +
                                "Leave the budget blank and the plan is not checked against one."
                        },
                        style = TrippinType.Caption,
                        color = colors.inkMuted
                    )
                }
            }

            item {
                PlannerCard("Pace") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TripPaceChoices.forEach { (code, label) ->
                            TrippinChoiceChip(label, state.pace == code) { viewModel.setPace(code) }
                        }
                    }
                }
            }

            item {
                PlannerCard("Your interests", "Pick what you want out of the trip. Nothing is picked for you.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        InterestWords.forEach { word ->
                            TrippinChoiceChip(
                                text = word.replaceFirstChar { it.uppercase() },
                                selected = word in state.interests
                            ) { viewModel.toggleInterest(word) }
                        }
                    }
                }
            }

            item {
                state.error?.let {
                    Text(it, style = TrippinType.Body, color = colors.danger, modifier = Modifier.padding(bottom = 10.dp))
                }
                TrippinButton(
                    text = if (state.submitting) "Building your plan" else "Build my plan",
                    onClick = viewModel::submit,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.canBuild,
                    loading = state.submitting,
                    leadingIcon = Icons.Default.Bolt
                )
                state.guidance?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = TrippinType.Caption, color = colors.inkMuted)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (pickingStart) {
        PlannerDatePicker(state.startDate, { pickingStart = false }, viewModel::setStartDate)
    }
    if (pickingEnd) {
        PlannerDatePicker(state.endDate, { pickingEnd = false }, viewModel::setEndDate)
    }
}

/**
 * The destination field, with suggestions underneath as the traveller types.
 *
 * Picking one is the point: it resolves the trip to a real place with coordinates behind it and sets
 * the currency from that country. Typing is never blocked, because the suggestions come from a network
 * call that can fail, and the engine resolves the destination again when it builds the plan.
 *
 * The list is a plain surface rather than a floating menu, so it pushes the form down instead of
 * covering the next field, and a phone keyboard cannot hide it.
 */
@Composable
private fun DestinationField(
    value: String,
    suggestions: List<DestinationSuggestionDto>,
    hint: SuggestionHint,
    loading: Boolean,
    resolvedLabel: String?,
    onValueChange: (String) -> Unit,
    onPick: (DestinationSuggestionDto) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = TrippinTheme.colors
    Column(Modifier.fillMaxWidth()) {
        TrippinTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                // Leaving the field puts the list away, so it does not sit open over the rest of the
                // form while the traveller fills in dates.
                .onFocusChanged { focus -> if (!focus.isFocused) onDismiss() },
            placeholder = "e.g. Manali, Lisbon, Tokyo",
            leadingIcon = Icons.Default.Search,
            trailingIcon = {
                when {
                    loading -> CircularProgressIndicator(
                        color = colors.accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    // A tick, so the traveller can see the difference between a place we resolved and
                    // words we are still going to have to guess at.
                    resolvedLabel != null -> Icon(
                        Icons.Default.Check,
                        contentDescription = "Destination found",
                        tint = colors.good
                    )
                    value.isNotEmpty() -> TrippinIconButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Clear the destination",
                        onClick = { onValueChange("") }
                    )
                    else -> Unit
                }
            }
        )

        when (hint) {
            SuggestionHint.NONE -> Unit

            SuggestionHint.RESULTS -> {
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .border(2.dp, colors.line, TrippinTheme.shapes.field)
                        .background(colors.panel, TrippinTheme.shapes.field)
                ) {
                    suggestions.forEachIndexed { index, suggestion ->
                        if (index > 0) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.panelAlt)
                            )
                        }
                        SuggestionRow(suggestion) { onPick(suggestion) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                // Provenance, stated rather than implied.
                Text("Places from OpenStreetMap", style = TrippinType.Caption, color = colors.inkMuted)
            }

            SuggestionHint.LOADING -> {
                Spacer(Modifier.height(8.dp))
                Text("Looking for places", style = TrippinType.Caption, color = colors.inkMuted)
            }

            SuggestionHint.NO_MATCHES -> {
                Spacer(Modifier.height(8.dp))
                // Nothing matched is not the same as the place not existing, and typing still works.
                Text(
                    "No matches yet. Keep typing, or use the name as it appears on a map.",
                    style = TrippinType.Caption,
                    color = colors.inkMuted
                )
            }
        }
    }
}

/** One place in the suggestion list. Tapping the whole row picks it. */
@Composable
private fun SuggestionRow(suggestion: DestinationSuggestionDto, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            // Comfortably above the 44dp touch minimum the design system fixes.
            .heightIn(min = 52.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.LocationOn,
            contentDescription = null,
            tint = colors.inkMuted,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(suggestion.name, style = TrippinType.Label, color = colors.ink)
            val place = listOfNotNull(suggestion.region.takeIf { it != suggestion.name }, suggestion.country)
                .joinToString(", ")
            if (place.isNotBlank()) {
                Text(place, style = TrippinType.Caption, color = colors.inkMuted)
            }
        }
        // Shown because it decides what the trip is budgeted in, so it is not a surprise later.
        suggestion.currency?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = TrippinType.Caption, color = colors.inkMuted)
        }
    }
}

@Composable
private fun PlannerCard(title: String, subtitle: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val colors = TrippinTheme.colors
    TrippinCard {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = TrippinType.Heading, color = colors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = TrippinType.Caption, color = colors.inkMuted)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun DateField(label: String, date: LocalDate?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = TrippinTheme.colors
    Column(modifier) {
        Text(label, style = TrippinType.Caption, color = colors.inkMuted)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .border(2.dp, colors.line, TrippinTheme.shapes.field)
                .background(colors.panel, TrippinTheme.shapes.field)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                date?.format(plannerDateLabel) ?: "Choose a day",
                style = if (date == null) TrippinType.Body else TrippinType.Label,
                color = if (date == null) colors.inkMuted else colors.ink
            )
            Icon(Icons.Default.DateRange, contentDescription = null, tint = colors.ink, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    val colors = TrippinTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.trippin.core.design.TrippinIconButton(Icons.Default.Remove, "One fewer", onMinus)
        Text("$value", style = TrippinType.Numeric, color = colors.ink, modifier = Modifier.width(44.dp).padding(horizontal = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        com.trippin.core.design.TrippinIconButton(Icons.Default.Add, "One more", onPlus)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlannerDatePicker(initial: LocalDate?, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("Set", style = TrippinType.Label, color = TrippinTheme.colors.accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = TrippinType.Label, color = TrippinTheme.colors.inkMuted) }
        }
    ) {
        DatePicker(state = pickerState)
    }
}
