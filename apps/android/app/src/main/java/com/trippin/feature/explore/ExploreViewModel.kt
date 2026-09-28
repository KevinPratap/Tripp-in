package com.trippin.feature.explore

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.map.PlaceKind
import com.trippin.core.network.ExplorePlaceDto
import com.trippin.core.network.GeoPointDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.ExploreRepository
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Explore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SwapState { IDLE, WORKING, DONE, FAILED }

data class ExploreUiState(
    val center: GeoPointDto,
    val centerTitle: String,
    val aroundMe: Boolean = false,
    val me: GeoPointDto? = null,
    val places: List<ExplorePlaceDto> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
    val radius: Int = 800,
    val kind: PlaceKind? = null,
    val selectedId: String? = null,
    val details: TripDetailsDto? = null,
    val swap: SwapState = SwapState.IDLE,
    val locating: Boolean = false,
    val locateFailed: Boolean = false
) {
    val shown: List<ExplorePlaceDto> get() = places.filter { kind == null || PlaceKind.of(it.kind) == kind }
    val kinds: List<PlaceKind> get() = places.map { PlaceKind.of(it.kind) }.distinct().sortedBy { it.ordinal }
    val selected: ExplorePlaceDto? get() = places.firstOrNull { it.id == selectedId }
}

/** The plan instruction for swapping a stop for a place found nearby. Goes through the same checks as any edit. */
internal fun swapInstruction(stopTitle: String, place: ExplorePlaceDto): String {
    val kind = place.cuisine?.let { "$it ${PlaceKind.of(place.kind).label.lowercase()}" } ?: PlaceKind.of(place.kind).label.lowercase()
    return "Replace the stop \"$stopTitle\" with \"${place.name}\" ($kind, OpenStreetMap ${place.id}, at " +
        "${place.latitude}, ${place.longitude}). Keep the same time slot and leave every other stop as it is."
}

@HiltViewModel
class ExploreViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val exploreRepository: ExploreRepository,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Explore>()
    val canSwap: Boolean = route.tripId != null && route.activityId != null

    private val _uiState = MutableStateFlow(
        ExploreUiState(center = GeoPointDto(route.lat, route.lng), centerTitle = route.title)
    )
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    init {
        load()
        route.tripId?.let { tripId ->
            viewModelScope.launch {
                tripRepository.observeTripDetails(tripId).collect { d -> _uiState.update { it.copy(details = d ?: it.details) } }
            }
        }
    }

    fun load() {
        val s = _uiState.value
        _uiState.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            when (val r = exploreRepository.nearby(s.center, s.radius)) {
                is DataResult.Ok -> _uiState.update {
                    it.copy(places = r.value.places, loading = false, kind = it.kind?.takeIf { k -> r.value.places.any { p -> PlaceKind.of(p.kind) == k } })
                }
                is DataResult.Fail -> _uiState.update { it.copy(loading = false, failed = true) }
            }
        }
    }

    fun setKind(kind: PlaceKind?) = _uiState.update { it.copy(kind = if (it.kind == kind) null else kind, selectedId = null) }

    fun select(id: String?) = _uiState.update { it.copy(selectedId = if (it.selectedId == id) null else id) }

    fun widen() {
        if (_uiState.value.radius >= 2000) return
        _uiState.update { it.copy(radius = 2000) }
        load()
    }

    fun locating() = _uiState.update { it.copy(locating = true, locateFailed = false) }

    fun useLocation(point: GeoPointDto?) {
        if (point == null) {
            _uiState.update { it.copy(locating = false, locateFailed = true) }
            return
        }
        _uiState.update { it.copy(center = point, me = point, aroundMe = true, centerTitle = "you", locating = false, selectedId = null) }
        load()
    }

    fun swapIn(place: ExplorePlaceDto) {
        val tripId = route.tripId ?: return
        val activityId = route.activityId ?: return
        val details = _uiState.value.details ?: return
        val itineraryId = details.itinerary?.id ?: return
        val stop = details.itinerary.days.flatMap { it.activities }.firstOrNull { it.id == activityId } ?: return
        if (_uiState.value.swap == SwapState.WORKING) return
        _uiState.update { it.copy(swap = SwapState.WORKING) }
        viewModelScope.launch {
            when (tripRepository.modify(itineraryId, swapInstruction(stop.title, place))) {
                is DataResult.Ok -> {
                    tripRepository.refreshTripDetails(tripId)
                    _uiState.update { it.copy(swap = SwapState.DONE) }
                }
                is DataResult.Fail -> _uiState.update { it.copy(swap = SwapState.FAILED) }
            }
        }
    }

    fun clearSwap() = _uiState.update { it.copy(swap = SwapState.IDLE) }
}
