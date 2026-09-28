package com.trippin.feature.map

import com.trippin.core.network.GeoPointDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteSketchTest {

    private val jeronimos = GeoPointDto(38.6979, -9.2068)
    private val tower = GeoPointDto(38.6916, -9.2160)
    private val pasteis = GeoPointDto(38.6975, -9.2033)

    @Test
    fun `north is up and every pin stays inside the box`() {
        val projected = projectRoute(listOf(jeronimos, tower, pasteis), 400f, 240f, 20f)
        projected.forEach {
            assertTrue(it.x in 20f..380f)
            assertTrue(it.y in 20f..220f)
        }
        // The tower is the southernmost stop, so it is drawn lowest.
        assertTrue(projected[1].y > projected[0].y && projected[1].y > projected[2].y)
        // And the westernmost, so it is drawn furthest left.
        assertTrue(projected[1].x < projected[0].x && projected[1].x < projected[2].x)
    }

    @Test
    fun `the route is centred and scaled evenly in both directions`() {
        val projected = projectRoute(listOf(GeoPointDto(0.0, 0.0), GeoPointDto(0.0, 1.0)), 400f, 240f, 20f)
        assertEquals(20f, projected[0].x, 0.01f)
        assertEquals(380f, projected[1].x, 0.01f)
        assertEquals(120f, projected[0].y, 0.01f)
        assertEquals(120f, projected[1].y, 0.01f)
    }

    @Test
    fun `a single spot sits in the middle`() {
        val projected = projectRoute(listOf(tower, tower), 400f, 240f, 20f)
        assertEquals(listOf(ProjectedPoint(200f, 120f), ProjectedPoint(200f, 120f)), projected)
    }

    @Test
    fun `spread is measured on the ground`() {
        val km = spreadKm(listOf(jeronimos, tower, pasteis))!!
        assertTrue("was $km", km in 1.0..1.6)
        assertEquals("About 1.3 km across", spreadLabel(1.26))
        assertEquals("About 450 m across", spreadLabel(0.47))
        assertNull(spreadKm(listOf(tower)))
    }
}
