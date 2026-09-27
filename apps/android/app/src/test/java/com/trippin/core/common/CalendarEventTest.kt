package com.trippin.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.ZoneId

/**
 * What goes into a calendar entry. The Intent that carries it is plumbing and is not tested here; every
 * decision that could be wrong is in these functions.
 */
class CalendarEventTest {

    private val lisbon = ZoneId.of("Europe/Lisbon")

    // ---- The whole trip ----

    private fun trip(
        destination: String = "Lisbon",
        start: String = "2026-10-11",
        end: String = "2026-10-14",
        days: Int = 4,
        stops: Int = 9,
        status: String? = "VERIFIED"
    ) = tripCalendarEvent(destination, start, end, days, stops, status)

    @Test
    fun `a trip spans every one of its days, because an all-day end is exclusive`() {
        // Get this wrong and a four day trip shows in the calendar as three.
        val event = trip()!!
        assertTrue(event.allDay)
        val span = Duration.ofMillis(event.endMillis - event.startMillis)
        assertEquals(4, span.toDays())
    }

    @Test
    fun `a one day trip spans one day`() {
        val event = trip(start = "2026-10-11", end = "2026-10-11", days = 1)!!
        assertEquals(1, Duration.ofMillis(event.endMillis - event.startMillis).toDays())
    }

    @Test
    fun `the title names the destination`() {
        assertEquals("Lisbon trip", trip()!!.title)
        assertEquals("Lisbon", trip()!!.location)
    }

    @Test
    fun `a verified plan says it was checked`() {
        val description = trip(status = "VERIFIED")!!.description
        assertTrue(description.contains("checked against its opening hours"))
    }

    @Test
    fun `a draft says it is a draft rather than borrowing a verified plan's wording`() {
        val description = trip(status = "DRAFT")!!.description
        assertTrue(description.contains("still a draft"))
        assertFalse(description.contains("Every stop checked"))
    }

    @Test
    fun `an unknown status claims neither`() {
        val description = trip(status = null)!!.description
        assertFalse(description.contains("checked"))
        assertFalse(description.contains("draft"))
    }

    @Test
    fun `counts are singular when there is one of them`() {
        val description = trip(days = 1, stops = 1)!!.description
        assertTrue(description.contains("1 day"))
        assertTrue(description.contains("1 stop"))
        assertFalse(description.contains("1 days"))
        assertFalse(description.contains("1 stops"))
    }

    @Test
    fun `counts that are not known are left out rather than printed as zero`() {
        val description = trip(days = 0, stops = 0)!!.description
        assertFalse(description.contains("0 day"))
        assertFalse(description.contains("0 stop"))
    }

    @Test
    fun `a trip that ends before it starts produces nothing`() {
        assertNull(trip(start = "2026-10-14", end = "2026-10-11"))
    }

    @Test
    fun `an unparseable or missing date produces nothing rather than a guessed one`() {
        assertNull(trip(start = "not a date"))
        assertNull(trip(end = ""))
    }

    @Test
    fun `a blank destination produces nothing`() {
        assertNull(trip(destination = "   "))
    }

    @Test
    fun `a date carrying a time is still read as its day`() {
        assertEquals(4, Duration.ofMillis(trip(start = "2026-10-11T00:00:00Z")!!.let { it.endMillis - it.startMillis }).toDays())
    }

    // ---- One stop ----

    private fun stop(
        title: String = "Mosteiro dos Jeronimos",
        date: String = "2026-10-11",
        from: String = "09:30",
        to: String = "11:00",
        address: String? = "Praca do Imperio"
    ) = stopCalendarEvent(title, date, from, to, address, lisbon)

    @Test
    fun `a stop runs for exactly its stated length`() {
        val event = stop()!!
        assertFalse(event.allDay)
        assertEquals(90, Duration.ofMillis(event.endMillis - event.startMillis).toMinutes())
    }

    @Test
    fun `a stop is placed in the traveller's own zone`() {
        val inLisbon = stopCalendarEvent("X", "2026-10-11", "09:30", "10:30", null, ZoneId.of("Europe/Lisbon"))!!
        val inTokyo = stopCalendarEvent("X", "2026-10-11", "09:30", "10:30", null, ZoneId.of("Asia/Tokyo"))!!
        // 09:30 in Tokyo is earlier in absolute time than 09:30 in Lisbon.
        assertTrue(inTokyo.startMillis < inLisbon.startMillis)
    }

    @Test
    fun `a stop whose end reads earlier than its start is treated as crossing midnight`() {
        val event = stop(from = "23:00", to = "00:30")!!
        assertEquals(90, Duration.ofMillis(event.endMillis - event.startMillis).toMinutes())
    }

    @Test
    fun `a stop carries its address as the location`() {
        assertEquals("Praca do Imperio", stop()!!.location)
    }

    @Test
    fun `a stop with no address has no location rather than an empty one`() {
        assertNull(stop(address = null)!!.location)
        assertNull(stop(address = "   ")!!.location)
    }

    @Test
    fun `a stop whose times will not parse produces nothing rather than an entry at a guessed hour`() {
        assertNull(stop(from = "half nine"))
        assertNull(stop(to = ""))
    }

    @Test
    fun `a seconds-bearing time is still read`() {
        assertEquals(90, Duration.ofMillis(stop(from = "09:30:00", to = "11:00:00")!!.let { it.endMillis - it.startMillis }).toMinutes())
    }

    @Test
    fun `a nameless stop produces nothing`() {
        assertNull(stop(title = "  "))
    }
}
