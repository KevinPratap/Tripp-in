package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.runNetwork
import com.trippin.core.database.SavedSpotDao
import com.trippin.core.database.SavedSpotEntity
import com.trippin.core.network.ApiService
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
