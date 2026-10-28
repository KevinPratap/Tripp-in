package com.trippin.ai.data.model

import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Category
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelTest {

    @Test fun rolePermissions() {
        assertTrue(Role.OWNER.canManage); assertTrue(Role.ADMIN.canManage)
        assertFalse(Role.MEMBER.canManage); assertFalse(Role.VIEWER.canManage)

        assertTrue(Role.OWNER.canContribute); assertTrue(Role.ADMIN.canContribute)
        assertTrue(Role.MEMBER.canContribute); assertFalse(Role.VIEWER.canContribute)
    }

    @Test fun destinationLabel() {
        assertEquals("Kyoto, Japan", Destination("Kyoto", "Japan", 35.0, 135.7).label)
        assertEquals("Lisbon", Destination("Lisbon", null, 38.7, -9.1).label)
        assertEquals("Tokyo", Destination("Tokyo", "  ", 35.6, 139.6).label)
    }

    @Test fun tripDatesAndLifecycle() {
        val start = LocalDate.of(2026, 11, 10)
        val trip = Trip(
            id = "t1", title = "Japan Tour", ownerId = "u1", memberIds = listOf("u1"),
            status = TripStatus.CONFIRMED, isGroup = false, startDate = start, days = 5,
            pace = TripBrain.Pace.STANDARD, destination = null, stay = null, inviteCode = "INV123",
            inviteExpiresAt = 2000000L, destinationVoteClosesAt = null, createdAt = 1000000L,
        )

        assertEquals(LocalDate.of(2026, 11, 14), trip.endDate)
        assertTrue(trip.isOngoing(LocalDate.of(2026, 11, 10)))
        assertTrue(trip.isOngoing(LocalDate.of(2026, 11, 14)))
        assertFalse(trip.isOngoing(LocalDate.of(2026, 11, 9)))
        assertFalse(trip.isOngoing(LocalDate.of(2026, 11, 15)))

        assertTrue(trip.isPast(LocalDate.of(2026, 11, 15)))
        assertFalse(trip.isPast(LocalDate.of(2026, 11, 14)))

        assertTrue(trip.inviteActive(1500000L))
        assertFalse(trip.inviteActive(2500000L))
    }

    @Test fun ideaVoteCalculations() {
        val votes = mapOf("u1" to VoteKind.MUST, "u2" to VoteKind.UP, "u3" to VoteKind.DOWN)
        val idea = Idea(
            id = "i1", type = IdeaType.PLACE, title = "Fushimi Inari", subtitle = "Shrine", country = "Japan",
            lat = 34.9, lng = 135.7, category = Category.LANDMARK, tags = setOf("shrine"),
            createdBy = "u1", createdByName = "Alex", createdAt = 1000L, votes = votes,
            status = IdeaStatus.OPEN, commentCount = 2,
        )

        assertEquals(2, idea.ups); assertEquals(1, idea.downs)
        assertEquals(1, idea.musts); assertEquals(3 + 1 - 2, idea.score)
    }

    @Test fun itineraryHelpers() {
        val stop = ItineraryStop(
            key = "d1-s0-a8f3", name = "Senso-ji", lat = 35.7, lng = 139.7, category = Category.LANDMARK,
            arrive = 540, start = 550, leave = 630, travelMinutes = 10, opensAt = 360, closesAt = 1020,
            hoursEstimated = false, pDisrupted = 0.05, pDelay = 0.03, pClosed = 0.01, pRainGivenDisrupted = 0.1,
            risk = "low", reason = "clear day", optional = false, ideaId = "i1",
        )
        val day = ItineraryDay(
            date = LocalDate.of(2026, 11, 10), rainProbability = 0.1, rainKnown = true, verified = true,
            km = 8.5, evolutionBest = emptyList(), evolutionAverage = emptyList(), stops = listOf(stop),
        )
        val itin = Itinerary(
            version = 1, generatedAt = 1000L, generatedById = "u1", generatedByName = "Alex", locked = false,
            dayStart = 540, origin = AltPlace("Hotel", 35.7, 139.7, Category.LANDMARK), days = listOf(day),
            alternates = emptyList(), notes = listOf("Good shoes"), gaImprovementPercent = 14.2,
        )

        assertEquals(8.5, itin.totalKm, 1e-6); assertTrue(itin.verified)
        assertNotNull(itin.stop("d1-s0-a8f3")); assertNull(itin.stop("nonexistent"))
    }

    @Test fun proposalSummary() {
        val remove = Proposal(
            id = "p1", type = ProposalType.REMOVE_STOP, stopKey = "k1", stopName = "Market",
            toDay = null, reason = "Crowded", createdBy = "u1", createdByName = "Alex",
            status = ProposalStatus.PENDING, createdAt = 1000L, resolvedByName = null,
        )
        assertEquals("Remove Market", remove.summary)

        val move = remove.copy(id = "p2", type = ProposalType.MOVE_STOP, toDay = 2)
        assertEquals("Move Market to day 3", move.summary)
    }
}
