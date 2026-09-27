package com.trippin.core.common

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A trip, or one stop in it, as something a calendar app can take.
 *
 * The web app has exported a trip as an ICS file for a while; the phone could not put a trip in a
 * calendar at all. This is the data half of fixing that, and it is deliberately separate from the
 * Intent that carries it: the dates, the title and the wording are worked out here, in plain Kotlin, so
 * they are tested without a device, and the Android side is left with nothing but plumbing.
 *
 * Nothing here states more than the plan knows. A trip that is still a draft says so rather than
 * borrowing the wording of a verified one, and a stop whose times will not parse produces no event at
 * all instead of one at a guessed hour.
 */
data class CalendarEvent(
    val title: String,
    val description: String,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val allDay: Boolean
)

private val dayLabel = DateTimeFormatter.ofPattern("d MMM", Locale.US)

/** "HH:mm", the shape the API sends a stop's times in. Longer strings are truncated to it. */
private fun parseStopTime(raw: String): LocalTime? =
    runCatching { LocalTime.parse(raw.take(5), DateTimeFormatter.ofPattern("HH:mm")) }.getOrNull()

private fun parseDate(raw: String): LocalDate? = runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()

/**
 * The whole trip as one all-day entry.
 *
 * All-day events are keyed to UTC midnight rather than the device's zone, which is what the calendar
 * provider expects, and the end is the day after the last day because an all-day end is exclusive. Get
 * that wrong and a four day trip shows as three.
 */
fun tripCalendarEvent(
    destination: String,
    startDate: String,
    endDate: String,
    dayCount: Int,
    stopCount: Int,
    itineraryStatus: String?
): CalendarEvent? {
    val start = parseDate(startDate) ?: return null
    val end = parseDate(endDate) ?: return null
    if (end.isBefore(start)) return null

    val place = destination.trim().ifBlank { return null }

    val facts = buildList {
        if (dayCount > 0) add(if (dayCount == 1) "1 day" else "$dayCount days")
        if (stopCount > 0) add(if (stopCount == 1) "1 stop" else "$stopCount stops")
    }.joinToString(", ")

    // Only a verified plan gets to say it was checked. A draft says what a draft is.
    val standing = when (itineraryStatus?.uppercase()) {
        "VERIFIED" -> "Every stop checked against its opening hours and the travel time between them."
        "DRAFT" -> "This plan is still a draft: some checks did not pass or a limit was relaxed."
        else -> null
    }

    val description = listOfNotNull(
        "${place}, ${start.format(dayLabel)} to ${end.format(dayLabel)}.",
        facts.ifBlank { null },
        standing
    ).joinToString(" ")

    return CalendarEvent(
        title = "$place trip",
        description = description,
        location = place,
        startMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        endMillis = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        allDay = true
    )
}

/**
 * One stop as a timed entry, in the traveller's own zone.
 *
 * A stop whose end reads earlier than its start is treated as crossing midnight rather than as an error,
 * which is the only reading that produces a sane entry. A stop we cannot place in time produces nothing.
 */
fun stopCalendarEvent(
    title: String,
    dayDate: String,
    startTime: String,
    endTime: String,
    address: String?,
    zone: ZoneId
): CalendarEvent? {
    val date = parseDate(dayDate) ?: return null
    val from = parseStopTime(startTime) ?: return null
    val to = parseStopTime(endTime) ?: return null
    val name = title.trim().ifBlank { return null }

    val startsAt = date.atTime(from).atZone(zone)
    val endsAt = if (to.isAfter(from)) {
        date.atTime(to).atZone(zone)
    } else {
        date.plusDays(1).atTime(to).atZone(zone)
    }

    return CalendarEvent(
        title = name,
        description = listOfNotNull(
            "Part of your Tripp'in plan.",
            address?.trim()?.ifBlank { null }
        ).joinToString(" "),
        location = address?.trim()?.ifBlank { null },
        startMillis = startsAt.toInstant().toEpochMilli(),
        endMillis = endsAt.toInstant().toEpochMilli(),
        allDay = false
    )
}
