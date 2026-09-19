package com.trippin.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.trippin.R

/**
 * Display face for headlines and titles, body face for everything else. Same pairing as the web
 * app (Fraunces display, Nunito body) so the phone and the browser are recognisably one product
 * instead of two Material defaults.
 */
val TrippinDisplay = FontFamily(
    Font(R.font.fraunces_bold, FontWeight.Bold),
    Font(R.font.fraunces_bold, FontWeight.SemiBold)
)

val TrippinBody = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_bold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Black),
    Font(R.font.nunito_bold, FontWeight.ExtraBold)
)

val TrippinTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )
)
