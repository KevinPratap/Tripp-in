package com.trippin.feature.budget

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.network.ExpenseOverviewDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.ExpenseRepository
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Budget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(
    val overview: ExpenseOverviewDto? = null,
    val details: TripDetailsDto? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadError: String? = null,
    val formOpen: Boolean = false,
    val draft: ExpenseDraft = ExpenseDraft(),
    val formError: String? = null,
    val saving: Boolean = false,
    val message: String? = null
) {
    /** The trip's own currency, which follows the destination. */
    val currency: String? get() = details?.trip?.currency?.takeIf { it.isNotBlank() } ?: overview?.currency?.takeIf { it.isNotBlank() && it != "UNSPECIFIED" }
    val payers: List<String> get() = payersFor(details)
    val estimate: Double? get() = planEstimate(details, currency)
}

@HiltViewModel
class BudgetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val expenseRepository: ExpenseRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.toRoute<Budget>().tripId

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            expenseRepository.observe(tripId).collect { overview ->
                _uiState.update { it.copy(overview = overview ?: it.overview, loading = it.loading && overview == null) }
            }
        }
        viewModelScope.launch {
            tripRepository.observeTripDetails(tripId).collect { details ->
                _uiState.update { it.copy(details = details ?: it.details) }
            }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _uiState.update { it.copy(refreshing = !initial, loadError = null) }
        viewModelScope.launch {
            val result = expenseRepository.refresh(tripId)
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    loadError = if (result is DataResult.Fail && it.overview == null) result.error.message else null
                )
            }
        }
    }

    fun openForm() = _uiState.update {
        val payers = it.payers
        it.copy(
            formOpen = true,
            formError = null,
            draft = ExpenseDraft(paidBy = payers.first(), splitBetween = payers.toSet())
        )
    }

    fun closeForm() = _uiState.update { it.copy(formOpen = false, formError = null) }

    fun editDraft(transform: (ExpenseDraft) -> ExpenseDraft) =
        _uiState.update { it.copy(draft = transform(it.draft), formError = null) }

    fun save() {
        val state = _uiState.value
        when (val check = checkDraft(state.draft, state.currency)) {
            is DraftCheck.Problem -> _uiState.update { it.copy(formError = check.message) }
            is DraftCheck.Ok -> {
                _uiState.update { it.copy(saving = true, formError = null) }
                viewModelScope.launch {
                    when (val result = expenseRepository.add(tripId, check.request)) {
                        is DataResult.Ok -> _uiState.update { it.copy(saving = false, formOpen = false, message = "Logged ${check.request.title}") }
                        is DataResult.Fail -> _uiState.update { it.copy(saving = false, formError = result.error.message) }
                    }
                }
            }
        }
    }

    fun delete(expenseId: String) {
        viewModelScope.launch {
            when (val result = expenseRepository.delete(tripId, expenseId)) {
                is DataResult.Ok -> _uiState.update { it.copy(message = "Removed") }
                is DataResult.Fail -> _uiState.update { it.copy(message = result.error.message) }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
