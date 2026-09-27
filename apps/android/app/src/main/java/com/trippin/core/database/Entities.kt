package com.trippin.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A cached server response, stored as the JSON the app already parses. Keyed so one table holds
 * everything the app caches: a trip's details under its id, the account's trip list under "me:trips",
 * the home feed under "home". This is the offline-first store: a screen reads the cache first and
 * refreshes over the network, so a plan already fetched is still there with no signal.
 */
@Entity(tableName = "cached_json")
data class CachedJsonEntity(
    @PrimaryKey val key: String,
    val json: String,
    val updatedAt: Long
)

/** A stop the traveller ticked off. Survives the app closing, unlike the old in-memory set. */
@Entity(tableName = "visited_stops")
data class VisitedStopEntity(
    @PrimaryKey val activityId: String,
    val visitedAt: Long
)

/** A place the traveller saved for later, kept with enough to show it offline. */
@Entity(tableName = "saved_spots")
data class SavedSpotEntity(
    @PrimaryKey val placeId: String,
    val name: String,
    val address: String,
    val json: String,
    val savedAt: Long
)
