package com.trippin.feature.planner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.design.parseStatedAmount
import com.trippin.core.network.CreateTripDto
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Planner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PlannerUiState(
    val destination: String = "",
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val travelers: Int = 2,
    val currency: String = "INR",
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

    val budgetProblem: String? get() = when {
        budget.isBlank() -> null
        budgetValue == null -> "Enter your budget as a number, or leave it blank."
        budgetValue!! <= 0.0 -> "A budget has to be more than zero."
        else -> null
    }

    /** The first thing still missing, in the order the form asks for it. */
    val guidance: String? get() = destinationProblem ?: dateProblem ?: budgetProblem
    val canBuild: Boolean get() = guidance == null && !submitting
}

@HiltViewModel
class PlannerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlannerUiState(destination = savedStateHandle.toRoute<Planner>().destination.trim())
    )
    val uiState: StateFlow<PlannerUiState> = _uiState.asStateFlow()

    fun onDestinationChange(v: String) = _uiState.update { it.copy(destination = v, error = null) }
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
    fun setCurrency(code: String) = _uiState.update { it.copy(currency = code) }
    fun setPace(pace: String) = _uiState.update { it.copy(pace = pace) }
    fun toggleInterest(word: String) = _uiState.update {
        it.copy(interests = if (word in it.interests) it.interests - word else it.interests + word)
    }

    fun submit() {
        val state = _uiState.value
        if (!state.canBuild || state.startDate == null || state.endDate == null) return
        _uiState.update { it.copy(submitting = true, error = null) }
        viewModelScope.launch {
            val created = tripRepository.createTrip(
                CreateTripDto(
                    destination = state.destination.trim(),
                    startDate = state.startDate.toString(),
                    endDate = state.endDate.toString(),
                    travelersCount = state.travelers,
                    budgetTotal = state.budgetValue,
                    currency = state.currency,
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
