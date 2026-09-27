package com.trippin.feature.planner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The date shortcuts, including the days the arithmetic is easy to get wrong.
 *
 * All dates here are real: 2026-09-27 is a Sunday, 2026-09-28 a Monday, 2026-10-03 a Saturday.
 */
class DatePresetsTest {

    private val monday = LocalDate.of(2026, 9, 28)
    private val wednesday = LocalDate.of(2026, 9, 30)
    private val friday = LocalDate.of(2026, 10, 2)
    private val saturday = LocalDate.of(2026, 10, 3)
    private val sunday = LocalDate.of(2026, 10, 4)

    @Test
    fun `the fixtures are the days this test claims they are`() {
        assertEquals(DayOfWeek.MONDAY, monday.dayOfWeek)
        assertEquals(DayOfWeek.WEDNESDAY, wednesday.dayOfWeek)
        assertEquals(DayOfWeek.FRIDAY, friday.dayOfWeek)
        assertEquals(DayOfWeek.SATURDAY, saturday.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, sunday.dayOfWeek)
    }

    // ---- This weekend ----

    @Test
    fun `midweek, this weekend is the coming Saturday and Sunday`() {
        val (start, end) = datesFor(DatePreset.THIS_WEEKEND, wednesday)
        assertEquals(LocalDate.of(2026, 10, 3), start)
        assertEquals(LocalDate.of(2026, 10, 4), end)
    }

    @Test
    fun `on a Friday, this weekend is tomorrow and the day after`() {
        val (start, end) = datesFor(DatePreset.THIS_WEEKEND, friday)
        assertEquals(LocalDate.of(2026, 10, 3), start)
        assertEquals(LocalDate.of(2026, 10, 4), end)
    }

    @Test
    fun `on a Saturday, this weekend starts today`() {
        val (start, end) = datesFor(DatePreset.THIS_WEEKEND, saturday)
        assertEquals(saturday, start)
        assertEquals(sunday, end)
    }

    @Test
    fun `on a Sunday, this weekend is today alone and not six days away`() {
        // The awkward case: the weekend's Saturday is yesterday, and a trip cannot start in the past.
        val (start, end) = datesFor(DatePreset.THIS_WEEKEND, sunday)
        assertEquals(sunday, start)
        assertEquals(sunday, end)
    }

    // ---- Next weekend ----

    @Test
    fun `midweek, next weekend is the Saturday after the coming one`() {
        val (start, end) = datesFor(DatePreset.NEXT_WEEKEND, wednesday)
        assertEquals(LocalDate.of(2026, 10, 10), start)
        assertEquals(LocalDate.of(2026, 10, 11), end)
    }

    @Test
    fun `on a Sunday, next weekend is the following full weekend`() {
        val (start, end) = datesFor(DatePreset.NEXT_WEEKEND, sunday)
        assertEquals(LocalDate.of(2026, 10, 10), start)
        assertEquals(LocalDate.of(2026, 10, 11), end)
        assertEquals(DayOfWeek.SATURDAY, start.dayOfWeek)
        assertEquals(DayOfWeek.SUNDAY, end.dayOfWeek)
    }

    @Test
    fun `next weekend never overlaps this weekend`() {
        for (offset in 0..13L) {
            val today = monday.plusDays(offset)
            val thisEnd = datesFor(DatePreset.THIS_WEEKEND, today).second
            val nextStart = datesFor(DatePreset.NEXT_WEEKEND, today).first
            assertTrue("failed for $today (${today.dayOfWeek})", nextStart.isAfter(thisEnd))
        }
    }

    // ---- A week ----

    @Test
    fun `a week is seven days counting today, so the label and the span agree`() {
        val (start, end) = datesFor(DatePreset.ONE_WEEK, monday)
        assertEquals(monday, start)
        assertEquals(LocalDate.of(2026, 10, 4), end)
        assertEquals(6, java.time.temporal.ChronoUnit.DAYS.between(start, end))
    }

    // ---- Rules that hold for every preset ----

    @Test
    fun `no preset ever starts in the past, which the form would reject`() {
        for (offset in 0..13L) {
            val today = monday.plusDays(offset)
            for (preset in DatePreset.entries) {
                val (start, _) = datesFor(preset, today)
                assertTrue("$preset on $today started before today", !start.isBefore(today))
            }
        }
    }

    @Test
    fun `no preset ever ends before it starts, which the form would also reject`() {
        for (offset in 0..13L) {
            val today = monday.plusDays(offset)
            for (preset in DatePreset.entries) {
                val (start, end) = datesFor(preset, today)
                assertTrue("$preset on $today ended before it started", !end.isBefore(start))
            }
        }
    }

    // ---- Which chip shows as chosen ----

    @Test
    fun `a preset's chip shows as chosen once its dates are applied`() {
        val (start, end) = datesFor(DatePreset.THIS_WEEKEND, wednesday)
        val state = PlannerUiState(startDate = start, endDate = end)
        assertTrue(state.matchesPreset(DatePreset.THIS_WEEKEND, wednesday))
    }

    @Test
    fun `only the matching preset shows as chosen`() {
        val (start, end) = datesFor(DatePreset.NEXT_WEEKEND, wednesday)
        val state = PlannerUiState(startDate = start, endDate = end)
        assertTrue(state.matchesPreset(DatePreset.NEXT_WEEKEND, wednesday))
        assertTrue(!state.matchesPreset(DatePreset.THIS_WEEKEND, wednesday))
        assertTrue(!state.matchesPreset(DatePreset.ONE_WEEK, wednesday))
    }

    @Test
    fun `no preset shows as chosen before any dates are set`() {
        val state = PlannerUiState()
        for (preset in DatePreset.entries) {
            assertTrue("$preset claimed a match with no dates", !state.matchesPreset(preset, wednesday))
        }
    }

    @Test
    fun `hand-picked dates that match no preset leave every chip unchosen`() {
        val state = PlannerUiState(
            startDate = LocalDate.of(2026, 11, 17),
            endDate = LocalDate.of(2026, 11, 21)
        )
        for (preset in DatePreset.entries) {
            assertTrue("$preset claimed hand-picked dates", !state.matchesPreset(preset, wednesday))
        }
    }

    @Test
    fun `every preset produces a form the planner will accept`() {
        // Ties the presets to the validation they have to satisfy, so the two cannot drift apart.
        for (offset in 0..13L) {
            val today = monday.plusDays(offset)
            for (preset in DatePreset.entries) {
                val (start, end) = datesFor(preset, today)
                val state = PlannerUiState(
                    destination = "Lisbon, Portugal",
                    currency = "EUR",
                    startDate = start,
                    endDate = end
                )
                // dateProblem compares against the real today, so only check the parts that do not.
                assertTrue("$preset on $today ordered its dates wrongly", !end.isBefore(start))
                assertEquals(null, state.destinationProblem)
                assertEquals(null, state.currencyProblem)
            }
        }
    }
}
