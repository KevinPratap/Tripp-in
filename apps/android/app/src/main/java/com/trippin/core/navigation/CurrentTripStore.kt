package com.trippin.core.navigation

import android.content.Context

/**
 * Which trip the Plan and Group tabs are about.
 *
 * The tabs need a stable route to be selectable, but a trip is identified by an id, so the id has to
 * outlive a single screen. This is stored on the device rather than fetched, because the app must be
 * able to open a trip with no signal, which is exactly when the Plan tab matters most.
 */
object CurrentTripStore {
    private const val PREFS = "trippin.current"
    private const val KEY_TRIP_ID = "tripId"

    fun tripId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRIP_ID, null)
            ?.takeIf { it.isNotBlank() }

    fun setTripId(context: Context, tripId: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TRIP_ID, tripId)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_TRIP_ID).apply()
    }
}
