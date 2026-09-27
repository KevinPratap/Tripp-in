package com.trippin.feature.planner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.design.TrippinCurrencies
import com.trippin.core.design.currencySymbol
import com.trippin.core.design.parseStatedAmount
import com.trippin.core.network.CreateTripDto
import com.trippin.core.network.DestinationSuggestionDto
import com.trippin.core.repository.PlacesRepository
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Planner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PlannerUiState(
    val destination: String = "",
    /**
     * The place the traveller picked from the suggestions, or null while the field is still free text.
     *
     * When this is set the destination is a real location with coordinates behind it, so the engine's
     * own geocode at build time cannot miss, and the trip is priced in that country's money.
     */
    val resolved: DestinationSuggestionDto? = null,
    val suggestions: List<DestinationSuggestionDto> = emptyList(),
    val suggestionsLoading: Boolean = false,
    val suggestionsVisible: Boolean = false,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val travelers: Int = 2,
    /**
     * Null until the destination resolves or the traveller picks one.
     *
     * It used to default to INR for every trip on the planet, which quietly priced a Lisbon trip in
     * rupees. There is no honest universal default, so there is no default: the destination decides it,
     * and if the traveller never picks a destination from the list they are asked.
     */
    val currency: String? = null,
    /** Set once the traveller chooses a currency, so a later destination does not overwrite them. */
    val currencyTouched: Boolean = false,
    val budget: String = "",
    val pace: String = "MODERATE",
    val interests: Set<String> = emptySet(),
    val submitting: Boolean = false,
    val error: String? = null,
    val createdTripId: String? = null
) {
    val budgetValue: Double? get() = if (budget.isBlank()) null else parseStatedAmount(budget)

    val destinationProblem: String? get() =
        if (destination.trim().length >= 2) null else "Type where you are going."

    val dateProblem: String? get() {
        val s = startDate; val e = endDate
        val today = LocalDate.now()
        return when {
            s == null || e == null -> "Pick the first and last day of the trip."
            e.isBefore(s) -> "The last day cannot be before the first day."
            s.isBefore(today) -> "Pick a first day from today onwards."
            else -> null
        }
    }

    val currencyProblem: String? get() =
        if (currency.isNullOrBlank()) "Pick the currency this trip is budgeted in." else null

    val budgetProblem: String? get() = when {
        budget.isBlank() -> null
        budgetValue == null -> "Enter your budget as a number, or leave it blank."
        budgetValue!! <= 0.0 -> "A budget has to be more than zero."
        else -> null
    }

    /** The first thing still missing, in the order the form asks for it. */
    val guidance: String? get() = destinationProblem ?: dateProblem ?: currencyProblem ?: budgetProblem
    val canBuild: Boolean get() = guidance == null && !submitting

    /**
     * The currency chips to offer.
     *
     * The base list is five currencies, but a destination can resolve to any of about a hundred and
     * fifty, so the resolved one is added when it is not already there. Otherwise a traveller planning
     * Bangkok would be told the trip is in THB with no way to see or keep that choice.
     */
    val offeredCurrencies: List<Pair<String, String>> get() {
        val extra = currency?.takeIf { code -> TrippinCurrencies.none { it.first == code } }
        return if (extra == null) TrippinCurrencies else TrippinCurrencies + (extra to currencySymbol(extra))
    }

    /** True when the field holds text the traveller typed rather than a place they picked. */
    val destinationIsUnresolved: Boolean
        get() = resolved == null && destination.trim().length >= 2

    // ---- State transitions ----
    //
    // Kept here as pure functions rather than inline in the view model, so the rules that decide the
    // trip's currency can be tested without an Android runtime. The view model only sequences them.

    /**
     * The traveller edited the text.
     *
     * Editing clears whatever was picked: the field no longer names that place, so the trip must not
     * keep claiming it does.
     */
    fun withDestinationTyped(value: String): PlannerUiState =
        copy(destination = value, resolved = null, error = null, suggestionsVisible = true)

    /**
     * The traveller took a place from the list.
     *
     * The currency follows the destination, because that is where the money is spent, but never over a
     * choice the traveller already made themselves. A suggestion whose country we cannot price leaves
     * the currency alone rather than clearing it.
     */
    fun withSuggestionPicked(suggestion: DestinationSuggestionDto): PlannerUiState = copy(
        destination = suggestion.label.ifBlank { suggestion.name },
        resolved = suggestion,
        suggestions = emptyList(),
        suggestionsVisible = false,
        suggestionsLoading = false,
        currency = if (currencyTouched) currency else suggestion.currency ?: currency,
        error = null
    )

    /** The traveller chose a currency, which from now on outranks the destination's own. */
    fun withCurrencyChosen(code: String): PlannerUiState = copy(currency = code, currencyTouched = true)
}

