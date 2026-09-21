package com.trippin.core.design

import androidx.compose.ui.graphics.Color

// Comic Red & Minimalist Graphic Novel Branding
val ComicRed = Color(0xFFE11D48)
val ComicRedDark = Color(0xFFBE123C)
val ComicInk = Color(0xFF18181B)
val ComicBlack = ComicInk
val ComicPaper = Color(0xFFFAF8F5)
val ComicPanel = Color(0xFFFFFFFF)
val ComicYellow = Color(0xFFFACC15)
val ComicMuted = Color(0xFF52525B)

// Primary Color Mappings (Comic Theme)
val OceanBlue = ComicRed
val OceanBlueDark = ComicRedDark
val EmeraldTeal = ComicRed
val EmeraldTealDark = ComicRedDark
val SunsetCoral = ComicYellow

// Surfaces & Backgrounds
val OffWhite = ComicPaper
val PureWhite = ComicPanel
val CharcoalDark = ComicInk
val SurfaceDark = Color(0xFF27272A)

// Category Colors
val CategoryAttraction = ComicRed
val CategoryFood = Color(0xFFF97316)
val CategoryNature = Color(0xFF10B981)
val CategoryNightlife = Color(0xFF8B5CF6)
val CategoryTransit = Color(0xFF0284C7)

// ---------------------------------------------------------------------------
// Semantic tokens (design system section 1).
//
// The values above are the palette; these are the meanings. A screen names a meaning and never a
// hue, so a colour cannot drift away from the thing it is supposed to say. Crimson is the brand and
// therefore the cheapest colour to waste: it has exactly three jobs, and amber, ink green and
// neutral each mean one thing each.
// ---------------------------------------------------------------------------

/** The one interactive colour: primary buttons, the selected tab, the selected chip, links. */
val AccentCrimson = ComicRed

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
val Ink = ComicInk

/** Secondary text: addresses, sources, timestamps, helper lines. */
val InkMuted = ComicMuted

/** The page. */
val Paper = ComicPaper

/** A card surface. */
val Panel = ComicPanel

/** A caution, on its own surface: over budget, a stop that clashes with opening hours. */
val WarnAmber = Color(0xFF8A5A00)
val WarnAmberSurface = Color(0xFFFFF4D6)

/** A confirmed good state, used sparingly: every traveller in, a locked plan. */
val GoodInk = Color(0xFF14532D)
val GoodInkSurface = Color(0xFFE7F3EA)

/** Neither good nor bad: a draft, a category label, an unvisited stop. */
val NeutralInk = Color(0xFF3F3F46)
val NeutralInkSurface = Color(0xFFF4F4F5)
