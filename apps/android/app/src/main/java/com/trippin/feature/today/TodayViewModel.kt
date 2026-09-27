package com.trippin.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Today
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val details: TripDetailsDto? = null,
    val visited: Set<String> = emptySet(),
    val loading: Boolean = true,
    val refreshing: Boolean = false
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.toRoute<Today>().tripId

    private val _uiState = MutableStateFlow(TodayUiState())
    val uiState: StateFlow<TodayUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tripRepository.observeTripDetails(tripId).collect { details ->
                _uiState.update { it.copy(details = details ?: it.details, loading = it.loading && details == null) }
            }
        }
        viewModelScope.launch {
            tripRepository.observeVisitedIds().collect { ids -> _uiState.update { it.copy(visited = ids) } }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _uiState.update { it.copy(refreshing = !initial, loading = initial && it.details == null) }
        viewModelScope.launch {
            tripRepository.refreshTripDetails(tripId)
            _uiState.update { it.copy(loading = false, refreshing = false) }
        }
    }

    fun toggleVisited(activityId: String) {
        viewModelScope.launch { tripRepository.toggleVisited(activityId) }
    }
}
