package com.trippin.navigation

import kotlinx.serialization.Serializable

/**
 * Every route the app can reach, as a type. Navigation is type safe: a screen is navigated to with
 * an instance of its route (`navigate(Plan(tripId))`) and reads its arguments back off that type, so
 * a missing or mistyped argument is a compile error rather than a null at runtime.
 *
 * Only routes something actually navigates to exist here. The bottom bar's four jobs are Trips, Plan,
 * Group and You; Planner, Today and Map are pushed on top of them. There is no Home and no standalone
 * Generating route: discovery lives on Trips, and the build wait is a state of Plan.
 */
sealed interface Destination

@Serializable
data object Trips : Destination

/** The Plan tab for a trip. A blank id means no trip is open yet. */
@Serializable
data class Plan(val tripId: String = "") : Destination

/** The Group tab for a trip. A blank id means no trip is open yet. */
@Serializable
data class Group(val tripId: String = "") : Destination

@Serializable
data object You : Destination

/** The trip planner, optionally seeded with a destination the traveller tapped. */
@Serializable
data class Planner(val destination: String = "") : Destination

@Serializable
data class Today(val tripId: String) : Destination

@Serializable
data class MapView(val tripId: String) : Destination
