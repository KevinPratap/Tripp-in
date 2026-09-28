package com.trippin.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trippin.core.design.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore by preferencesDataStore(name = "trippin_settings")

/** The person's own app preferences: the theme, and whether the first-launch intro has been seen. */
@Singleton
class SettingsManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_mode")

    val themeMode: Flow<ThemeMode> = context.settingsStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "LIGHT" -> ThemeMode.LIGHT
            "DARK" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.settingsStore.edit { it[themeKey] = mode.name }
    }

    private val introSeenKey = booleanPreferencesKey("intro_seen")

    val introSeen: Flow<Boolean> = context.settingsStore.data.map { it[introSeenKey] ?: false }

    suspend fun markIntroSeen() {
        context.settingsStore.edit { it[introSeenKey] = true }
    }
}
