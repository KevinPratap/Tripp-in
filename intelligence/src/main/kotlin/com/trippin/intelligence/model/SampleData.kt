package com.trippin.intelligence.model

/**
 * An offline demo city (South Mumbai) so the app and the tests work with no network.
 * Coordinates and hours are approximate — check them before a live demo.
 */
object SampleData {
    val hotel = Place("hotel", "Your stay · Churchgate", 18.9322, 72.8264, Category.LANDMARK, 0)

    val mumbai = listOf(
        Place("csmvs", "CSMVS Museum", 18.9269, 72.8326, Category.MUSEUM, 120, 10 * 60 + 15, 18 * 60),
        Place("jehangir", "Jehangir Art Gallery", 18.9272, 72.8317, Category.MUSEUM, 60, 11 * 60, 19 * 60),
        Place("gateway", "Gateway of India", 18.9220, 72.8347, Category.LANDMARK, 45),
        Place("cst", "Chhatrapati Shivaji Terminus", 18.9398, 72.8355, Category.LANDMARK, 40),
        Place("britannia", "Britannia & Co.", 18.9346, 72.8384, Category.FOOD, 60, 12 * 60, 16 * 60),
        Place("leopold", "Leopold Cafe", 18.9229, 72.8317, Category.FOOD, 60, 7 * 60 + 30, 23 * 60 + 30),
        Place("crawford", "Crawford Market", 18.9476, 72.8340, Category.SHOPPING, 75, 11 * 60, 20 * 60, hoursEstimated = true),
        Place("causeway", "Colaba Causeway", 18.9178, 72.8292, Category.SHOPPING, 60, 11 * 60, 21 * 60, hoursEstimated = true),
        Place("marine", "Marine Drive", 18.9432, 72.8235, Category.PARK, 45),
        Place("hanging", "Hanging Gardens", 18.9567, 72.8053, Category.PARK, 45, 5 * 60, 21 * 60),
        Place("chowpatty", "Girgaon Chowpatty", 18.9548, 72.8147, Category.FOOD, 45, 16 * 60, 23 * 60, hoursEstimated = true),
        Place("hajiali", "Haji Ali Dargah", 18.9827, 72.8089, Category.LANDMARK, 60, 5 * 60 + 30, 22 * 60),
    )

    fun byId(id: String): Place = mumbai.first { it.id == id }
}
