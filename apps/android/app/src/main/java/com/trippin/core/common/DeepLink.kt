package com.trippin.core.common

/**
 * A shared trip link ("https://.../t/<token>") opens the app instead of only a browser tab.
 *
 * The parsing lives here as plain text rather than an `android.net.Uri` operation, so it is testable
 * without an Android runtime: the one call site (MainActivity) converts the incoming Intent's data to
 * its path before calling this, and that conversion is the only untested part.
 */

/**
 * The share token in a path shaped like "/t/<token>", or null when the path is not that shape.
 *
 * Deliberately strict rather than permissive: a link is either exactly this app's share URL or it is
 * not something this function has an opinion about, and the caller is left to fall back to a normal
 * launch rather than guess at a token from something that merely contains "/t/" somewhere.
 */
fun shareTokenFromPath(path: String?): String? {
    if (path.isNullOrBlank()) return null
    val segments = path.trim('/').split('/').filter { it.isNotEmpty() }
    if (segments.size != 2 || segments[0] != "t") return null
    return segments[1].takeIf { it.isNotBlank() }
}
