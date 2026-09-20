package com.trippin.core.cache

import androidx.compose.runtime.mutableStateListOf
import com.trippin.core.network.PlaceSearchResultDto

/**
 * Client-side Saved Spots (Bookmarks Bucket) manager.
 * Allows travelers to save venues they love and retrieve them on the go.
 */
object SavedSpotsManager {
    val savedSpots = mutableStateListOf<PlaceSearchResultDto>()

    fun isSaved(placeId: String): Boolean {
        return savedSpots.any { it.id == placeId }
    }

    fun toggleSave(place: PlaceSearchResultDto): Boolean {
        val existingIndex = savedSpots.indexOfFirst { it.id == place.id }
        return if (existingIndex >= 0) {
            savedSpots.removeAt(existingIndex)
            false
        } else {
            savedSpots.add(place)
            true
        }
    }
}
