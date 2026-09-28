package com.trippin.feature.triphub

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.GroupRepository
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.TripHub
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripHubUiState(
    val details: TripDetailsDto? = null,
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val loadError: String? = null,
    val sharing: Boolean = false,
    val shareUrl: String? = null,
    val message: String? = null
)

@HiltViewModel
class TripHubViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    val tripId: String = savedStateHandle.toRoute<TripHub>().tripId

    private val _uiState = MutableStateFlow(TripHubUiState())
    val uiState: StateFlow<TripHubUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            tripRepository.observeTripDetails(tripId).collect { details ->
                _uiState.update { it.copy(details = details ?: it.details, loading = it.loading && details == null) }
            }
        }
        refresh(initial = true)
    }

    fun refresh(initial: Boolean = false) {
        _uiState.update { it.copy(refreshing = !initial, loading = initial && it.details == null, loadError = null) }
        viewModelScope.launch {
            val result = tripRepository.refreshTripDetails(tripId)
            _uiState.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    loadError = if (result is DataResult.Fail && it.details == null) result.error.message else null
                )
            }
        }
    }

    /** Asks the server for a view-only link, or reuses the one the trip already has. */
    fun share() {
        val existing = _uiState.value.details?.trip?.shareToken
        if (existing != null) {
            _uiState.update { it.copy(shareUrl = shareUrlFor(existing)) }
            return
        }
        _uiState.update { it.copy(sharing = true) }
        viewModelScope.launch {
            when (val result = groupRepository.createShareLink(tripId)) {
                is DataResult.Ok -> _uiState.update { it.copy(sharing = false, shareUrl = result.value.url) }
                is DataResult.Fail -> _uiState.update { it.copy(sharing = false, message = "Could not make a share link. Try again when you are online.") }
            }
        }
    }

    fun consumeShareUrl() = _uiState.update { it.copy(shareUrl = null) }
    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

private const val SHARE_BASE = "https://web-production-a9ec6.up.railway.app/t/"

internal fun shareUrlFor(token: String): String = SHARE_BASE + token
