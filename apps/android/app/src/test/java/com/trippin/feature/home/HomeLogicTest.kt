package com.trippin.feature.home

import com.trippin.core.network.ActivityDto
import com.trippin.core.network.SavedTripSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class HomeLogicTest {

    private val today = LocalDate.of(2026, 9, 28)

    private fun trip(id: String, start: String, end: String) =
        SavedTripSummaryDto(id = id, destinationName = id, startDate = start, endDate = end)

    private fun stop(id: String, start: String, end: String) = ActivityDto(
        id = id, placeId = id, title = id, type = "SIGHT",
        startTime = start, endTime = end, durationMinutes = 60
    )

    @Test
    fun `a trip happening today is featured as live with its day number`() {
        val trips = listOf(
            trip("kyoto", "2026-11-14", "2026-11-20"),
            trip("lisbon", "2026-09-27T00:00:00.000Z", "2026-09-30T00:00:00.000Z")
        )
        val featured = pickFeatured(trips, today)
        assertTrue(featured is Featured.Live)
        assertEquals("lisbon", featured!!.trip.id)
        assertEquals(2, (featured as Featured.Live).dayNumber)
        assertEquals(4, featured.window.dayCount)
    }

    @Test
    fun `with nothing live the soonest future trip is featured with a countdown`() {
        val trips = listOf(
            trip("kyoto", "2026-11-14", "2026-11-20"),
            trip("porto", "2026-10-10", "2026-10-12")
        )
        val featured = pickFeatured(trips, today) as Featured.Upcoming
        assertEquals("porto", featured.trip.id)
        assertEquals(12L, featured.daysUntil)
    }

    @Test
    fun `finished and undated trips are never featured`() {
        val trips = listOf(
            trip("barcelona", "2026-06-02", "2026-06-06"),
            trip("nowhere", "", "")
        )
        assertNull(pickFeatured(trips, today))
    }

    @Test
    fun `upcoming list leaves out the featured trip and anything already over`() {
        val trips = listOf(
            trip("barcelona", "2026-06-02", "2026-06-06"),
            trip("kyoto", "2026-11-14", "2026-11-20"),
            trip("lisbon", "2026-09-27", "2026-09-30"),
            trip("porto", "2026-10-10", "2026-10-12")
        )
        assertEquals(listOf("porto", "kyoto"), upcomingAfter(trips, "lisbon", today).map { it.id })
    }

    @Test
    fun `now is the stop whose window holds the time and next is the first to start after it`() {
        val stops = listOf(
            stop("jeronimos", "10:30", "11:30"),
            stop("pasteis", "12:45", "13:30"),
            stop("tower", "11:45", "12:30")
        )
        val result = nowAndNext(stops, LocalTime.of(12, 0))
        assertEquals("tower", result.now?.id)
        assertEquals("pasteis", result.next?.id)
    }

    @Test
    fun `between stops there is no now, only a next`() {
        val stops = listOf(stop("a", "10:00", "11:00"), stop("b", "14:00", "15:00"))
        val result = nowAndNext(stops, LocalTime.of(12, 0))
        assertNull(result.now)
        assertEquals("b", result.next?.id)
    }

    @Test
    fun `after the last stop there is neither`() {
        val result = nowAndNext(listOf(stop("a", "10:00", "11:00")), LocalTime.of(22, 0))
        assertNull(result.now)
        assertNull(result.next)
    }
}
