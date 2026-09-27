package com.trippin.feature.group

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.network.CreateTravellerRequestDto
import com.trippin.core.network.TravellerDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.network.UpdateTravellerRequestDto
import com.trippin.core.repository.GroupRepository
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Group
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupUiState(
    val travellers: List<TravellerDto> = emptyList(),
    val details: TripDetailsDto? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val shareUrl: String? = null
) {
    val currency: String? get() = details?.trip?.currency
    val perTravellerCost get() = details?.trip?.perTravellerCost.orEmpty()
    val options get() = details?.trip?.options.orEmpty()
}

@HiltViewModel
class GroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val tripRepository: TripRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.toRoute<Group>().tripId

    private val _uiState = MutableStateFlow(GroupUiState())
    val uiState: StateFlow<GroupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tripRepository.observeTripDetails(tripId).collect { details ->
                _uiState.update { it.copy(details = details ?: it.details) }
            }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _uiState.update { it.copy(refreshing = !initial, loading = initial && it.travellers.isEmpty()) }
        viewModelScope.launch {
            tripRepository.refreshTripDetails(tripId)
            when (val result = groupRepository.getTravellers(tripId)) {
                is DataResult.Ok -> _uiState.update { it.copy(loading = false, refreshing = false, travellers = result.value, error = null) }
                is DataResult.Fail -> _uiState.update {
                    it.copy(loading = false, refreshing = false, error = if (it.travellers.isEmpty()) result.error.message else null)
                }
            }
        }
    }

    fun addTraveller(name: String, cap: Double?, interests: List<String>, dislikes: List<String>, pace: String?) {
        if (name.isBlank() || _uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = groupRepository.addTraveller(
                tripId,
                CreateTravellerRequestDto(name.trim(), cap, interests, dislikes, pace)
            )
            finishMutation(result, "Added ${name.trim()}")
        }
    }

    fun updateTraveller(id: String, name: String?, cap: Double?, interests: List<String>?, dislikes: List<String>?, pace: String?) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            val result = groupRepository.updateTraveller(
                tripId, id, UpdateTravellerRequestDto(name?.trim(), cap, interests, dislikes, pace)
            )
            finishMutation(result, "Saved")
        }
    }

    fun deleteTraveller(id: String) {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            finishMutation(groupRepository.deleteTraveller(tripId, id), "Removed")
        }
    }

    private fun <T> finishMutation(result: DataResult<T>, successMessage: String) {
        when (result) {
            is DataResult.Ok -> { _uiState.update { it.copy(busy = false, message = successMessage) }; refresh() }
            is DataResult.Fail -> _uiState.update { it.copy(busy = false, message = result.error.message) }
        }
    }

    fun createShareLink() {
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = groupRepository.createShareLink(tripId)) {
                is DataResult.Ok -> _uiState.update { it.copy(busy = false, shareUrl = result.value.url) }
                is DataResult.Fail -> _uiState.update { it.copy(busy = false, message = "Could not create an invite link") }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
    fun consumeShareUrl() = _uiState.update { it.copy(shareUrl = null) }
}
