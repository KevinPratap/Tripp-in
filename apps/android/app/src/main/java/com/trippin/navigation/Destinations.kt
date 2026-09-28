package com.trippin.navigation

import kotlinx.serialization.Serializable

/**
 * Every route the app can reach, as a type. Navigation is type safe: a screen is navigated to with
 * an instance of its route (`navigate(TripHub(tripId))`) and reads its arguments back off that type,
 * so a missing or mistyped argument is a compile error rather than a null at runtime.
 *
 * The bottom bar holds Home, Trips and You, each of which always has something to show. Everything
 * about one trip hangs off [TripHub] and is pushed on top of it, so no tab is ever an empty "pick a
 * trip first" screen.
 */
sealed interface Destination

@Serializable
data object Home : Destination

@Serializable
data object Trips : Destination

@Serializable
data object You : Destination

/** One trip's overview: the place, what is next, and every section of the trip. */
@Serializable
data class TripHub(val tripId: String) : Destination

/** A trip's day-by-day plan. */
@Serializable
data class Plan(val tripId: String) : Destination

/** The people on a trip. */
@Serializable
data class Group(val tripId: String) : Destination

/** The trip planner, optionally seeded with a destination the traveller tapped. */
@Serializable
data class Planner(val destination: String = "") : Destination

@Serializable
data class Today(val tripId: String) : Destination

/** One stop on a trip's plan, full page: when, why, where each fact came from, and how to get there. */
@Serializable
data class Stop(val tripId: String, val activityId: String) : Destination

/** What the group has spent on a trip and who owes whom. */
@Serializable
data class Budget(val tripId: String) : Destination

@Serializable
data class MapView(val tripId: String) : Destination

/** Every saved version of a trip's plan. */
@Serializable
data class History(val tripId: String) : Destination

/**
 * Everything to do within walking distance of a point. Opened from a stop (with [tripId] and
 * [activityId], so a place found here can be swapped into the plan) or from the traveller's own
 * location (without them).
 */
@Serializable
data class Explore(
    val lat: Double,
    val lng: Double,
    val title: String,
    val tripId: String? = null,
    val activityId: String? = null
) : Destination
