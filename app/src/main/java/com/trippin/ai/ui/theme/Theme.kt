package com.trippin.ai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.trippin.ai.R

/**
 * Trippin' luxe palette: warm white surfaces, deep ink, and one metal accent (champagne gold)
 * used for hairlines, highlights and the logo dot. Colour is otherwise kept out of the way so
 * photos and places carry the screen.
 *
 * The names are kept from earlier versions so every screen picks up the look. Values are read
 * through getters backed by Compose state, so flipping [dark] recomposes everything that uses
 * them. In dark mode Ink and Paper swap roles (Ink becomes the light text colour), which keeps
 * every "Paper text on Ink background" pairing readable without touching each screen.
 */
object Trip {
    var dark by mutableStateOf(false)

    val Ink: Color get() = if (dark) Color(0xFFF4F1EA) else Color(0xFF17181C)
    val Paper: Color get() = if (dark) Color(0xFF0F0F11) else Color(0xFFFBFAF7)
    val PaperDeep: Color get() = if (dark) Color(0xFF1C1C20) else Color(0xFFF1EEE7)
    val Card: Color get() = if (dark) Color(0xFF17171A) else Color(0xFFFFFFFF)
    val Muted: Color get() = if (dark) Color(0xFFA19D93) else Color(0xFF6B675E)
    val OnInkMuted: Color get() = if (dark) Color(0xFF5E5B54) else Color(0xFFB2ADA2)
    /** Hairline used instead of heavy borders. */
    val Line: Color get() = if (dark) Color(0x26FFFFFF) else Color(0x1F17181C)

    /** Champagne gold. Readable on white for icons and large text; [GoldText] for small text. */
    val Signal: Color get() = if (dark) Color(0xFFD9B878) else Color(0xFFB08848)
    val GoldText: Color get() = if (dark) Color(0xFFD9B878) else Color(0xFF8A6A34)
    val Champagne = Color(0xFFEADBB8)
    /** Secondary accent for data: a deep slate blue. */
    val Cobalt: Color get() = if (dark) Color(0xFF8EA4D4) else Color(0xFF3B4C72)
    val Mint = Color(0xFF2F7D5C)
    val Amber = Color(0xFFB9791C)
    /** Warm highlight reserved for MUST votes. */
    val Gold = Color(0xFFE2BE72)
    val Danger = Color(0xFFA83A48)

    /** Filled surfaces (primary buttons, trip headers): deep ink with a faint lift, not a loud gradient. */
    val Brand: Brush get() = Brush.linearGradient(listOf(Color(0xFF2B2D34), Color(0xFF15161A)))
    /** The gilt edge: used on rings, the logo dot and small highlights. */
    val Gilt: Brush get() = Brush.linearGradient(listOf(Color(0xFFEBD9AE), Color(0xFFB08848), Color(0xFFD9B878)))

    /** Avatar colours, chosen per user id so a friend keeps the same colour everywhere. Muted jewel tones. */
    val AvatarColors = listOf(Color(0xFF8A5A44), Color(0xFF3B4C72), Color(0xFF2F7D5C), Color(0xFFB08848), Color(0xFF5B4B7A), Color(0xFF9C4A5A))
}

/** Headlines: a high-contrast old-style serif. */
val Display = FontFamily(
    Font(R.font.serif_medium, FontWeight.Medium),
    Font(R.font.serif_semibold, FontWeight.SemiBold),
    Font(R.font.serif_medium_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.serif_semibold_italic, FontWeight.SemiBold, FontStyle.Italic),
)
/** Everything else: a clean geometric sans. */
val Body = FontFamily(
    Font(R.font.sans_regular, FontWeight.Normal),
    Font(R.font.sans_medium, FontWeight.Medium),
    Font(R.font.sans_semibold, FontWeight.SemiBold),
    Font(R.font.sans_bold, FontWeight.Bold),
)
/** Monospace for figures and codes. */
val Mono = FontFamily.Monospace

private val TrippinTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 46.sp, lineHeight = 48.sp, letterSpacing = (-0.015).em),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 38.sp, lineHeight = 41.sp, letterSpacing = (-0.01).em),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 35.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 31.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 23.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 27.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 13.5.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.01.em),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, letterSpacing = 0.01.em),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 11.5.sp, letterSpacing = 0.01.em),
)

private fun lightScheme() = lightColorScheme(
    primary = Color(0xFF17181C), onPrimary = Color.White,
    secondary = Color(0xFFB08848), onSecondary = Color.White,
    background = Color(0xFFFBFAF7), onBackground = Color(0xFF17181C),
    surface = Color(0xFFFBFAF7), onSurface = Color(0xFF17181C),
    surfaceVariant = Color(0xFFF1EEE7), onSurfaceVariant = Color(0xFF6B675E),
    outline = Color(0x3317181C), error = Trip.Danger,
)

private fun darkScheme() = darkColorScheme(
    primary = Color(0xFFF4F1EA), onPrimary = Color(0xFF17181C),
    secondary = Color(0xFFD9B878), onSecondary = Color(0xFF17181C),
    background = Color(0xFF0F0F11), onBackground = Color(0xFFF4F1EA),
    surface = Color(0xFF0F0F11), onSurface = Color(0xFFF4F1EA),
    surfaceVariant = Color(0xFF1C1C20), onSurfaceVariant = Color(0xFFA19D93),
    outline = Color(0x40FFFFFF), error = Trip.Danger,
)

@Composable
fun TrippinTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    // Set before anything reads it, so the very first frame already has the right colours.
    Trip.dark = isDark
    MaterialTheme(colorScheme = if (isDark) darkScheme() else lightScheme(), typography = TrippinTypography, content = content)
}
