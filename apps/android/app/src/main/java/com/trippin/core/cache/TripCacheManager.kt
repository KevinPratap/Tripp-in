package com.trippin.core.cache

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import com.trippin.core.network.HomeFeedDto
import com.trippin.core.network.TripDetailsDto

/**
 * In-memory client-side cache manager.
 * Eliminates sluggish network delays and jarring spinners when switching between
 * Home, Itinerary, Map, and Today screens.
 */
object TripCacheManager {
    private val tripDetailsCache = mutableMapOf<String, TripDetailsDto>()
    private val visitedActivityIds = mutableSetOf<String>()
    
    val homeFeedState = mutableStateOf<HomeFeedDto?>(null)

    fun getTrip(tripId: String): TripDetailsDto? {
        return tripDetailsCache[tripId]
    }

    fun putTrip(tripId: String, details: TripDetailsDto) {
        tripDetailsCache[tripId] = details
    }

    fun invalidateTrip(tripId: String) {
        tripDetailsCache.remove(tripId)
    }

    fun isActivityVisited(activityId: String): Boolean {
        return visitedActivityIds.contains(activityId)
    }

    fun toggleActivityVisited(activityId: String): Boolean {
        return if (visitedActivityIds.contains(activityId)) {
            visitedActivityIds.remove(activityId)
            false
        } else {
            visitedActivityIds.add(activityId)
            true
        }
    }

    fun getVisitedCountForDay(activityIds: List<String>): Int {
        return activityIds.count { visitedActivityIds.contains(it) }
    }
}
