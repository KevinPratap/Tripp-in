package com.trippin.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.core.repository.AuthRepository
import com.trippin.core.datastore.CurrentTripStore
import com.trippin.core.datastore.SignedInAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
    private val currentTripStore: CurrentTripStore
) : ViewModel() {

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
}
