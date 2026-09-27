package com.trippin.feature.planner

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The shortcuts above the date fields.
 *
 * Picking the two ends of a trip out of two separate calendar dialogs is four taps and a lot of
 * scrolling for the dates most trips actually use. These cover them in one.
 *
 * Every function takes today as an argument rather than reading the clock, so the week arithmetic is
 * testable, including the awkward days: on a Saturday "this weekend" starts today, and on a Sunday it
 * is today alone, because the weekend is nearly over rather than six days away.
 */
enum class DatePreset(val label: String) {
    THIS_WEEKEND("This weekend"),
    NEXT_WEEKEND("Next weekend"),
    ONE_WEEK("A week")
}

/** The two ends of a trip for this preset, first day first. Both ends are inclusive. */
fun datesFor(preset: DatePreset, today: LocalDate): Pair<LocalDate, LocalDate> = when (preset) {
    DatePreset.THIS_WEEKEND -> {
        val saturday = weekendSaturday(today)
        // On a Sunday the Saturday is yesterday, and a trip cannot start in the past.
        val start = if (saturday.isBefore(today)) today else saturday
        start to saturday.plusDays(1)
    }

    DatePreset.NEXT_WEEKEND -> {
        val saturday = weekendSaturday(today).plusWeeks(1)
        saturday to saturday.plusDays(1)
    }

    // Seven days counting today, so the label and the span agree.
    DatePreset.ONE_WEEK -> today to today.plusDays(6)
}

/**
 * The Saturday of the weekend [today] belongs to: the next one on or after today, except on a Sunday,
 * when it is the day before, because that Sunday is the end of a weekend rather than the start of the
 * wait for the next.
 */
private fun weekendSaturday(today: LocalDate): LocalDate = when (today.dayOfWeek) {
    DayOfWeek.SUNDAY -> today.minusDays(1)
    DayOfWeek.SATURDAY -> today
    else -> today.plusDays((DayOfWeek.SATURDAY.value - today.dayOfWeek.value).toLong())
}