@HiltViewModel
class PlannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val placesRepository: PlacesRepository
) : ViewModel() {

    private companion object {
        /**
         * How long the field waits after the last keystroke before asking for suggestions. Short
         * enough to feel immediate, long enough that typing a city name is one request and not eight.
         */
        const val SUGGEST_DEBOUNCE_MS = 250L
    }

    private val _uiState = MutableStateFlow(
        PlannerUiState(destination = savedStateHandle.toRoute<Planner>().destination.trim())
    )
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    private var suggestJob: Job? = null

    init {
        // A destination tapped on another screen arrives already typed, so look it up straight away
        // rather than waiting for the traveller to edit a field they did not fill in.
        val seeded = _uiState.value.destination
        if (seeded.length >= 2) requestSuggestions(seeded)
    }

    fun onDestinationChange(v: String) {
        _uiState.update { it.withDestinationTyped(v) }
        requestSuggestions(v)
    }

    private fun requestSuggestions(raw: String) {
        suggestJob?.cancel()
        val term = raw.trim()
        if (term.length < 2) {
            _uiState.update { it.copy(suggestions = emptyList(), suggestionsLoading = false) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            _uiState.update { it.copy(suggestionsLoading = true) }
            val results = placesRepository.autocompleteDestinations(term)
            // The traveller may have typed on while this was in flight. Applying a stale list would
            // offer places for a word no longer in the field.
            if (_uiState.value.destination.trim() == term) {
                _uiState.update { it.copy(suggestions = results, suggestionsLoading = false) }
            }
        }
    }

    /** Take a place from the suggestions. The rule lives on [PlannerUiState.withSuggestionPicked]. */
    fun pickSuggestion(suggestion: DestinationSuggestionDto) {
        suggestJob?.cancel()
        _uiState.update { it.withSuggestionPicked(suggestion) }
    }

    fun dismissSuggestions() = _uiState.update { it.copy(suggestionsVisible = false) }

    fun onBudgetChange(v: String) {
        if (v.all { c -> c.isDigit() || c == '.' || c == ',' }) {
            _uiState.update { it.copy(budget = v, error = null) }
        }
    }

    fun setStartDate(date: LocalDate) = _uiState.update {
        // A first day after the chosen last day would be rejected by the server; clear the last day
        // rather than quietly moving it.
        it.copy(startDate = date, endDate = if (it.endDate?.isBefore(date) == true) null else it.endDate)
    }
    fun setEndDate(date: LocalDate) = _uiState.update { it.copy(endDate = date) }
    fun incTravelers() = _uiState.update { it.copy(travelers = it.travelers + 1) }
    fun decTravelers() = _uiState.update { it.copy(travelers = (it.travelers - 1).coerceAtLeast(1)) }
    fun setCurrency(code: String) = _uiState.update { it.withCurrencyChosen(code) }
    fun setPace(pace: String) = _uiState.update { it.copy(pace = pace) }
    fun toggleInterest(word: String) = _uiState.update {
        it.copy(interests = if (word in it.interests) it.interests - word else it.interests + word)
    }

    fun submit() {
        val state = _uiState.value
        val currency = state.currency
        if (!state.canBuild || state.startDate == null || state.endDate == null || currency == null) return
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val created = tripRepository.createTrip(
                CreateTripDto(
                    destination = state.destination.trim(),
                    startDate = state.startDate.toString(),
                    endDate = state.endDate.toString(),
                    travelersCount = state.travelers,
                    budgetTotal = state.budgetValue,
                    currency = currency,
                    pace = state.pace,
                    interests = state.interests.toList()
                )
            )
            when (created) {
                is DataResult.Ok -> {
                    tripRepository.triggerGeneration(created.value.tripId)
                    _uiState.update { it.copy(submitting = false, createdTripId = created.value.tripId) }
                }
                is DataResult.Fail -> _uiState.update {
                    it.copy(submitting = false, error = created.error.message)
                }
            }
        }
    }
}
