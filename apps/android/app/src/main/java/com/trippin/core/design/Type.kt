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

/**
 * The seven type roles from design system section 2.
 *
 * One family, seven sizes, and every piece of text on a screen declares which one it is. Before
 * this a single "12sp bold" was doing eight jobs, so every screen read as one flat wall of equally
 * loud text. Nothing here may be used as a one-off size: if a piece of text does not fit a role, the
 * element is wrong rather than the scale.
 */
object TrippinType {
    /** The single biggest thing on a screen. One per screen, never two. */
    val Display = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Black,
        fontSize = 30.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp
    )

    /** A screen heading, or a card's own name. */
    val Title = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Black,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp
    )

    /** A section heading inside a screen: Group, Today, Money. */
    val Heading = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.5.sp
    )

    /** Sentences a person reads: a description, an address, a reason. */
    val Body = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    )

    /** The name of a thing: a field label, a chip, a button, a tab. */
    val Label = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.8.sp
    )

    /** Metadata only: a source, a count, a time. The floor, never below. */
    val Caption = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.0.sp
    )

    /**
     * Times, counts and money, with tabular figures so a column of stops lines up. A figure is never
     * animated before it is known: no counting up to a price.
     */
    val Numeric = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Black,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )

    /** The same tabular figures at caption scale, for a stop row's time or a small amount. */
    val NumericSmall = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )
}
