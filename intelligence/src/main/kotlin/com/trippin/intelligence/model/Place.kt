package com.trippin.intelligence.model

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** The kinds of stop the planner knows about. Used as actions by the Q-learning recommender. */
enum class Category { MUSEUM, FOOD, PARK, LANDMARK, SHOPPING, NIGHTLIFE }

/**
 * One place a traveller can visit.
 *
 * @param opensAt minutes after midnight the place opens (e.g. 600 = 10:00)
 * @param closesAt minutes after midnight it closes
 * @param hoursEstimated true when the hours were guessed from the category rather than published
 */
data class Place(
    val id: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val category: Category,
    val visitMinutes: Int,
    val opensAt: Int = 0,
    val closesAt: Int = 24 * 60,
    val hoursEstimated: Boolean = false,
    /** Days of the week the place is shut (1 = Monday … 7 = Sunday), from OSM `opening_hours`. */
    val closedDays: Set<Int> = emptySet(),
    /** Per-weekday hours when they differ from [opensAt]/[closesAt] (1 = Monday … 7 = Sunday). */
    val weeklyHours: Map<Int, Pair<Int, Int>> = emptyMap(),
    /** Facts the group planner can match against member constraints, e.g. "vegetarian", "wheelchair". */
    val tags: Set<String> = emptySet(),
) {
    /** False when the place is closed all day on [dayOfWeek] (1 = Monday … 7 = Sunday). */
    fun isOpenOn(dayOfWeek: Int): Boolean = dayOfWeek !in closedDays

    /** This place with its opening window set for [dayOfWeek], or null when it is closed that day. */
    fun forDay(dayOfWeek: Int): Place? {
        if (!isOpenOn(dayOfWeek)) return null
        val w = weeklyHours[dayOfWeek] ?: return this
        return copy(opensAt = w.first, closesAt = w.second)
    }

    companion object {
        const val TAG_VEGETARIAN = "vegetarian"
        const val TAG_VEGAN = "vegan"
        const val TAG_WHEELCHAIR = "wheelchair"
        const val TAG_ALCOHOL = "alcohol"
    }
}

object Geo {
    private const val EARTH_RADIUS_KM = 6371.0

    /** Great-circle distance between two points, in kilometres (haversine formula). */
    fun haversineKm(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
        val dLat = Math.toRadians(bLat - aLat)
        val dLng = Math.toRadians(bLng - aLng)
        val h = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLng / 2).pow(2)
        return 2 * EARTH_RADIUS_KM * asin(sqrt(h))
    }

    fun distanceKm(a: Place, b: Place): Double = haversineKm(a.lat, a.lng, b.lat, b.lng)

    /**
     * Rough door-to-door minutes: walk under 1.5 km (4.5 km/h), otherwise transit
     * (average 18 km/h plus 8 minutes of waiting and walking to the station).
     */
    fun travelMinutes(km: Double): Int =
        if (km < 1.5) (km / 4.5 * 60).toInt() + 1 else (km / 18.0 * 60 + 8).toInt()
}

/** Formats minutes after midnight as HH:MM. */
fun Int.asClock(): String = "%02d:%02d".format(this / 60, this % 60)
