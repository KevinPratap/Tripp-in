package com.trippin.core.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Tripp'in colour system.
 *
 * A screen names a meaning (ink, paper, accent) and never a hue, and every meaning is a field on
 * [TrippinColors]. There are two instances of it, [LightTrippinColors] and [DarkTrippinColors], and
 * the theme puts the right one on [com.trippin.core.design.LocalTrippinColors] so a screen reads the
 * same token in both modes. The brand does not change between modes: it is newsprint by day (cream
 * page, ink type, crimson action) and carbon by night (near black page, warm cream type, a lifted
 * rose action), drawn with the same thick borders and hard offset shadows either way.
 */
@Immutable
data class TrippinColors(
    /** The one interactive colour: primary buttons, the selected tab, the selected chip, links. */
    val accent: Color,
    /** The label, icon or spinner that sits on a filled accent control. */
    val onAccent: Color,
    /** A destructive or failed action only: delete, a plan that failed, a budget exceeded. */
    val danger: Color,
    /** All primary text, and every card edge and border. */
    val ink: Color,
    /** Secondary text: addresses, sources, timestamps, helper lines. */
    val inkMuted: Color,
    /** The page. */
    val paper: Color,
    /** A card surface. */
    val panel: Color,
    /** A quieter surface, one step off the page: a nested row, a field, a disabled control. */
    val panelAlt: Color,
    /**
     * A card that leads the screen. In light mode this is the same paper as [panel] and the lift comes
     * from a soft shadow; in dark mode a shadow on a dark page is invisible, so the lift has to come
     * from the surface being a step lighter instead.
     */
    val panelRaised: Color,
    /**
     * The edge of a card or a divider: ink at low opacity, so a card is bounded without being outlined.
     * Every surface used to carry a 2.5dp edge of full ink, which left nothing on a screen quieter than
     * anything else.
     */
    val hairline: Color,
    /** The edge of something a finger acts on: a field, a secondary button, an unselected chip. */
    val controlEdge: Color,
    /** Full ink. For a deliberate hard edge only, and for the offset shadow under a primary action. */
    val line: Color,
    /** The hard offset shadow. Reserved for the primary action; it is what the app is known for. */
    val shadow: Color,
    /** Tint for the soft shadow under a raised card. */
    val shadowSoft: Color,
    /** A caution, on its own surface: over budget, a stop that clashes with opening hours. */
    val warn: Color,
    val warnSurface: Color,
    /** A confirmed good state, used sparingly: every traveller in, a locked plan. */
    val good: Color,
    val goodSurface: Color,
    /** Neither good nor bad: a draft, a category label, an unvisited stop. */
    val neutral: Color,
    val neutralSurface: Color,
    val isDark: Boolean
)

/** Newsprint by day. */
val LightTrippinColors = TrippinColors(
    accent = Color(0xFFE11D48),
    onAccent = Color(0xFFFFFFFF),
    danger = Color(0xFFB31B3E),
    ink = Color(0xFF18181B),
    inkMuted = Color(0xFF52525B),
    paper = Color(0xFFFAF8F5),
    panel = Color(0xFFFFFFFF),
    panelAlt = Color(0xFFF1EDE6),
    panelRaised = Color(0xFFFFFFFF),
    hairline = Color(0x1718181B),
    controlEdge = Color(0x2B18181B),
    line = Color(0xFF18181B),
    shadow = Color(0xFF18181B),
    shadowSoft = Color(0xFF18181B),
    warn = Color(0xFF8A5A00),
    warnSurface = Color(0xFFFFF4D6),
    good = Color(0xFF14532D),
    goodSurface = Color(0xFFE7F3EA),
    neutral = Color(0xFF3F3F46),
    neutralSurface = Color(0xFFF4F4F5),
    isDark = false
)

/** Carbon by night. Same borders, warmer ink, a lifted rose so the action still reads. */
val DarkTrippinColors = TrippinColors(
    accent = Color(0xFFFB7185),
    onAccent = Color(0xFF1A0E12),
    danger = Color(0xFFF98A9C),
    ink = Color(0xFFF5EFE7),
    inkMuted = Color(0xFFA8A29A),
    paper = Color(0xFF141110),
    panel = Color(0xFF201C19),
    panelAlt = Color(0xFF2A2521),
    panelRaised = Color(0xFF29231E),
    hairline = Color(0x24F5EFE7),
    controlEdge = Color(0x3DF5EFE7),
    line = Color(0xFFF5EFE7),
    shadow = Color(0xFF000000),
    shadowSoft = Color(0xFF000000),
    warn = Color(0xFFF3C77A),
    warnSurface = Color(0xFF352B14),
    good = Color(0xFF8FD9AC),
    goodSurface = Color(0xFF173026),
    neutral = Color(0xFFCBC5BC),
    neutralSurface = Color(0xFF2A2521),
    isDark = true
)
