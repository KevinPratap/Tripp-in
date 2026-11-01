package com.trippin.feature.home

import com.trippin.core.network.ActivityDto
import com.trippin.core.network.SavedTripSummaryDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** A trip's dates, or null when either end is missing or unreadable. */
data class TripWindow(val start: LocalDate, val end: LocalDate) {
    fun contains(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)
    val dayCount: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()
}

fun parseIsoDate(raw: String?): LocalDate? =
    raw?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

fun tripWindow(trip: SavedTripSummaryDto): TripWindow? {
    val start = parseIsoDate(trip.startDate) ?: return null
    val end = parseIsoDate(trip.endDate) ?: return null
    return if (end.isBefore(start)) null else TripWindow(start, end)
}

/** Which trip Home leads with, and why. */
sealed interface Featured {
    val trip: SavedTripSummaryDto
    val window: TripWindow

    /** Today falls inside the trip. [dayNumber] is 1 on the first day. */
    data class Live(override val trip: SavedTripSummaryDto, override val window: TripWindow, val dayNumber: Int) : Featured

    /** The soonest trip that has not started. */
    data class Upcoming(override val trip: SavedTripSummaryDto, override val window: TripWindow, val daysUntil: Long) : Featured
}

/**
 * The trip happening today wins. Failing that, the soonest one still ahead. A trip that has ended,
 * or has no readable dates, is never featured: leading with a finished trip would put the past in
 * the one spot meant for what is next.
 */
fun pickFeatured(trips: List<SavedTripSummaryDto>, today: LocalDate): Featured? {
    val dated = trips.mapNotNull { trip -> tripWindow(trip)?.let { trip to it } }
    dated.filter { (_, w) -> w.contains(today) }
        .minByOrNull { (_, w) -> w.start }
        ?.let { (trip, w) ->
            return Featured.Live(trip, w, (ChronoUnit.DAYS.between(w.start, today) + 1).toInt())
        }
    return dated.filter { (_, w) -> w.start.isAfter(today) }
        .minByOrNull { (_, w) -> w.start }
        ?.let { (trip, w) -> Featured.Upcoming(trip, w, ChronoUnit.DAYS.between(today, w.start)) }
}

/** Trips still ahead other than the featured one, soonest first. */
fun upcomingAfter(trips: List<SavedTripSummaryDto>, featuredId: String?, today: LocalDate): List<SavedTripSummaryDto> =
    trips.mapNotNull { trip -> tripWindow(trip)?.let { trip to it } }
        .filter { (trip, w) -> trip.id != featuredId && !w.end.isBefore(today) }
        .sortedBy { (_, w) -> w.start }
        .map { it.first }

private fun clock(raw: String): LocalTime? = runCatching { LocalTime.parse(raw.take(5)) }.getOrNull()

/** The stop whose window holds [now], and the first one that starts after [now]. */
data class NowAndNext(val now: ActivityDto?, val next: ActivityDto?)

fun nowAndNext(activities: List<ActivityDto>, now: LocalTime): NowAndNext {
    val timed = activities.mapNotNull { act ->
        val start = clock(act.startTime) ?: return@mapNotNull null
        val end = clock(act.endTime) ?: return@mapNotNull null
        Triple(act, start, end)
    }.sortedBy { it.second }
    val current = timed.firstOrNull { (_, start, end) -> !now.isBefore(start) && now.isBefore(end) }?.first
    val upcoming = timed.firstOrNull { (_, start, _) -> start.isAfter(now) }?.first
    return NowAndNext(current, upcoming)
}
