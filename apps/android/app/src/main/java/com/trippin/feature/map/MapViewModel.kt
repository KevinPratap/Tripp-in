package com.trippin.feature.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.ExploreRouteDto
import com.trippin.core.network.GeoPointDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.ExploreRepository
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
    val error: String? = null,
    val selectedDay: Int = 0,
    /** Routes already fetched, by day position. */
    val routes: Map<Int, ExploreRouteDto> = emptyMap(),
    val routeLoading: Boolean = false,
    val routeFailed: Boolean = false
) {
    val days get() = details?.itinerary?.days.orEmpty()
    val route: ExploreRouteDto? get() = routes[selectedDay]
}

internal fun GeoPointDto?.isReal(): Boolean = this != null && (latitude != 0.0 || longitude != 0.0)

internal fun ActivityDto.point(): GeoPointDto? = place?.location?.takeIf { it.isReal() }

@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val exploreRepository: ExploreRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.toRoute<MapView>().tripId

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tripRepository.observeTripDetails(tripId).collect { details ->
                val hadDays = _uiState.value.days.isNotEmpty()
                _uiState.update { it.copy(details = details ?: it.details, loading = it.loading && details == null) }
                if (!hadDays && _uiState.value.days.isNotEmpty()) loadRoute()
            }
        }
        viewModelScope.launch {
            when (val r = tripRepository.refreshTripDetails(tripId)) {
                is DataResult.Ok -> _uiState.update { it.copy(loading = false) }
                is DataResult.Fail -> _uiState.update {
                    it.copy(loading = false, error = if (it.details == null) r.error.message else null)
                }
            }
        }
    }

    fun selectDay(index: Int) {
        _uiState.update { it.copy(selectedDay = index, routeFailed = false) }
        loadRoute()
    }

    fun retryRoute() = loadRoute()

    private fun loadRoute() {
        val state = _uiState.value
        val day = state.selectedDay
        if (state.routes.containsKey(day)) return
        val points = state.days.getOrNull(day)?.activities?.mapNotNull { it.point() }.orEmpty()
        if (points.size < 2) return
        _uiState.update { it.copy(routeLoading = true, routeFailed = false) }
        viewModelScope.launch {
            when (val r = exploreRepository.route(points.take(12))) {
                is DataResult.Ok -> _uiState.update { it.copy(routes = it.routes + (day to r.value), routeLoading = false) }
                is DataResult.Fail -> _uiState.update { it.copy(routeLoading = false, routeFailed = true) }
            }
        }
    }
}
