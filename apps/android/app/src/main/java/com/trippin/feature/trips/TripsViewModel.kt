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
    val error: String? = null
)

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
}
