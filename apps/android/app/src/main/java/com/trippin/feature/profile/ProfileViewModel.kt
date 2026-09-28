package com.trippin.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.core.datastore.SessionManager
import com.trippin.core.datastore.SettingsManager
import com.trippin.core.datastore.SignedInAccount
import com.trippin.core.design.ThemeMode
import com.trippin.core.network.PlaceSearchResultDto
import com.trippin.core.network.SavedTripSummaryDto
import com.trippin.core.repository.AuthRepository
import com.trippin.core.repository.PlacesRepository
import com.trippin.core.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Counts taken straight from the trips on this account. Nothing is estimated. */
data class TravelStats(val trips: Int, val places: Int, val daysPlanned: Int)

fun travelStats(trips: List<SavedTripSummaryDto>): TravelStats = TravelStats(
    trips = trips.size,
    places = trips.map { it.destinationName.substringBefore(',').trim().lowercase() }.filter { it.isNotEmpty() }.distinct().size,
    daysPlanned = trips.sumOf { it.dayCount }
)

data class ProfileUiState(
    val account: SignedInAccount? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val savedSpots: List<PlaceSearchResultDto> = emptyList(),
    val stats: TravelStats = TravelStats(0, 0, 0)
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    authRepository: AuthRepository,
    private val placesRepository: PlacesRepository,
    private val settingsManager: SettingsManager,
    private val sessionManager: SessionManager,
    tripRepository: TripRepository
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
        viewModelScope.launch {
            tripRepository.observeMyTrips().collect { trips -> _uiState.update { it.copy(stats = travelStats(trips)) } }
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
