package com.trippin.intelligence

import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Geo
import com.trippin.intelligence.model.Place
import com.trippin.intelligence.model.asClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoTest {

    @Test
    fun haversineKmZeroForIdenticalCoordinates() {
        assertEquals(0.0, Geo.haversineKm(18.9220, 72.8347, 18.9220, 72.8347), 1e-9)
        assertEquals(0.0, Geo.haversineKm(0.0, 0.0, 0.0, 0.0), 1e-9)
    }

    @Test
    fun haversineKmIsSymmetric() {
        val d1 = Geo.haversineKm(18.9220, 72.8347, 18.9432, 72.8230)
        val d2 = Geo.haversineKm(18.9432, 72.8230, 18.9220, 72.8347)
        assertEquals(d1, d2, 1e-9)
    }

    @Test
    fun haversineKmCalculatesKnownGeographicDistance() {
        // Gateway of India (18.9220, 72.8347) to Marine Drive (18.9432, 72.8230) ~ 2.64 km
        val distance = Geo.haversineKm(18.9220, 72.8347, 18.9432, 72.8230)
        assertEquals(2.64, distance, 0.05)
    }

    @Test
    fun haversineKmExtremeDistancesOnSphere() {
        // Equator quarter circumference: (0, 0) to (0, 90) = PI / 2 * 6371.0 = ~10007.54 km
        val quarter = Geo.haversineKm(0.0, 0.0, 0.0, 90.0)
        assertEquals(Math.PI / 2 * 6371.0, quarter, 0.1)

        // North pole to South pole: (90, 0) to (-90, 0) = PI * 6371.0 = ~20015.09 km
        val poleToPole = Geo.haversineKm(90.0, 0.0, -90.0, 0.0)
        assertEquals(Math.PI * 6371.0, poleToPole, 0.1)
    }

    @Test
    fun distanceKmDelegatesToHaversineForPlaces() {
        val p1 = Place("1", "Gateway", 18.9220, 72.8347, Category.LANDMARK, 60)
        val p2 = Place("2", "Marine Drive", 18.9432, 72.8230, Category.LANDMARK, 45)
        assertEquals(Geo.haversineKm(18.9220, 72.8347, 18.9432, 72.8230), Geo.distanceKm(p1, p2), 1e-9)
    }

    @Test
    fun travelMinutesUsesWalkingPaceUnderOnePointFiveKm() {
        // km < 1.5: (km / 4.5 * 60).toInt() + 1
        assertEquals(1, Geo.travelMinutes(0.0))
        assertEquals(7, Geo.travelMinutes(0.45))
        assertEquals(14, Geo.travelMinutes(1.0))
        assertEquals(20, Geo.travelMinutes(1.49))
    }

    @Test
    fun travelMinutesSwitchesToTransitPaceFromOnePointFiveKm() {
        // km >= 1.5: (km / 18.0 * 60 + 8).toInt()
        assertEquals(13, Geo.travelMinutes(1.5))
        assertEquals(18, Geo.travelMinutes(3.0))
        assertEquals(38, Geo.travelMinutes(9.0))
    }

    @Test
    fun asClockFormatsMinutesAfterMidnight() {
        assertEquals("00:00", 0.asClock())
        assertEquals("00:05", 5.asClock())
        assertEquals("09:00", 540.asClock())
        assertEquals("10:35", 635.asClock())
        assertEquals("23:59", 1439.asClock())
        assertEquals("24:00", 1440.asClock())
    }

    @Test
    fun isOpenOnChecksClosedDays() {
        val place = Place(
            id = "p1",
            name = "Test Place",
            lat = 18.9,
            lng = 72.8,
            category = Category.MUSEUM,
            visitMinutes = 60,
            closedDays = setOf(1, 7),
        )
        assertFalse(place.isOpenOn(1))
        assertTrue(place.isOpenOn(2))
        assertTrue(place.isOpenOn(3))
        assertTrue(place.isOpenOn(4))
        assertTrue(place.isOpenOn(5))
        assertTrue(place.isOpenOn(6))
        assertFalse(place.isOpenOn(7))
    }

    @Test
    fun forDayReturnsNullWhenPlaceIsClosed() {
        val place = Place(
            id = "p1",
            name = "Test Place",
            lat = 18.9,
            lng = 72.8,
            category = Category.MUSEUM,
            visitMinutes = 60,
            closedDays = setOf(1),
        )
        assertNull(place.forDay(1))
    }

    @Test
    fun forDayReturnsCopyWithWeeklyHoursOverride() {
        val place = Place(
            id = "p1",
            name = "Test Place",
            lat = 18.9,
            lng = 72.8,
            category = Category.MUSEUM,
            visitMinutes = 60,
            opensAt = 600,
            closesAt = 1080,
            weeklyHours = mapOf(2 to Pair(660, 1200)),
        )
        val tuesday = place.forDay(2)
        assertNotNull(tuesday)
        assertEquals(660, tuesday!!.opensAt)
        assertEquals(1200, tuesday.closesAt)
        assertEquals("p1", tuesday.id)
    }

    @Test
    fun forDayReturnsUnmodifiedPlaceWhenNoWeeklyOverride() {
        val place = Place(
            id = "p1",
            name = "Test Place",
            lat = 18.9,
            lng = 72.8,
            category = Category.MUSEUM,
            visitMinutes = 60,
            opensAt = 600,
            closesAt = 1080,
            weeklyHours = mapOf(2 to Pair(660, 1200)),
        )
        val wednesday = place.forDay(3)
        assertEquals(place, wednesday)
        assertEquals(600, wednesday!!.opensAt)
        assertEquals(1080, wednesday.closesAt)
    }

    @Test
    fun placeCompanionTagConstants() {
        assertEquals("vegetarian", Place.TAG_VEGETARIAN)
        assertEquals("vegan", Place.TAG_VEGAN)
        assertEquals("wheelchair", Place.TAG_WHEELCHAIR)
        assertEquals("alcohol", Place.TAG_ALCOHOL)
    }
}
