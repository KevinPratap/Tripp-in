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
        return toWindow(m)
    }

    private fun toWindow(m: MatchResult): Pair<Int, Int> {
        val (h1, m1, h2, m2) = m.destructured
        val open = h1.toInt() * 60 + m1.toInt()
        var close = h2.toInt() * 60 + m2.toInt()
        if (close <= open) close += 24 * 60 // past midnight, e.g. 18:00-02:00
        return open to minOf(close, 24 * 60)
    }

    /** Hours per weekday (1 = Monday … 7 = Sunday); a missing day means closed. */
    data class WeeklyHours(val byDay: Map<Int, Pair<Int, Int>>) {
        val closedDays: Set<Int> get() = (1..7).toSet() - byDay.keys
    }

    private val dayNames = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
    private val dayPart = Regex("""^((?:Mo|Tu|We|Th|Fr|Sa|Su)(?:\s*-\s*(?:Mo|Tu|We|Th|Fr|Sa|Su))?(?:\s*,\s*(?:Mo|Tu|We|Th|Fr|Sa|Su)(?:\s*-\s*(?:Mo|Tu|We|Th|Fr|Sa|Su))?)*)\s+(.*)$""")

    /**
     * Parses the common subset of OSM `opening_hours`: rules separated by `;`, each an optional
     * day list ("Mo-Fr", "Sa,Su", "Tu-Su") followed by a time range or `off`, e.g.
     * `Mo off; Tu-Su 10:00-18:00`. Later rules override earlier ones, as in the OSM spec.
     * Returns null for anything it cannot read with confidence (public holidays, sunrise, months…).
     */
    fun parseWeekly(value: String?): WeeklyHours? {
        if (value.isNullOrBlank()) return null
        val v = value.trim()
        if (v == "24/7") return WeeklyHours((1..7).associateWith { 0 to 24 * 60 })
        val result = HashMap<Int, Pair<Int, Int>>()
        var sawRule = false
        for (raw in v.split(';').map { it.trim() }.filter { it.isNotEmpty() }) {
            if (raw.startsWith("PH")) continue // public-holiday rules: ignore rather than guess
            val dm = dayPart.find(raw)
            val days: Set<Int>
            val rest: String
            if (dm != null) {
                days = expandDays(dm.groupValues[1])
                rest = dm.groupValues[2].trim()
            } else {
                days = (1..7).toSet()
                rest = raw
            }
            when {
                rest == "off" || rest == "closed" -> days.forEach { result.remove(it) }
                else -> {
                    // "10:00-14:00,17:00-22:00" → treat as one window from first open to last close.
                    val windows = range.findAll(rest).map(::toWindow).toList()
                    if (windows.isEmpty()) return null
                    val leftover = range.replace(rest, "").replace(",", "").trim()
                    if (leftover.isNotEmpty()) return null
                    val w = windows.minOf { it.first } to windows.maxOf { it.second }
                    days.forEach { result[it] = w }
                }
            }
            sawRule = true
        }
        return if (sawRule) WeeklyHours(result) else null
    }

    private fun expandDays(spec: String): Set<Int> = spec.split(',').flatMap { part ->
        val bits = part.split('-').map { it.trim() }
        val a = dayNames.indexOf(bits[0]) + 1
        if (bits.size == 1) listOf(a) else {
            val b = dayNames.indexOf(bits[1]) + 1
            if (a <= b) (a..b).toList() else ((a..7) + (1..b)).toList() // wraps, e.g. Fr-Mo
        }
    }.toSet()

    fun tagsOf(tags: Map<String, String>): Set<String> = buildSet {
        fun yes(k: String) = tags[k] in setOf("yes", "only", "limited")
        if (yes("diet:vegetarian") || yes("diet:vegan")) add(Place.TAG_VEGETARIAN)
        if (yes("diet:vegan")) add(Place.TAG_VEGAN)
        if (tags["wheelchair"] == "yes") add(Place.TAG_WHEELCHAIR)
        if (tags["amenity"] in setOf("bar", "pub", "nightclub")) add(Place.TAG_ALCOHOL)
    }

    fun toPlace(id: String, lat: Double, lng: Double, tags: Map<String, String>): Place? {
        val name = tags["name:en"] ?: tags["name"] ?: return null
        val cat = category(tags) ?: return null
        val (visit, dOpen, dClose) = defaults(cat)
        val weekly = parseWeekly(tags["opening_hours"])
        val typical = weekly?.byDay?.values?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key
        return Place(
            id = id,
            name = name,
            lat = lat,
            lng = lng,
            category = cat,
            visitMinutes = visit,
            opensAt = typical?.first ?: dOpen,
            closesAt = typical?.second ?: dClose,
            hoursEstimated = weekly == null,
            closedDays = weekly?.closedDays ?: emptySet(),
            weeklyHours = weekly?.byDay?.filterValues { it != typical } ?: emptyMap(),
            tags = tagsOf(tags),
        )
    }
}
