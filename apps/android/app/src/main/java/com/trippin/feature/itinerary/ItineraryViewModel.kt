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
    /**
     * True from the moment Delete is confirmed until the grace window ends. The trip is not actually
     * gone yet: the network call is deliberately delayed, so this is the honest window in which Undo
     * can still work. See [ItineraryViewModel.delete].
     */
    val deletePending: Boolean = false,
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

    private var deleteJob: Job? = null

    /**
     * Starts the delete, but does not call the server yet.
     *
     * The delete this app can offer is not reversible: the API removes the row outright, with no
     * archive and no restore, so once that call is made there is nothing left to undo. Rather than
     * either skip undo entirely or claim a restore this API cannot do, the network call itself is
     * delayed by [UNDO_WINDOW_MS]. [undoDelete] during that window cancels the call before it ever
     * happens, which is the only honest kind of undo available here.
     */
    fun delete() {
        if (_uiState.value.busy || _uiState.value.deletePending) return
        _uiState.update { it.copy(deletePending = true) }
        deleteJob = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            when (tripRepository.delete(tripId)) {
                is DataResult.Ok -> _uiState.update { it.copy(deletePending = false, deleted = true) }
                is DataResult.Fail -> _uiState.update {
                    it.copy(
                        deletePending = false,
                        message = ActionMessage("Could not delete this trip", MessageTone.BAD)
                    )
                }
            }
        }
    }

    /** Cancels a pending delete before its grace window ends. A no-op once the window has passed. */
    fun undoDelete() {
        deleteJob?.cancel()
        deleteJob = null
        _uiState.update { it.copy(deletePending = false) }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    private companion object {
        /** How long Undo actually works for, before the delete call is made for real. */
        const val UNDO_WINDOW_MS = 5000L
    }
}
