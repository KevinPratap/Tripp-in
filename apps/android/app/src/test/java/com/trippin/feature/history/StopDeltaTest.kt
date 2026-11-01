package com.trippin.feature.history

import com.trippin.core.network.ItineraryVersionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StopDeltaTest {

    private val newestFirst = listOf(
        ItineraryVersionDto(version = 4, activitiesCount = 13),
        ItineraryVersionDto(version = 3, activitiesCount = 14),
        ItineraryVersionDto(version = 2, activitiesCount = 12),
        ItineraryVersionDto(version = 1, activitiesCount = 12)
    )

    @Test
    fun `each version is compared with the one saved before it`() {
        assertEquals("1 fewer stop than before", stopDelta(newestFirst, 0))
        assertEquals("2 more stops than before", stopDelta(newestFirst, 1))
        assertEquals("Same number of stops as before", stopDelta(newestFirst, 2))
    }

    @Test
    fun `the first plan has nothing to compare with`() {
        assertNull(stopDelta(newestFirst, 3))
    }
}
