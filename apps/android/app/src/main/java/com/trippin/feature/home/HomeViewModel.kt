package com.trippin.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trippin.core.common.DataResult
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.SavedTripSummaryDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.repository.TripRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val now: LocalDateTime = LocalDateTime.now(),
    val featured: Featured? = null,
    /** The featured trip's stop happening now and the next one, when the trip is live today. */
    val nowStop: ActivityDto? = null,
    val nextStop: ActivityDto? = null,
    /** True once the live trip's plan is loaded, so "nothing left today" is a fact, not a guess. */
    val todayKnown: Boolean = false,
    val upcoming: List<SavedTripSummaryDto> = emptyList(),
    val hasTrips: Boolean = false,
    val error: String? = null
)

private data class LoadState(val loading: Boolean = true, val refreshing: Boolean = false, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val tripRepository: TripRepository
) : ViewModel() {

    private val load = MutableStateFlow(LoadState())

    /** Minute ticks, so "now" moves on while the screen is open. */
    private val clock = flow {
        while (true) {
            emit(LocalDateTime.now())
            delay(60_000)
        }
    }

    private val trips = tripRepository.observeMyTrips()

    private val featured = combine(trips, clock.map { it.toLocalDate() }.distinctUntilChanged()) { list, day ->
        pickFeatured(list, day)
    }

    private val liveDetails = featured
        .map { (it as? Featured.Live)?.trip?.id }
        .distinctUntilChanged()
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else tripRepository.observeTripDetails(id)
        }

    val uiState: StateFlow<HomeUiState> = combine(trips, featured, liveDetails, clock, load) { list, feat, details, now, ld ->
        val today = now.toLocalDate()
        val todays = (feat as? Featured.Live)?.let { todaysStops(details, today) }
        val pair = todays?.let { nowAndNext(it, now.toLocalTime()) }
        HomeUiState(
            loading = ld.loading && list.isEmpty(),
            refreshing = ld.refreshing,
            now = now,
            featured = feat,
            nowStop = pair?.now,
            nextStop = pair?.next,
            todayKnown = todays != null,
            upcoming = upcomingAfter(list, feat?.trip?.id, today).take(4),
            hasTrips = list.isNotEmpty(),
            error = ld.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        refresh(initial = true)
        viewModelScope.launch {
            liveTripId().collect { id -> if (id != null) tripRepository.refreshTripDetails(id) }
        }
    }

    private fun liveTripId() = featured.map { (it as? Featured.Live)?.trip?.id }.distinctUntilChanged()

    fun refresh(initial: Boolean = false) {
        load.update { it.copy(loading = initial, refreshing = !initial, error = null) }
        viewModelScope.launch {
            val result = tripRepository.refreshMyTrips()
            val live = uiState.value.featured as? Featured.Live
            if (!initial && live != null) tripRepository.refreshTripDetails(live.trip.id)
            load.update {
                LoadState(loading = false, refreshing = false, error = (result as? DataResult.Fail)?.error?.message)
            }
        }
    }

    private fun todaysStops(details: TripDetailsDto?, today: LocalDate): List<ActivityDto>? {
        val days = details?.itinerary?.days ?: return null
        return days.firstOrNull { parseIsoDate(it.date) == today }?.activities ?: emptyList()
    }
}
