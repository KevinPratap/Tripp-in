package com.trippin.ai.data.share

import com.trippin.ai.data.model.AltPlace
import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaStatus
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.ItineraryDay
import com.trippin.ai.data.model.ItineraryStop
import com.trippin.ai.data.model.Member
import com.trippin.ai.data.model.Role
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripStatus
import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShareSnapshotTest {

    private fun sampleTrip() = Trip(
        id = "trip-99", title = "Summer in Kyoto", ownerId = "u1", memberIds = listOf("u1", "u2"),
        status = TripStatus.PLAN_READY, isGroup = true, startDate = LocalDate.of(2026, 7, 1), days = 3,
        pace = TripBrain.Pace.STANDARD, destination = Destination("Kyoto", "Japan", 35.0, 135.7),
        stay = null, inviteCode = "CODE1", inviteExpiresAt = null, destinationVoteClosesAt = null, createdAt = 1000L,
    )

    @Test fun generatesShareLink() {
        assertEquals("https://trippin-ee84c.web.app/t/share123", ShareSnapshot.link("share123"))
    }

    @Test fun buildIncludesSafeFieldsAndFiltersIdeas() {
        val trip = sampleTrip()
        val members = listOf(Member("u1", "Maya", Role.OWNER, 100L), Member("u2", "Liam", Role.MEMBER, 200L))
        val place = Idea(
            id = "idea-p1", type = IdeaType.PLACE, title = "Kinkaku-ji", subtitle = "Golden Pavilion",
            country = "Japan", lat = 35.0, lng = 135.7, category = Category.LANDMARK, tags = emptySet(),
            createdBy = "u1", createdByName = "Maya", createdAt = 150L,
            votes = mapOf("u1" to VoteKind.MUST, "u2" to VoteKind.UP), status = IdeaStatus.SELECTED, commentCount = 1,
        )
        val destinationIdea = place.copy(id = "idea-d1", type = IdeaType.DESTINATION, title = "Tokyo")
        val dismissedPlace = place.copy(id = "idea-p2", status = IdeaStatus.DISMISSED)

        val snapshot = ShareSnapshot.build(trip, members, listOf(place, destinationIdea, dismissedPlace), null)

        assertEquals("trip-99", snapshot["tripId"])
        assertEquals("Summer in Kyoto", snapshot["title"])
        assertEquals("PLAN_READY", snapshot["status"])
        assertEquals("2026-07-01", snapshot["startDate"])
        assertEquals(3, snapshot["days"])
        assertNull(snapshot["plan"])

        @Suppress("UNCHECKED_CAST")
        val membersList = snapshot["members"] as List<Map<String, Any?>>
        assertEquals(listOf("Maya", "Liam"), membersList.map { it["name"] })

        @Suppress("UNCHECKED_CAST")
        val ideasList = snapshot["ideas"] as List<Map<String, Any?>>
        assertEquals(1, ideasList.size)
        assertEquals("idea-p1", ideasList[0]["id"])
        assertEquals(1, ideasList[0]["love"])
        assertEquals(1, ideasList[0]["like"])
        assertEquals(0, ideasList[0]["pass"])
        assertTrue(ideasList[0]["selected"] as Boolean)
    }

    @Test fun buildFormatsPlanWhenPresent() {
        val trip = sampleTrip()
        val stop = ItineraryStop(
            key = "d1-s0", name = "Nijo Castle", lat = 35.0, lng = 135.7, category = Category.MUSEUM,
            arrive = 540, start = 550, leave = 630, travelMinutes = 10, opensAt = 540, closesAt = 1020,
            hoursEstimated = false, pDisrupted = 0.02, pDelay = 0.01, pClosed = 0.0, pRainGivenDisrupted = 0.05,
            risk = "low", reason = "good", optional = false, ideaId = "i1",
        )
        val day = ItineraryDay(
            date = LocalDate.of(2026, 7, 1), rainProbability = 0.1, rainKnown = true, verified = true, km = 5.0,
            evolutionBest = emptyList(), evolutionAverage = emptyList(), stops = listOf(stop),
        )
        val itin = Itinerary(
            version = 1, generatedAt = 1000L, generatedById = "u1", generatedByName = "Maya", locked = true,
            dayStart = 540, origin = AltPlace("Station", 35.0, 135.7, Category.LANDMARK), days = listOf(day),
            alternates = emptyList(), notes = emptyList(), gaImprovementPercent = 10.0,
        )

        val snapshot = ShareSnapshot.build(trip, emptyList(), emptyList(), itin)

        @Suppress("UNCHECKED_CAST")
        val plan = snapshot["plan"] as Map<String, Any?>
        assertTrue(plan["locked"] as Boolean)

        @Suppress("UNCHECKED_CAST")
        val days = plan["days"] as List<Map<String, Any?>>
        assertEquals(1, days.size)
        assertEquals("2026-07-01", days[0]["date"])

        @Suppress("UNCHECKED_CAST")
        val stops = days[0]["stops"] as List<Map<String, Any?>>
        assertEquals("Nijo Castle", stops[0]["name"])
        assertEquals("MUSEUM", stops[0]["category"])
        assertEquals(550, stops[0]["start"]); assertEquals(630, stops[0]["leave"])
    }
}
