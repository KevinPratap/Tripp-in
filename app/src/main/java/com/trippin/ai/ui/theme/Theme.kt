package com.trippin.ai.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.trippin.ai.R

/** Trippin' palette: paper and ink, one loud signal orange, one cobalt. */
object Trip {
    val Ink = Color(0xFF0F0F0E)
    val Paper = Color(0xFFF2EDE4)
    val PaperDeep = Color(0xFFE6DFD2)
    val Card = Color(0xFFFFFFFF)
    val Signal = Color(0xFFFF4F1F)
    val Cobalt = Color(0xFF2A36F0)
    val Mint = Color(0xFF1E7A4F)
    val Amber = Color(0xFFB45309)
    val Muted = Color(0xFF5E594F)
    val OnInkMuted = Color(0xFFBDB6A8)
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
    displayLarge = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 104.sp, lineHeight = 88.sp, letterSpacing = (-0.01).em),
    displayMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.Black, fontSize = 72.sp, lineHeight = 64.sp),
    displaySmall = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, lineHeight = 44.sp),
    headlineMedium = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 34.sp),
    titleLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = Body, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, letterSpacing = 0.02.em),
    labelMedium = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.08.em),
    labelSmall = TextStyle(fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.08.em),
)

private val Scheme = lightColorScheme(
    primary = Trip.Signal,
    onPrimary = Trip.Ink,
    secondary = Trip.Cobalt,
    onSecondary = Color.White,
    background = Trip.Paper,
    onBackground = Trip.Ink,
    surface = Trip.Paper,
    onSurface = Trip.Ink,
    surfaceVariant = Trip.PaperDeep,
    onSurfaceVariant = Trip.Muted,
    outline = Trip.Ink,
    error = Color(0xFFB3261E),
)

@Composable
fun TrippinTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, typography = TrippinTypography, content = content)
}
