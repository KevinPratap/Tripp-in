package com.trippin.feature.stop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Stop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where one stop sits in its trip: the stop itself, its day, and its place in that day. */
data class StopPlacement(
    val activity: ActivityDto,
    val dayIndex: Int,
    val dayDate: String,
    val position: Int,
    val stopsThatDay: Int,
    val destinationName: String?
)

fun locateStop(details: TripDetailsDto?, activityId: String): StopPlacement? {
    val days = details?.itinerary?.days ?: return null
    for (day in days) {
        val i = day.activities.indexOfFirst { it.id == activityId }
        if (i >= 0) {
            return StopPlacement(
                activity = day.activities[i],
                dayIndex = day.dayIndex,
                dayDate = day.date,
                position = i + 1,
                stopsThatDay = day.activities.size,
                destinationName = details.trip.destination.takeIf { it.isNotBlank() }
            )
        }
    }
    return null
}

data class StopUiState(
    val loading: Boolean = true,
    val placement: StopPlacement? = null,
    val visited: Boolean = false
)

/**
 * Reads the stop from the trip already on the phone. The plan screen that led here has just loaded
 * it, so there is nothing to fetch; a stop that is no longer in the plan (it was replanned away)
 * shows as gone rather than as a stale copy.
 */
@HiltViewModel
class StopViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Stop>()

    val uiState: StateFlow<StopUiState> = combine(
        tripRepository.observeTripDetails(route.tripId),
        tripRepository.observeVisitedIds()
    ) { details, visited ->
        StopUiState(
            loading = false,
            placement = locateStop(details, route.activityId),
            visited = route.activityId in visited
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopUiState())

    fun toggleVisited() {
        viewModelScope.launch { tripRepository.toggleVisited(route.activityId) }
    }
}
