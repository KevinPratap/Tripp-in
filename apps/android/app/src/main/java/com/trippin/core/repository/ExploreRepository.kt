package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.runNetwork
import com.trippin.core.database.CachedJsonDao
import com.trippin.core.database.CachedJsonEntity
import com.trippin.core.network.ApiService
import com.trippin.core.network.ExploreRouteDto
import com.trippin.core.network.GeoPointDto
import com.trippin.core.network.NearbyPlacesDto
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Walking routes between stops and the places around them. A day's route is kept on the phone once
 * fetched, so the map still draws the streets offline.
 */
@Singleton
class ExploreRepository @Inject constructor(
    private val api: ApiService,
    private val cache: CachedJsonDao,
    private val json: Json
) {
    suspend fun route(stops: List<GeoPointDto>): DataResult<ExploreRouteDto> {
        val points = stops.joinToString(";") { String.format(Locale.US, "%.6f,%.6f", it.latitude, it.longitude) }
        val key = "route:$points"
        val fresh = runNetwork { api.exploreRoute(points) }
        if (fresh is DataResult.Ok) {
            cache.put(CachedJsonEntity(key, json.encodeToString(ExploreRouteDto.serializer(), fresh.value), System.currentTimeMillis()))
            return fresh
        }
        val saved = cache.get(key)?.json?.let {
            runCatching { json.decodeFromString(ExploreRouteDto.serializer(), it) }.getOrNull()
        }
        return if (saved != null) DataResult.Ok(saved) else fresh
    }

    suspend fun nearby(center: GeoPointDto, radiusMeters: Int = 800): DataResult<NearbyPlacesDto> =
        runNetwork { api.exploreNearby(center.latitude, center.longitude, radiusMeters) }
}
