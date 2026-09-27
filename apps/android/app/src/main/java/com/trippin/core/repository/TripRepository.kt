package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.runNetwork
import com.trippin.core.database.CachedJsonDao
import com.trippin.core.database.CachedJsonEntity
import com.trippin.core.database.VisitedStopDao
import com.trippin.core.database.VisitedStopEntity
import com.trippin.core.network.ApiService
import com.trippin.core.network.CreateTripDto
import com.trippin.core.network.CreateTripResponseDto
import com.trippin.core.network.GenerateTripResponseDto
import com.trippin.core.network.LockResponseDto
import com.trippin.core.network.ModifyItineraryRequestDto
import com.trippin.core.network.ModifyItineraryResponseDto
import com.trippin.core.network.ReplanRequestDto
import com.trippin.core.network.ReplanResponseDto
import com.trippin.core.network.SavedTripSummaryDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.network.TripStatusResponseDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Trips, offline-first. A screen observes the Room cache and separately asks for a refresh, so a plan
 * already on the phone is shown instantly and with no network, then quietly replaced when the server
 * answers. Every network call returns a [DataResult] whose failure is already a readable message.
 */
@Singleton
class TripRepository @Inject constructor(
    private val api: ApiService,
    private val cache: CachedJsonDao,
    private val visitedDao: VisitedStopDao,
    private val json: Json
) {
    private companion object {
        const val MY_TRIPS_KEY = "me:trips"
        fun tripKey(id: String) = "trip:$id"
    }

    // ---- Trip details (offline-first) ----

    fun observeTripDetails(tripId: String): Flow<TripDetailsDto?> =
        cache.observe(tripKey(tripId)).map { row ->
            row?.json?.let { text ->
                runCatching { json.decodeFromString(TripDetailsDto.serializer(), text) }.getOrNull()
            }
        }

    suspend fun refreshTripDetails(tripId: String): DataResult<TripDetailsDto> {
        val result = runNetwork { api.getTripDetails(tripId) }
        if (result is DataResult.Ok) {
            cache.put(
                CachedJsonEntity(
                    key = tripKey(tripId),
                    json = json.encodeToString(TripDetailsDto.serializer(), result.value),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    /**
     * Resolves a shared trip link and caches it, so the screen it opens onto reads instantly rather
     * than starting blank. No identity is required: the token is the credential.
     */
    suspend fun resolveShareToken(token: String): DataResult<TripDetailsDto> {
        val result = runNetwork { api.resolveShareToken(token) }
        if (result is DataResult.Ok) {
            cache.put(
                CachedJsonEntity(
                    key = tripKey(result.value.trip.id),
                    json = json.encodeToString(TripDetailsDto.serializer(), result.value),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    // ---- Trip list (offline-first) ----

    fun observeMyTrips(): Flow<List<SavedTripSummaryDto>> =
        cache.observe(MY_TRIPS_KEY).map { row ->
            row?.json?.let { text ->
                runCatching {
                    json.decodeFromString(ListSerializer(SavedTripSummaryDto.serializer()), text)
                }.getOrNull()
            } ?: emptyList()
        }

    suspend fun refreshMyTrips(): DataResult<List<SavedTripSummaryDto>> {
        val result = runNetwork { api.getMyTrips().trips }
        if (result is DataResult.Ok) {
            cache.put(
                CachedJsonEntity(
                    key = MY_TRIPS_KEY,
                    json = json.encodeToString(ListSerializer(SavedTripSummaryDto.serializer()), result.value),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }

    // ---- Mutations ----

    suspend fun createTrip(dto: CreateTripDto): DataResult<CreateTripResponseDto> =
        runNetwork { api.createTrip(dto) }

    suspend fun triggerGeneration(tripId: String): DataResult<GenerateTripResponseDto> =
        runNetwork { api.triggerGeneration(tripId) }

    suspend fun getStatus(tripId: String): DataResult<TripStatusResponseDto> =
        runNetwork { api.getTripStatus(tripId) }

    suspend fun modify(itineraryId: String, instruction: String): DataResult<ModifyItineraryResponseDto> =
        runNetwork { api.modifyItinerary(itineraryId, ModifyItineraryRequestDto(instruction = instruction)) }

    suspend fun replan(tripId: String, intent: String): DataResult<ReplanResponseDto> =
        runNetwork { api.replanTrip(tripId, ReplanRequestDto(intent = intent)) }

    suspend fun setLocked(tripId: String, locked: Boolean): DataResult<LockResponseDto> =
        runNetwork { if (locked) api.lockTrip(tripId) else api.unlockTrip(tripId) }

    suspend fun delete(tripId: String): DataResult<Unit> {
        val result = runNetwork { api.deleteTrip(tripId) }
        if (result is DataResult.Ok) cache.delete(tripKey(tripId))
        return when (result) {
            is DataResult.Ok -> DataResult.Ok(Unit)
            is DataResult.Fail -> result
        }
    }

    // ---- Visited stops (local, durable) ----

    fun observeVisitedIds(): Flow<Set<String>> = visitedDao.observeIds().map { it.toSet() }

    suspend fun isVisited(activityId: String): Boolean = visitedDao.isVisited(activityId)

    /** Toggle a stop's visited mark and return the new state. */
    suspend fun toggleVisited(activityId: String): Boolean {
        val now = visitedDao.isVisited(activityId)
        if (now) {
            visitedDao.delete(activityId)
        } else {
            visitedDao.insert(VisitedStopEntity(activityId, System.currentTimeMillis()))
        }
        return !now
    }
}
