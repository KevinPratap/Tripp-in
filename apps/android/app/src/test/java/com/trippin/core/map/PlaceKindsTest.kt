package com.trippin.core.map

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceKindsTest {

    @Test
    fun `server kinds map to their look, unknown ones read as a sight`() {
        assertEquals(PlaceKind.COFFEE, PlaceKind.of("coffee"))
        assertEquals(PlaceKind.VIEWPOINT, PlaceKind.of("viewpoint"))
        assertEquals(PlaceKind.SIGHT, PlaceKind.of("something-new"))
    }

    @Test
    fun `distances read naturally`() {
        assertEquals("350 m", distanceLabel(347))
        assertEquals("10 m", distanceLabel(2))
        assertEquals("1.2 km", distanceLabel(1234))
    }

    @Test
    fun `walking time is a padded estimate, never zero`() {
        assertEquals(1, estimatedWalkMinutes(0))
        assertEquals(5, estimatedWalkMinutes(300))
        assertEquals("About 17 min walk", walkLabel(1000))
    }
}
