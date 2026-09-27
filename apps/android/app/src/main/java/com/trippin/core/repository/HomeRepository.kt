package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.runNetwork
import com.trippin.core.database.CachedJsonDao
import com.trippin.core.database.CachedJsonEntity
import com.trippin.core.network.ApiService
import com.trippin.core.network.HomeFeedDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The discovery feed: destinations to start a trip from. Offline-first, so the ideas are still there
 * with no signal. Nothing here carries an invented rating; a destination's numbers are whatever the
 * server sent, or absent.
 */
@Singleton
class HomeRepository @Inject constructor(
    private val api: ApiService,
    private val cache: CachedJsonDao,
    private val json: Json
) {
    private val key = "home"

    fun observeHome(): Flow<HomeFeedDto?> = cache.observe(key).map { row ->
        row?.json?.let { text ->
            runCatching { json.decodeFromString(HomeFeedDto.serializer(), text) }.getOrNull()
        }
    }

    suspend fun refreshHome(): DataResult<HomeFeedDto> {
        val result = runNetwork { api.getHome() }
        if (result is DataResult.Ok) {
            cache.put(
                CachedJsonEntity(
                    key = key,
                    json = json.encodeToString(HomeFeedDto.serializer(), result.value),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }
}
