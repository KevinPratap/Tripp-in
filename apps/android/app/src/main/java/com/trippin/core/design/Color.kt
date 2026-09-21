package com.trippin.core.design

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// The semantic tokens (design system section 1).
//
// A screen names a meaning and never a hue, and this file names each colour once. Every token holds
// its own literal rather than an alias of a palette row above it, because two names for one value is
// how the wrong one gets picked: this file used to declare a Comic palette, four Material colour
// aliases and a category set, and ComicBlack and ComicInk were the same ink under two names.
//
// The one deliberate exception is Panel and OnCrimson, which are the same white and are separate
// tokens because a card surface and the ink on a crimson fill must be free to move apart.
// ---------------------------------------------------------------------------

/** The one interactive colour: primary buttons, the selected tab, the selected chip, links. */
val AccentCrimson = Color(0xFFE11D48)

/**
 * The label, icon or spinner on a filled crimson control, and nothing else: the accent button and
 * the destructive button alike. Section 1 of the design system has no token for this job, which is
 * why the filled button used to reach for a surface token and the spinners inside two dialogs used
 * a raw white. It is its own literal on purpose: ink on crimson is not a card surface and must not
 * follow one if the surface value ever moves.
 */
val OnCrimson = Color(0xFFFFFFFF)

/** A destructive or failed action only: Delete, a plan that failed, a budget exceeded. */
val DangerCrimson = Color(0xFFB31B3E)

/** All primary text, borders and every card edge. */
val Ink = Color(0xFF18181B)

/** Secondary text: addresses, sources, timestamps, helper lines. */
val InkMuted = Color(0xFF52525B)

/** The page. */
val Paper = Color(0xFFFAF8F5)

/** A card surface. */
val Panel = Color(0xFFFFFFFF)

/** A caution, on its own surface: over budget, a stop that clashes with opening hours. */
val WarnAmber = Color(0xFF8A5A00)
val WarnAmberSurface = Color(0xFFFFF4D6)

/** A confirmed good state, used sparingly: every traveller in, a locked plan. */
val GoodInk = Color(0xFF14532D)
val GoodInkSurface = Color(0xFFE7F3EA)

/** Neither good nor bad: a draft, a category label, an unvisited stop. */
val NeutralInk = Color(0xFF3F3F46)
val NeutralInkSurface = Color(0xFFF4F4F5)

// ---------------------------------------------------------------------------
// The Material scheme rows. No screen reads these, and nothing outside Theme.kt does either.
//
// They exist so MaterialTheme has a scheme, because the Material components the app still uses by
// default (the date picker, the text fields, the dialogs) take their own colours from it. The names
// are the palette rows this file used to declare, kept here so Theme.kt does not have to change in
// the same edit: what the dark scheme maps to is Kevin's call and is recorded as such in
// TRIPPIN_DESIGN_SYSTEM.md section 1, which says a dark scheme gets its own values for the tokens
// above. Deleting these rows before that decision is made would decide it silently.
// ---------------------------------------------------------------------------

val OceanBlue = AccentCrimson
val OceanBlueDark = Color(0xFFBE123C)
val EmeraldTeal = AccentCrimson
val EmeraldTealDark = OceanBlueDark
val SunsetCoral = Color(0xFFFACC15)
val CharcoalDark = Ink
val OffWhite = Paper
val PureWhite = Panel
val SurfaceDark = Color(0xFF27272A)
