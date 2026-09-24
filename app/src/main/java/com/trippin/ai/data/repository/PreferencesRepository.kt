package com.trippin.ai.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(val guestName: String?, val briefingsOn: Boolean)

/** Small key-value settings (DataStore, the modern replacement for SharedPreferences). */
class PreferencesRepository(private val context: Context) {
    private val guestKey = stringPreferencesKey("guest_name")
    private val briefingsKey = booleanPreferencesKey("briefings_on")

    val settings: Flow<Settings> = context.dataStore.data.map {
        Settings(it[guestKey], it[briefingsKey] ?: true)
    }

    suspend fun setGuest(name: String?) {
        context.dataStore.edit { prefs ->
            if (name == null) {
                prefs.remove(guestKey)
            } else {
                prefs[guestKey] = name
            }
        }
    }

    suspend fun setBriefings(on: Boolean) {
        context.dataStore.edit { it[briefingsKey] = on }
    }
}
