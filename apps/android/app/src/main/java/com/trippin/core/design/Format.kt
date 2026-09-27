package com.trippin.core.design

import java.util.Locale

/**
 * Money and place formatting, kept honest.
 *
 * Every amount the app prints is shown in the currency it was actually sent in, with the symbol a
 * traveller reads. Nothing here invents a rate, converts between currencies, or borrows the trip
 * currency for an amount that arrived without one.
 */

/** The currencies the planner offers, with the symbol a traveller actually reads. */
val TrippinCurrencies: List<Pair<String, String>> = listOf(
    "INR" to "₹",
    "EUR" to "€",
    "GBP" to "£",
    "JPY" to "¥",
    "USD" to "$"
)

/** The symbol for a currency code, or the code itself when there is no symbol for it. */
fun currencySymbol(code: String?): String {
    if (code.isNullOrBlank()) return ""
    val upper = code.trim().uppercase()
    return TrippinCurrencies.firstOrNull { it.first == upper }?.second ?: upper
}

/** Parse a typed amount, tolerating thousands separators. Null when it is not a positive number. */
fun parseStatedAmount(raw: String): Double? {
    if (raw.isBlank()) return null
    val cleaned = raw.replace(",", "").trim()
    return cleaned.toDoubleOrNull()
}

/**
 * Format an amount in a stated currency: "₹1,200" or "€45.50". Whole numbers drop the
 * decimals; JPY has no minor unit so it is always whole.
 */
fun formatStatedAmount(value: Double, currency: String?): String {
    val symbol = currencySymbol(currency)
    val whole = value % 1.0 == 0.0 || currency?.uppercase() == "JPY"
    val number = if (whole) {
        String.format(Locale.US, "%,d", value.toLong())
    } else {
        String.format(Locale.US, "%,.2f", value)
    }
    return if (symbol.isEmpty()) number else "$symbol$number"
}

/**
 * Whether an address is something a traveller standing on a pavement could actually walk to. A bare
 * city name, or a prefecture repeated twice with no street, is not printed as a navigable address:
 * it needs more than one comma-separated part and either a street number or a reasonable length.
 */
fun isStreetLevelAddress(raw: String): Boolean {
    val address = raw.trim()
    if (address.length < 6) return false
    val parts = address.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return false
    val distinct = parts.map { it.lowercase() }.distinct()
    if (distinct.size < 2) return false
    val hasNumber = address.any { it.isDigit() }
    return hasNumber || address.length >= 14
}

/**
 * The source named on a photo, read off the URL host. A host the app does not recognise gets no
 * credit at all rather than a claim the picture is something it is not.
 */
fun photoCredit(url: String): String? {
    val host = runCatching { java.net.URI(url).host.orEmpty() }.getOrDefault("").lowercase()
    return when {
        host.contains("wikimedia") || host.contains("wikipedia") -> "Wikimedia"
        host.contains("openstreetmap") || host.contains("osm") -> "OpenStreetMap"
        host.contains("mapillary") -> "Mapillary"
        else -> null
    }
}
