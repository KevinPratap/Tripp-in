package com.trippin.feature.itinerary

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Plan
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class MessageTone { GOOD, WORKING, BAD }
data class ActionMessage(val text: String, val tone: MessageTone)

data class ItineraryUiState(
    val details: TripDetailsDto? = null,
    val visited: Set<String> = emptySet(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadError: String? = null,
    val busy: Boolean = false,
    val message: ActionMessage? = null,
    val buildDismissed: Boolean = false,
    val generationProgress: Int? = null,
    val generationStage: String? = null,
    val deleted: Boolean = false
) {
    val status: String? get() = details?.trip?.status
    val isBuilding: Boolean get() = status == "GENERATING"
    val buildFailed: Boolean get() = status == "FAILED"
    val days get() = details?.itinerary?.days.orEmpty()
    val isLocked: Boolean get() = details?.trip?.isLocked == true
    val showBuildState: Boolean get() = days.isEmpty() && !buildDismissed && (isBuilding || buildFailed)
}

@HiltViewModel
class ItineraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.toRoute<Plan>().tripId

    private val _uiState = MutableStateFlow(ItineraryUiState())
    val uiState: StateFlow<ItineraryUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            tripRepository.observeTripDetails(tripId).collect { details ->
                _uiState.update {
                    it.copy(details = details ?: it.details, loading = it.loading && details == null)
                }
                manageGenerationPolling()
            }
        }
        viewModelScope.launch {
            tripRepository.observeVisitedIds().collect { ids ->
                _uiState.update { it.copy(visited = ids) }
            }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _uiState.update { it.copy(refreshing = !initial, loading = initial && it.details == null, loadError = null) }
        viewModelScope.launch {
            when (val result = tripRepository.refreshTripDetails(tripId)) {
                is DataResult.Ok -> _uiState.update { it.copy(loading = false, refreshing = false) }
                is DataResult.Fail -> _uiState.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        loadError = if (it.details == null) result.error.message else null
                    )
                }
            }
        }
    }

    private fun manageGenerationPolling() {
        val building = _uiState.value.isBuilding
        if (building && pollingJob?.isActive != true) {
            pollingJob = viewModelScope.launch {
                while (isActive && _uiState.value.isBuilding) {
                    delay(2500)
                    when (val status = tripRepository.getStatus(tripId)) {
                        is DataResult.Ok -> {
                            _uiState.update {
                                it.copy(
                                    generationProgress = status.value.progressPercentage,
                                    generationStage = status.value.currentStepMessage
                                )
                            }
                            if (status.value.status != "GENERATING") {
                                refresh()
                                break
                            }
                        }
                        is DataResult.Fail -> Unit // keep polling; a blip is not a failed build
                    }
                }
            }
        } else if (!building) {
            pollingJob?.cancel()
            pollingJob = null
        }
    }

    fun dismissBuildState() {
        _uiState.update { it.copy(buildDismissed = true) }
        refresh()
    }

    fun toggleVisited(activityId: String) {
        viewModelScope.launch { tripRepository.toggleVisited(activityId) }
    }

    fun toggleLock() {
        if (_uiState.value.busy) return
        val target = !_uiState.value.isLocked
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (tripRepository.setLocked(tripId, target)) {
                is DataResult.Ok -> { tripRepository.refreshTripDetails(tripId); _uiState.update { it.copy(busy = false) } }
                is DataResult.Fail -> _uiState.update {
                    it.copy(busy = false, message = ActionMessage("Could not change the lock", MessageTone.BAD))
                }
            }
        }
    }

    fun replan(intent: String) {
        if (_uiState.value.isLocked || _uiState.value.busy) return
        _uiState.update { it.copy(busy = true, message = ActionMessage("Replanning...", MessageTone.WORKING)) }
        viewModelScope.launch {
            when (tripRepository.replan(tripId, intent)) {
                is DataResult.Ok -> {
                    tripRepository.refreshTripDetails(tripId)
                    _uiState.update { it.copy(busy = false, message = ActionMessage("Plan updated.", MessageTone.GOOD)) }
                }
                is DataResult.Fail -> _uiState.update {
                    it.copy(busy = false, message = ActionMessage("Could not replan", MessageTone.BAD))
                }
            }
        }
    }

    fun modify(instruction: String, onDone: (Boolean) -> Unit) {
        val itineraryId = _uiState.value.details?.itinerary?.id
        if (instruction.isBlank() || itineraryId == null || _uiState.value.busy) {
            onDone(false); return
        }
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (tripRepository.modify(itineraryId, instruction.trim())) {
                is DataResult.Ok -> {
                    tripRepository.refreshTripDetails(tripId)
                    _uiState.update { it.copy(busy = false, message = ActionMessage("Plan updated.", MessageTone.GOOD)) }
                    onDone(true)
                }
                is DataResult.Fail -> {
                    _uiState.update { it.copy(busy = false, message = ActionMessage("Could not apply that change", MessageTone.BAD)) }
                    onDone(false)
                }
            }
        }
    }

    fun delete() {
        if (_uiState.value.busy) return
        _uiState.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (tripRepository.delete(tripId)) {
                is DataResult.Ok -> _uiState.update { it.copy(busy = false, deleted = true) }
                is DataResult.Fail -> _uiState.update {
                    it.copy(busy = false, message = ActionMessage("Could not delete this trip", MessageTone.BAD))
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
