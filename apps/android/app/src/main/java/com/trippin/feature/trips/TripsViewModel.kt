package com.trippin.feature.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.core.common.DataResult
import com.trippin.core.network.HomeFeedDto
import com.trippin.core.network.JoinTripRequestDto
import com.trippin.core.network.SavedTripSummaryDto
import com.trippin.core.repository.GroupRepository
import com.trippin.core.repository.HomeRepository
import com.trippin.core.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripsUiState(
    val myTrips: List<SavedTripSummaryDto> = emptyList(),
    val home: HomeFeedDto? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    /** A trip swiped away whose delete has not been sent yet. Hidden from the list until then. */
    val pendingDelete: SavedTripSummaryDto? = null,
    val message: String? = null
) {
    val visibleTrips: List<SavedTripSummaryDto> get() = myTrips.filter { it.id != pendingDelete?.id }
}

data class JoinUiState(
    val open: Boolean = false,
    val token: String = "",
    val name: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val joinedTripId: String? = null
) {
    val canJoin: Boolean get() = token.isNotBlank() && name.isNotBlank() && !loading
}

@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val homeRepository: HomeRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TripsUiState())
    val uiState: StateFlow<TripsUiState> = _uiState.asStateFlow()

    private val _joinState = MutableStateFlow(JoinUiState())
    val joinState: StateFlow<JoinUiState> = _joinState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                tripRepository.observeMyTrips(),
                homeRepository.observeHome()
            ) { trips, home -> trips to home }
                .collect { (trips, home) ->
                    _uiState.update { it.copy(myTrips = trips, home = home) }
                }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _uiState.update { it.copy(refreshing = !initial, loading = initial && it.myTrips.isEmpty()) }
        viewModelScope.launch {
            val trips = tripRepository.refreshMyTrips()
            val home = homeRepository.refreshHome()
            val firstError = listOf(trips, home)
                .filterIsInstance<DataResult.Fail>()
                .firstOrNull()?.error
            _uiState.update {
                it.copy(loading = false, refreshing = false, error = firstError?.message)
            }
        }
    }

    // ---- Join a trip by invite ----

    fun openJoin() = _joinState.update { JoinUiState(open = true) }
    fun closeJoin() = _joinState.update { JoinUiState(open = false) }
    fun onJoinTokenChange(v: String) = _joinState.update { it.copy(token = v, error = null) }
    fun onJoinNameChange(v: String) = _joinState.update { it.copy(name = v, error = null) }

    fun submitJoin() {
        val state = _joinState.value
        if (!state.canJoin) return
        _joinState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = groupRepository.joinTrip(
                JoinTripRequestDto(token = state.token.trim(), name = state.name.trim())
            )
            when (result) {
                is DataResult.Ok -> {
                    refresh()
                    _joinState.update { it.copy(loading = false, joinedTripId = result.value.tripId, open = false) }
                }
                is DataResult.Fail -> _joinState.update {
                    it.copy(loading = false, error = "Could not join that trip. Check the invite code.")
                }
            }
        }
    }

    fun consumeJoined() = _joinState.update { it.copy(joinedTripId = null) }

    // ---- Swipe to delete, with an undo that really undoes ----

    private var deleteJob: Job? = null

    /**
     * Hides the trip at once and only asks the server to delete it once the undo window has passed,
     * so Undo cancels a delete that never happened rather than pretending to restore one. A second
     * swipe during the window sends the first delete straight away.
     */
    fun delete(trip: SavedTripSummaryDto) {
        _uiState.value.pendingDelete?.let { previous ->
            deleteJob?.cancel()
            sendDelete(previous)
        }
        _uiState.update { it.copy(pendingDelete = trip) }
        deleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            _uiState.update { it.copy(pendingDelete = null) }
            sendDelete(trip)
        }
    }

    fun undoDelete() {
        deleteJob?.cancel()
        deleteJob = null
        _uiState.update { it.copy(pendingDelete = null) }
    }

    private fun sendDelete(trip: SavedTripSummaryDto) {
        viewModelScope.launch {
            when (tripRepository.delete(trip.id)) {
                is DataResult.Ok -> tripRepository.refreshMyTrips()
                is DataResult.Fail -> _uiState.update {
                    it.copy(message = "Could not delete ${trip.destinationName.substringBefore(',')}. It is back in the list.")
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private companion object {
        const val UNDO_WINDOW_MS = 5000L
    }
}
