package com.trippin.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.core.common.DataResult
import com.trippin.core.repository.AuthRepository
import com.trippin.core.datastore.CurrentTripStore
import com.trippin.core.datastore.SettingsManager
import com.trippin.core.datastore.SignedInAccount
import com.trippin.core.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Whether the app is still deciding, signed out, or signed in as someone. */
sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val account: SignedInAccount) : AuthState
}

@HiltViewModel
class ShellViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val currentTripStore: CurrentTripStore,
    private val tripRepository: TripRepository,
    private val settingsManager: SettingsManager
) : ViewModel() {

    /** Null until the setting is read, so the intro never flashes for someone who has seen it. */
    val introSeen: StateFlow<Boolean?> = settingsManager.introSeen
        .map<Boolean, Boolean?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun finishIntro() {
        viewModelScope.launch { settingsManager.markIntroSeen() }
    }

    val authState: StateFlow<AuthState> = authRepository.account
        .map { if (it != null) AuthState.SignedIn(it) else AuthState.SignedOut }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AuthState.Loading)

    val currentTripId: StateFlow<String?> = currentTripStore.tripId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Re-check a stored token. A dead token clears itself and the shell falls back to sign-in. */
    fun revalidate() {
        viewModelScope.launch { authRepository.validateSession() }
    }

    fun openTrip(tripId: String) {
        viewModelScope.launch { currentTripStore.set(tripId) }
    }

    private val _resolvedShareTripId = MutableStateFlow<String?>(null)

    /** Set once a shared link's token resolves to a trip, so the shell can navigate to it and clear this. */
    val resolvedShareTripId: StateFlow<String?> = _resolvedShareTripId.asStateFlow()

    /**
     * Resolves a shared trip link's token and opens it, the same way tapping a trip on the Trips tab
     * does. A token that does not resolve (expired, revoked, never existed) is dropped silently:
     * there is no dedicated place on the shell to explain a dead link, and landing on the ordinary
     * Trips tab is a safe fallback rather than a dead end.
     */
    fun openSharedTrip(token: String) {
        viewModelScope.launch {
            val result = tripRepository.resolveShareToken(token)
            if (result is DataResult.Ok) {
                val tripId = result.value.trip.id
                openTrip(tripId)
                _resolvedShareTripId.value = tripId
            }
        }
    }

    fun clearResolvedShareTrip() {
        _resolvedShareTripId.value = null
    }
}
