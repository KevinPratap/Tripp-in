package com.trippin.core.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.trippin.R

/**
 * Two faces with two different jobs. Instrument Serif names places and states the big numbers: a
 * destination, a stop, a time, a total. Geist carries everything a person acts on or scans: labels,
 * buttons, body copy, metadata. Keeping the serif off controls is what lets a place read as a place.
 *
 * Instrument Serif ships in one weight. Every display role below asks for Normal, because any heavier
 * request would make Compose fake a bold by smearing the outlines.
 */
val TrippinDisplay = FontFamily(
    Font(R.font.instrument_serif_regular, FontWeight.Normal)
)

val TrippinBody = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold)
)

val TrippinTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 28.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 24.sp,
        lineHeight = 28.sp
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
        fontWeight = FontWeight.Normal,
        fontSize = 36.sp,
        lineHeight = 38.sp,
        letterSpacing = 0.sp
    )

    /** A screen heading, or a card's own name. */
    val Title = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp
    )

    /** A section heading inside a screen: Group, Today, Money. */
    val Heading = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp
    )

    /** Sentences a person reads: a description, an address, a reason. */
    val Body = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp
    )

    /**
     * The name of a thing: a field label, a chip, a button, a tab.
     *
     * Tracking was 0.8sp. Wide tracking on a 13sp label reads as machine-set rather than typeset, and it
     * was on nearly every string in the app, so it is now effectively none. Sentence case, not upper:
     * upper case is [Eyebrow]'s job and has to be chosen.
     */
    val Label = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp
    )

    /** Metadata only: a source, a count, a time. The floor, never below. */
    val Caption = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    )

    /**
     * The one place upper case belongs: a short label over a group of cards ("Next up", "Earlier").
     *
     * It exists so upper case is a deliberate choice for a handful of strings, rather than what every
     * label got by default. Never a sentence, never more than three words.
     */
    val Eyebrow = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp
    )

    /**
     * Times, counts and money, with tabular figures so a column of stops lines up. A figure is never
     * animated before it is known: no counting up to a price.
     */
    val Numeric = TextStyle(
        fontFamily = TrippinDisplay,
        fontWeight = FontWeight.Normal,
        fontSize = 32.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "lnum"
    )

    /** The same tabular figures at caption scale, for a stop row's time or a small amount. */
    val NumericSmall = TextStyle(
        fontFamily = TrippinBody,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
        fontFeatureSettings = "tnum"
    )
}
