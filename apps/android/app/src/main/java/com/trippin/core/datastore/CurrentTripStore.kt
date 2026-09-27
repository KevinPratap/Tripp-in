package com.trippin.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.currentTripStore by preferencesDataStore(name = "trippin_current_trip")

/**
 * The trip the app last opened. The Plan and Group tabs are about a specific trip, so they resolve
 * to this one, and say so plainly when there is none. Persisted so the tabs still land on the right
 * trip after the app is reopened.
 */
@Singleton
class CurrentTripStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("current_trip_id")

    val tripId: Flow<String?> = context.currentTripStore.data.map { prefs ->
        prefs[key]?.takeIf { it.isNotBlank() }
    }

    suspend fun set(tripId: String) {
        context.currentTripStore.edit { it[key] = tripId }
    }

    suspend fun clear() {
        context.currentTripStore.edit { it.remove(key) }
    }
}
