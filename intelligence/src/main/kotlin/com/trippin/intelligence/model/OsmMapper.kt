package com.trippin.intelligence.model

/**
 * Turns an OpenStreetMap element (its tags) into a [Place]. Kept free of Android so it is
 * unit-tested with the algorithms.
 */
object OsmMapper {

    fun category(tags: Map<String, String>): Category? {
        val tourism = tags["tourism"]
        val amenity = tags["amenity"]
        val leisure = tags["leisure"]
        return when {
            tourism == "museum" || tourism == "gallery" -> Category.MUSEUM
            amenity in setOf("bar", "pub", "nightclub") -> Category.NIGHTLIFE
            amenity in setOf("restaurant", "cafe", "fast_food", "food_court", "ice_cream") -> Category.FOOD
            leisure in setOf("park", "garden", "nature_reserve") || tags["natural"] == "beach" -> Category.PARK
            tags["shop"] == "mall" || amenity == "marketplace" -> Category.SHOPPING
            tourism in setOf("attraction", "viewpoint", "artwork") || tags.containsKey("historic") -> Category.LANDMARK
            else -> null
        }
    }

    /** Typical visit length and hours, used when OSM publishes nothing. */
    fun defaults(c: Category): Triple<Int, Int, Int> = when (c) {
        Category.MUSEUM -> Triple(90, 10 * 60, 18 * 60)
        Category.FOOD -> Triple(60, 11 * 60, 22 * 60)
        Category.PARK -> Triple(45, 6 * 60, 20 * 60)
        Category.LANDMARK -> Triple(45, 8 * 60, 20 * 60)
        Category.SHOPPING -> Triple(75, 11 * 60, 21 * 60)
        Category.NIGHTLIFE -> Triple(90, 19 * 60, 24 * 60)
    }

    private val range = Regex("""(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})""")

    /**
     * Reads the first time range in an `opening_hours` value, e.g. "Tu-Su 10:00-18:00".
     * Returns null when the value is missing or too complex to trust, so the caller falls back
     * to category defaults and marks the hours as estimated.
     */
    fun parseHours(value: String?): Pair<Int, Int>? {
        if (value.isNullOrBlank()) return null
        if (value.trim() == "24/7") return 0 to 24 * 60
        val m = range.find(value) ?: return null
        val (h1, m1, h2, m2) = m.destructured
        val open = h1.toInt() * 60 + m1.toInt()
        var close = h2.toInt() * 60 + m2.toInt()
        if (close <= open) close += 24 * 60 // past midnight, e.g. 18:00-02:00
        return open to minOf(close, 24 * 60)
    }

    fun toPlace(id: String, lat: Double, lng: Double, tags: Map<String, String>): Place? {
        val name = tags["name:en"] ?: tags["name"] ?: return null
        val cat = category(tags) ?: return null
        val (visit, dOpen, dClose) = defaults(cat)
        val parsed = parseHours(tags["opening_hours"])
        return Place(
            id = id,
            name = name,
            lat = lat,
            lng = lng,
            category = cat,
            visitMinutes = visit,
            opensAt = parsed?.first ?: dOpen,
            closesAt = parsed?.second ?: dClose,
            hoursEstimated = parsed == null,
        )
    }
}
