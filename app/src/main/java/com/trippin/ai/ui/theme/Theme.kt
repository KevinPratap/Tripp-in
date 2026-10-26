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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.trippin.ai.R

/**
 * Trippin' v4 palette. Soft neutral surfaces, near-black ink, and one brand gradient
 * (coral to violet) used for actions, votes and story rings.
 *
 * The names are kept from v3 so every screen picks up the new look. The values are read through
 * getters backed by Compose state, so flipping [dark] recomposes everything that uses them.
 * Note for dark mode: Ink and Paper swap roles (Ink becomes the light text colour), which keeps
 * every "Paper text on Ink background" pairing readable without touching each screen.
 */
object Trip {
    var dark by mutableStateOf(false)

    val Ink: Color get() = if (dark) Color(0xFFF4F4F8) else Color(0xFF14141A)
    val Paper: Color get() = if (dark) Color(0xFF0E0E12) else Color(0xFFF6F6F9)
    val PaperDeep: Color get() = if (dark) Color(0xFF1D1D26) else Color(0xFFE9E9EF)
    val Card: Color get() = if (dark) Color(0xFF17171E) else Color(0xFFFFFFFF)
    val Muted: Color get() = if (dark) Color(0xFF9C9CB0) else Color(0xFF6A6A7B)
    val OnInkMuted: Color get() = if (dark) Color(0xFF5A5A6E) else Color(0xFFA9A9BC)
    /** Hairline used instead of heavy borders. */
    val Line: Color get() = if (dark) Color(0x24FFFFFF) else Color(0x1A14141A)

    val Signal = Color(0xFFFF3D6E)
    val Cobalt = Color(0xFF6C4DFF)
    val Mint = Color(0xFF12A66A)
    val Amber = Color(0xFFE08A00)
    /** Warm highlight reserved for MUST votes. */
    val Gold = Color(0xFFFFC53D)
    val Danger = Color(0xFFD92D4A)

    val BrandColors = listOf(Signal, Cobalt)
    val Brand: Brush get() = Brush.linearGradient(BrandColors)

    /** Avatar colours, chosen per user id so a friend keeps the same colour everywhere. */
    val AvatarColors = listOf(Color(0xFFFF3D6E), Color(0xFF6C4DFF), Color(0xFF12A66A), Color(0xFFE08A00), Color(0xFF0F8FD9), Color(0xFFC13FD6))
}

val Display = FontFamily(
    Font(R.font.display_extrabold, FontWeight.ExtraBold),
    Font(R.font.display_black, FontWeight.Black),
)
val Body = FontFamily(
    Font(R.font.body_regular, FontWeight.Normal),
    Font(R.font.body_semibold, FontWeight.SemiBold),
    Font(R.font.body_extrabold, FontWeight.ExtraBold),
)
val Mono = FontFamily(Font(R.font.mono_medium, FontWeight.Medium))

private val TrippinTypography = Typography(
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 40.sp, lineHeight = 42.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.02).em),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = (-0.01).em),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, lineHeight = 27.sp),
    headlineSmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 23.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, lineHeight = 24.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp),
    labelMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, letterSpacing = 0.01.em),
    labelSmall = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.01.em),
)

private fun lightScheme() = lightColorScheme(
    primary = Trip.Signal, onPrimary = Color.White,
    secondary = Trip.Cobalt, onSecondary = Color.White,
    background = Color(0xFFF6F6F9), onBackground = Color(0xFF14141A),
    surface = Color(0xFFF6F6F9), onSurface = Color(0xFF14141A),
    surfaceVariant = Color(0xFFE9E9EF), onSurfaceVariant = Color(0xFF6A6A7B),
    outline = Color(0x3314141A), error = Trip.Danger,
)

private fun darkScheme() = darkColorScheme(
    primary = Trip.Signal, onPrimary = Color.White,
    secondary = Trip.Cobalt, onSecondary = Color.White,
    background = Color(0xFF0E0E12), onBackground = Color(0xFFF4F4F8),
    surface = Color(0xFF0E0E12), onSurface = Color(0xFFF4F4F8),
    surfaceVariant = Color(0xFF1D1D26), onSurfaceVariant = Color(0xFF9C9CB0),
    outline = Color(0x40FFFFFF), error = Trip.Danger,
)

@Composable
fun TrippinTheme(content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    // Set before anything reads it, so the very first frame already has the right colours.
    Trip.dark = isDark
    MaterialTheme(colorScheme = if (isDark) darkScheme() else lightScheme(), typography = TrippinTypography, content = content)
}
