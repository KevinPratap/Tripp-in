package com.trippin.core.map

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attractions
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Museum
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

/** How each kind of place from the explore endpoints looks: a name, a colour for its dot, an icon. */
enum class PlaceKind(val key: String, val label: String, val plural: String, val color: Color, val icon: ImageVector) {
    COFFEE("coffee", "Coffee", "Coffee", Color(0xFF8B5A2B), Icons.Default.LocalCafe),
    FOOD("food", "Food", "Food", Color(0xFFE11D48), Icons.Default.Restaurant),
    SWEETS("sweets", "Sweets", "Sweets", Color(0xFFDB2777), Icons.Default.Cake),
    DRINKS("drinks", "Drinks", "Drinks", Color(0xFF7C3AED), Icons.Default.LocalBar),
    SIGHT("sight", "Sight", "Sights", Color(0xFF18181B), Icons.Default.Attractions),
    MUSEUM("museum", "Museum", "Museums", Color(0xFF0369A1), Icons.Default.Museum),
    VIEWPOINT("viewpoint", "Viewpoint", "Views", Color(0xFF0D9488), Icons.Default.Landscape),
    PARK("park", "Park", "Parks", Color(0xFF15803D), Icons.Default.Park),
    MARKET("market", "Market", "Markets", Color(0xFFD97706), Icons.Default.Storefront);

    companion object {
        fun of(key: String): PlaceKind = entries.firstOrNull { it.key == key } ?: SIGHT
    }
}

/** "350 m" or "1.2 km". */
fun distanceLabel(meters: Int): String =
    if (meters < 1000) "${((meters / 10.0).roundToInt() * 10).coerceAtLeast(10)} m"
    else String.format(Locale.US, "%.1f km", meters / 1000.0)

/**
 * A walking time worked out from straight-line distance: streets add roughly a quarter to the
 * distance, and a relaxed pace is about 75 m a minute. It is an estimate and is always shown as
 * "about"; real walking times come from the routing service.
 */
fun estimatedWalkMinutes(straightMeters: Int): Int = ceil(straightMeters * 1.25 / 75.0).toInt().coerceAtLeast(1)

fun walkLabel(straightMeters: Int): String = "About ${estimatedWalkMinutes(straightMeters)} min walk"
