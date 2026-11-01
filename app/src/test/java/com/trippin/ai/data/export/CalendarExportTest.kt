package com.trippin.ai.data.export

import com.trippin.ai.data.model.AltPlace
import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.ItineraryDay
import com.trippin.ai.data.model.ItineraryStop
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripStatus
import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.model.Category
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CalendarExportTest {

    private fun sampleTrip() = Trip(
        id = "t100", title = "Tokyo Journey", ownerId = "u1", memberIds = listOf("u1"),
        status = TripStatus.CONFIRMED, isGroup = false, startDate = LocalDate.of(2026, 8, 15),
        days = 1, pace = TripBrain.Pace.STANDARD, destination = Destination("Tokyo", "Japan", 35.6, 139.6),
        stay = null, inviteCode = null, inviteExpiresAt = null, destinationVoteClosesAt = null, createdAt = 1000L,
    )

    private fun sampleItin(stopName: String = "Meiji Shrine"): Itinerary {
        val stop = ItineraryStop(
            key = "d1-s0-key", name = stopName, lat = 35.6, lng = 139.6, category = Category.LANDMARK,
            arrive = 540, start = 550, leave = 640, travelMinutes = 10, opensAt = 360, closesAt = 1080,
            hoursEstimated = false, pDisrupted = 0.05, pDelay = 0.02, pClosed = 0.01, pRainGivenDisrupted = 0.1,
            risk = "low", reason = "good weather", optional = true, ideaId = "i1",
        )
        val day = ItineraryDay(
            date = LocalDate.of(2026, 8, 15), rainProbability = 0.05, rainKnown = true, verified = true,
            km = 3.2, evolutionBest = emptyList(), evolutionAverage = emptyList(), stops = listOf(stop),
        )
        return Itinerary(
            version = 1, generatedAt = 1000L, generatedById = "u1", generatedByName = "Alex", locked = true,
            dayStart = 540, origin = AltPlace("Station", 35.6, 139.6, Category.LANDMARK), days = listOf(day),
            alternates = emptyList(), notes = emptyList(), gaImprovementPercent = 5.0,
        )
    }

    @Test fun generatesValidIcsStructure() {
        val ics = CalendarExport.ics(sampleTrip(), sampleItin())

        assertTrue(ics.contains("BEGIN:VCALENDAR"))
        assertTrue(ics.contains("VERSION:2.0"))
        assertTrue(ics.contains("PRODID:-//Trippin AI//EN"))
        assertTrue(ics.contains("BEGIN:VEVENT"))
        assertTrue(ics.contains("UID:t100-d1-s0-key@trippin.ai"))
        assertTrue(ics.contains("DTSTART:20260815T091000"))
        assertTrue(ics.contains("DTEND:20260815T104000"))
        assertTrue(ics.contains("SUMMARY:Meiji Shrine"))
        assertTrue(ics.contains("GEO:35.6;139.6"))
        assertTrue(ics.contains("LOCATION:Meiji Shrine"))
        assertTrue(ics.contains("END:VEVENT"))
        assertTrue(ics.contains("END:VCALENDAR"))
        assertTrue(ics.endsWith("\r\n"))
    }

    @Test fun escapesSpecialCharacters() {
        val complexName = "Bakery; Cafe, Tea & Sweets\nTokyo"
        val ics = CalendarExport.ics(sampleTrip(), sampleItin(complexName))
        assertTrue(ics.contains("Bakery\\; Cafe\\, Tea & Sweets\\nTokyo"))
    }

    @Test fun foldsLinesExceedingLimit() {
        val longStopName = "A".repeat(100)
        val ics = CalendarExport.ics(sampleTrip(), sampleItin(longStopName))
        assertTrue(ics.contains("\r\n "))
    }
}
