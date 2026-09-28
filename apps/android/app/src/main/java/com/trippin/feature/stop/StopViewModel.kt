package com.trippin.feature.stop

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.trippin.core.common.DataResult
import com.trippin.core.network.ActivityCollabDto
import com.trippin.core.network.ActivityDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.network.VoteRequestDto
import com.trippin.core.repository.AuthRepository
import com.trippin.core.repository.GroupRepository
import com.trippin.core.repository.TripRepository
import com.trippin.navigation.Stop
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where one stop sits in its trip: the stop itself, its day, and its place in that day. */
data class StopPlacement(
    val activity: ActivityDto,
    val dayIndex: Int,
    val dayDate: String,
    val position: Int,
    val stopsThatDay: Int,
    val destinationName: String?
)

fun locateStop(details: TripDetailsDto?, activityId: String): StopPlacement? {
    val days = details?.itinerary?.days ?: return null
    for (day in days) {
        val i = day.activities.indexOfFirst { it.id == activityId }
        if (i >= 0) {
            return StopPlacement(
                activity = day.activities[i],
                dayIndex = day.dayIndex,
                dayDate = day.date,
                position = i + 1,
                stopsThatDay = day.activities.size,
                destinationName = details.trip.destination.takeIf { it.isNotBlank() }
            )
        }
    }
    return null
}

data class StopUiState(
    val loading: Boolean = true,
    val placement: StopPlacement? = null,
    val visited: Boolean = false,
    /** The group's votes on this stop, once loaded. Null while loading or when offline. */
    val votes: ActivityCollabDto? = null,
    val votesLoaded: Boolean = false,
    /** The name this phone votes under. */
    val voterName: String? = null,
    val voting: Boolean = false,
    val voteError: String? = null
) {
    val myVote: Int? get() = voterName?.let { votes?.voters?.get(it) }
}

/**
 * Reads the stop from the trip already on the phone. The plan screen that led here has just loaded
 * it, so there is nothing to fetch; a stop that is no longer in the plan (it was replanned away)
 * shows as gone rather than as a stale copy.
 */
@HiltViewModel
class StopViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val groupRepository: GroupRepository,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val route = savedStateHandle.toRoute<Stop>()

    private data class VoteState(
        val votes: ActivityCollabDto? = null,
        val loaded: Boolean = false,
        val voterName: String? = null,
        val voting: Boolean = false,
        val error: String? = null
    )

    private val voteState = MutableStateFlow(VoteState())

    val uiState: StateFlow<StopUiState> = combine(
        tripRepository.observeTripDetails(route.tripId),
        tripRepository.observeVisitedIds(),
        voteState
    ) { details, visited, v ->
        StopUiState(
            loading = false,
            placement = locateStop(details, route.activityId),
            visited = route.activityId in visited,
            votes = v.votes,
            votesLoaded = v.loaded,
            voterName = v.voterName,
            voting = v.voting,
            voteError = v.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StopUiState())

    init {
        viewModelScope.launch {
            val account = authRepository.account.first()
            val name = account?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: account?.email?.substringBefore('@')?.takeIf { it.isNotEmpty() }
            voteState.update { it.copy(voterName = name) }
            loadVotes()
        }
    }

    private suspend fun loadVotes() {
        when (val result = groupRepository.getCollab(route.tripId)) {
            is DataResult.Ok -> voteState.update {
                it.copy(votes = result.value.activities[route.activityId], loaded = true, error = null)
            }
            is DataResult.Fail -> voteState.update { it.copy(loaded = true, error = null) }
        }
    }

    fun toggleVisited() {
        viewModelScope.launch { tripRepository.toggleVisited(route.activityId) }
    }

    /** +1 keeps the stop, -1 would skip it. The server keeps one vote per name, so voting again changes it. */
    fun vote(value: Int) {
        val name = voteState.value.voterName ?: return
        voteState.update { it.copy(voting = true, error = null) }
        viewModelScope.launch {
            when (val result = groupRepository.vote(route.tripId, VoteRequestDto(activityId = route.activityId, voterName = name, vote = value))) {
                is DataResult.Ok -> voteState.update {
                    it.copy(votes = result.value.activities[route.activityId], voting = false)
                }
                is DataResult.Fail -> voteState.update { it.copy(voting = false, error = "Your vote did not go through. Try again when you are online.") }
            }
        }
    }
}
