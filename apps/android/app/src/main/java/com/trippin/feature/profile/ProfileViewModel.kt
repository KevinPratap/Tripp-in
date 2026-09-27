package com.trippin.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.core.datastore.SessionManager
import com.trippin.core.datastore.SettingsManager
import com.trippin.core.datastore.SignedInAccount
import com.trippin.core.design.ThemeMode
import com.trippin.core.network.PlaceSearchResultDto
import com.trippin.core.repository.AuthRepository
import com.trippin.core.repository.PlacesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val account: SignedInAccount? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val savedSpots: List<PlaceSearchResultDto> = emptyList()
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val placesRepository: PlacesRepository,
    private val settingsManager: SettingsManager,
    private val sessionManager: SessionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.account.collect { acc -> _uiState.update { it.copy(account = acc) } }
        }
        viewModelScope.launch {
            settingsManager.themeMode.collect { mode -> _uiState.update { it.copy(themeMode = mode) } }
        }
        viewModelScope.launch {
            placesRepository.observeSavedSpots().collect { spots -> _uiState.update { it.copy(savedSpots = spots) } }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { settingsManager.setThemeMode(mode) }
    }

    fun removeSaved(place: PlaceSearchResultDto) {
        viewModelScope.launch { placesRepository.toggleSaved(place) }
    }

    fun signOut() {
        viewModelScope.launch { sessionManager.clear() }
    }
}
