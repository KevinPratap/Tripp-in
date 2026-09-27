package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.runNetwork
import com.trippin.core.database.SavedSpotDao
import com.trippin.core.database.SavedSpotEntity
import com.trippin.core.network.ApiService
import com.trippin.core.network.DestinationSuggestionDto
import com.trippin.core.network.PlaceSearchResultDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Real places from OpenStreetMap, and the ones the traveller saved. No invented ratings anywhere. */
@Singleton
class PlacesRepository @Inject constructor(
    private val api: ApiService,
    private val savedDao: SavedSpotDao,
    private val json: Json
) {
    suspend fun search(query: String): DataResult<List<PlaceSearchResultDto>> =
        runNetwork { api.searchPlaces(query.trim()) }

    /**
     * Destination suggestions for what has been typed so far.
     *
     * A query under two characters returns an empty list without a request: one character matches most
     * of the planet. A failure returns an empty list too, not an error, because suggestions are a
     * convenience: the planner still accepts typed text, and the engine resolves the destination again
     * when it builds the plan. So an outage costs the traveller the dropdown, never the trip.
     */
    suspend fun autocompleteDestinations(query: String): List<DestinationSuggestionDto> {
        val term = query.trim()
        if (term.length < 2) return emptyList()
        return when (val result = runNetwork { api.autocompleteDestinations(term) }) {
            is DataResult.Ok -> result.value.suggestions
            is DataResult.Fail -> emptyList()
        }
    }

    fun observeSavedSpots(): Flow<List<PlaceSearchResultDto>> = savedDao.observeAll().map { rows ->
        rows.mapNotNull { row ->
            runCatching { json.decodeFromString(PlaceSearchResultDto.serializer(), row.json) }.getOrNull()
        }
    }

    fun observeSavedIds(): Flow<Set<String>> =
        savedDao.observeAll().map { rows -> rows.map { it.placeId }.toSet() }

    suspend fun isSaved(placeId: String): Boolean = savedDao.isSaved(placeId)

    suspend fun toggleSaved(place: PlaceSearchResultDto): Boolean {
        val id = place.id.ifBlank { place.name }
        val already = savedDao.isSaved(id)
        if (already) {
            savedDao.deleteById(id)
        } else {
            savedDao.insert(
                SavedSpotEntity(
                    placeId = id,
                    name = place.name,
                    address = place.formattedAddress,
                    json = json.encodeToString(PlaceSearchResultDto.serializer(), place),
                    savedAt = System.currentTimeMillis()
                )
            )
        }
        return !already
    }
}
