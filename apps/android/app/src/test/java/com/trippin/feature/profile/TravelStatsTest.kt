package com.trippin.feature.profile

import com.trippin.core.network.SavedTripSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Test

class TravelStatsTest {

    @Test
    fun `counts trips, distinct places and planned days from the trips themselves`() {
        val trips = listOf(
            SavedTripSummaryDto(id = "1", destinationName = "Lisbon, Portugal", dayCount = 4),
            SavedTripSummaryDto(id = "2", destinationName = "lisbon", dayCount = 2),
            SavedTripSummaryDto(id = "3", destinationName = "Kyoto, Japan", dayCount = 6),
            SavedTripSummaryDto(id = "4", destinationName = "", dayCount = 0)
        )
        assertEquals(TravelStats(trips = 4, places = 2, daysPlanned = 12), travelStats(trips))
    }

    @Test
    fun `no trips is all zeros`() {
        assertEquals(TravelStats(0, 0, 0), travelStats(emptyList()))
    }
}
