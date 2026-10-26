package com.trippin.ai.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * Trippin's own icon set: 24 unit grid, 1.6 stroke, round caps and joins, no fills.
 * One stroke weight across every icon is what makes them read as a family. Colour comes from
 * the Icon tint, so the same glyph works on white, ink or gold.
 */
object TripIcons {
    private fun icon(name: String, mirror: Boolean = false, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f, autoMirror = mirror).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 1.6f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Home = icon("Home", false, "M4 11 L12 4 L20 11", "M6 9.5 V19.5 H18 V9.5", "M10 19.5 V14 H14 V19.5")
    val Explore = icon("Explore", false, "M3 12 A9 9 0 1 0 21 12 A9 9 0 1 0 3 12 Z", "M15.5 8.5 L13.5 13.5 L8.5 15.5 L10.5 10.5 Z")
    val Luggage = icon(
        "Luggage", false,
        "M5 8 H19 A1.5 1.5 0 0 1 20.5 9.5 V18 A1.5 1.5 0 0 1 19 19.5 H5 A1.5 1.5 0 0 1 3.5 18 V9.5 A1.5 1.5 0 0 1 5 8 Z",
        "M9 8 V6 A1 1 0 0 1 10 5 H14 A1 1 0 0 1 15 6 V8", "M3.5 13 H20.5",
    )
    val Style = icon(
        "Style", false,
        "M6 7 H15 A1.5 1.5 0 0 1 16.5 8.5 V19 A1.5 1.5 0 0 1 15 20.5 H6 A1.5 1.5 0 0 1 4.5 19 V8.5 A1.5 1.5 0 0 1 6 7 Z",
        "M8 4 H18 A1.5 1.5 0 0 1 19.5 5.5 V15.5",
    )
    val Person = icon("Person", false, "M8.5 8 A3.5 3.5 0 1 0 15.5 8 A3.5 3.5 0 1 0 8.5 8 Z", "M5 19.5 C5 15.8 8 14 12 14 C16 14 19 15.8 19 19.5")
    val Add = icon("Add", false, "M12 5 V19", "M5 12 H19")
    val Close = icon("Close", false, "M6 6 L18 18", "M18 6 L6 18")
    val Check = icon("Check", false, "M5 12.5 L10 17.5 L19 7")
    val Back = icon("Back", true, "M15 5 L8 12 L15 19")
    val Forward = icon("Forward", true, "M5 12 H19", "M13 6 L19 12 L13 18")
    val Send = icon("Send", true, "M4 12 L20 4 L14 20 L11.5 13.5 Z", "M11.5 13.5 L20 4")
    val Search = icon("Search", false, "M4 10.5 A6.5 6.5 0 1 0 17 10.5 A6.5 6.5 0 1 0 4 10.5 Z", "M15.5 15.5 L20 20")
    val Place = icon(
        "Place", false,
        "M12 21 C12 21 5 14.5 5 9.5 A7 7 0 0 1 19 9.5 C19 14.5 12 21 12 21 Z",
        "M9.5 9.5 A2.5 2.5 0 1 0 14.5 9.5 A2.5 2.5 0 1 0 9.5 9.5 Z",
    )
    val ThumbUp = icon(
        "ThumbUp", false,
        "M4.5 11 H7.5 V19.5 H4.5 Z",
        "M7.5 11 L11 4.5 C12.5 4.5 13.5 5.7 13 7.2 L12.3 9.5 H18.2 A1.8 1.8 0 0 1 19.9 11.7 L18.7 17.7 A2 2 0 0 1 16.7 19.5 H7.5",
    )
    val Restore = icon("Restore", false, "M3 12 A9 9 0 1 0 12 3 A9.75 9.75 0 0 0 5.26 5.74 L3 8", "M3 3 V8 H8")
    val Lock = icon(
        "Lock", false,
        "M6 11 H18 A1.5 1.5 0 0 1 19.5 12.5 V19 A1.5 1.5 0 0 1 18 20.5 H6 A1.5 1.5 0 0 1 4.5 19 V12.5 A1.5 1.5 0 0 1 6 11 Z",
        "M8 11 V8 A4 4 0 0 1 16 8 V11",
    )
    val Chat = icon("Chat", false, "M5 5 H19 A1.5 1.5 0 0 1 20.5 6.5 V15.5 A1.5 1.5 0 0 1 19 17 H10 L6 20.5 V17 H5 A1.5 1.5 0 0 1 3.5 15.5 V6.5 A1.5 1.5 0 0 1 5 5 Z")
    val Star = icon("Star", false, "M12 3.8 L14.4 9 L20 9.6 L15.8 13.4 L17 19 L12 16.1 L7 19 L8.2 13.4 L4 9.6 L9.6 9 Z")
    val ShoppingBag = icon(
        "ShoppingBag", false,
        "M5.5 8 H18.5 L19.5 19.5 A1 1 0 0 1 18.5 20.5 H5.5 A1 1 0 0 1 4.5 19.5 Z",
        "M9 10.5 V7 A3 3 0 0 1 15 7 V10.5",
    )
    val Settings = icon(
        "Settings", false,
        "M4 7 H7", "M11 7 H20", "M4 17 H13", "M17 17 H20",
        "M7 7 A2 2 0 1 0 11 7 A2 2 0 1 0 7 7 Z", "M13 17 A2 2 0 1 0 17 17 A2 2 0 1 0 13 17 Z",
    )
    val Restaurant = icon("Restaurant", false, "M6 3.5 V8.5 A2.5 2.5 0 0 0 11 8.5 V3.5", "M8.5 3.5 V20.5", "M17 20.5 V3.5 C14.5 5 14 8 14 11 H17")
    val Park = icon(
        "Park", false,
        "M12 3.5 C16 3.5 18.5 6.5 18.5 9.5 C18.5 12.5 16 14.5 12 14.5 C8 14.5 5.5 12.5 5.5 9.5 C5.5 6.5 8 3.5 12 3.5 Z",
        "M12 14.5 V20.5", "M9 20.5 H15",
    )
    val Nightlife = icon("Nightlife", false, "M5 4.5 H19 L12 13 Z", "M12 13 V20", "M8 20 H16")
    val Map = icon("Map", false, "M3.5 6.5 L9 4.5 L15 6.5 L20.5 4.5 V17.5 L15 19.5 L9 17.5 L3.5 19.5 Z", "M9 4.5 V17.5", "M15 6.5 V19.5")
    val Edit = icon(
        "Edit", false,
        "M4.5 19.5 L5.2 15.5 L15.8 4.9 A1.8 1.8 0 0 1 18.4 4.9 L19.1 5.6 A1.8 1.8 0 0 1 19.1 8.2 L8.5 18.8 Z", "M14 6.7 L17.3 10",
    )
    val Delete = icon(
        "Delete", false,
        "M5 7 H19", "M9.5 7 V5 A1 1 0 0 1 10.5 4 H13.5 A1 1 0 0 1 14.5 5 V7",
        "M6.5 7 L7.3 19 A1.5 1.5 0 0 0 8.8 20.5 H15.2 A1.5 1.5 0 0 0 16.7 19 L17.5 7", "M10 11 V16.5", "M14 11 V16.5",
    )
    val Museum = icon(
        "Museum", false,
        "M3.5 9 L12 4 L20.5 9 Z", "M5.5 11.5 V17", "M9.5 11.5 V17", "M14.5 11.5 V17", "M18.5 11.5 V17", "M3.5 20 H20.5",
    )
    val Wallet = icon(
        "Wallet", false,
        "M4 7.5 A1.5 1.5 0 0 1 5.5 6 H17.5 V9", "M4 7.5 V18 A1.5 1.5 0 0 0 5.5 19.5 H19 A1.5 1.5 0 0 0 20.5 18 V10.5 A1.5 1.5 0 0 0 19 9 H5.5",
        "M16 14.2 A0.8 0.8 0 1 0 17.6 14.2 A0.8 0.8 0 1 0 16 14.2 Z",
    )
    val Share = icon("Share", false, "M12 15 V4", "M8 8 L12 4 L16 8", "M6 12 V19 A1 1 0 0 0 7 20 H17 A1 1 0 0 0 18 19 V12")
    val Calendar = icon(
        "Calendar", false,
        "M5 6 H19 A1.5 1.5 0 0 1 20.5 7.5 V19 A1.5 1.5 0 0 1 19 20.5 H5 A1.5 1.5 0 0 1 3.5 19 V7.5 A1.5 1.5 0 0 1 5 6 Z",
        "M3.5 10.5 H20.5", "M8 3.5 V7.5", "M16 3.5 V7.5",
    )
    val Heart = icon("Heart", false, "M12 20 C12 20 4 14.5 4 9.2 A4.2 4.2 0 0 1 12 7.4 A4.2 4.2 0 0 1 20 9.2 C20 14.5 12 20 12 20 Z")
    val Sparkle = icon("Sparkle", false, "M12 3.5 C12.8 8 14.5 9.7 19 10.5 C14.5 11.3 12.8 13 12 17.5 C11.2 13 9.5 11.3 5 10.5 C9.5 9.7 11.2 8 12 3.5 Z", "M18.5 16 V20", "M16.5 18 H20.5")
}
