package com.trippin.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.MapView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MapUiState(
    val details: TripDetailsDto? = null,
    val loading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.toRoute<MapView>().tripId

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tripRepository.observeTripDetails(tripId).collect { details ->
                _uiState.update { it.copy(details = details ?: it.details, loading = it.loading && details == null) }
            }
        }
        viewModelScope.launch {
            when (val r = tripRepository.refreshTripDetails(tripId)) {
                is com.trippin.core.common.DataResult.Ok -> _uiState.update { it.copy(loading = false) }
                is com.trippin.core.common.DataResult.Fail -> _uiState.update {
                    it.copy(loading = false, error = if (it.details == null) r.error.message else null)
                }
            }
        }
    }
}
