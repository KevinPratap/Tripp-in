package com.trippin.ai.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trippin.ai.data.model.Budget
import com.trippin.ai.data.model.MemberPrefs
import com.trippin.intelligence.group.Diet
import com.trippin.intelligence.group.StartPreference
import com.trippin.intelligence.model.Category
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(
    val displayName: String?,
    /** Demo mode only: the on-device user id. */
    val localUid: String?,
    val briefingsOn: Boolean,
)

/** Small per-device settings (DataStore). Shared trip data lives in Firestore, not here. */
class PreferencesRepository(private val context: Context) {
    private val nameKey = stringPreferencesKey("display_name")
    private val uidKey = stringPreferencesKey("local_uid")
    private val briefingsKey = booleanPreferencesKey("briefings_on")

    // Default travel preferences, copied into each new trip the user joins.
    private val interestsKey = stringSetPreferencesKey("pref_interests")
    private val dietKey = stringPreferencesKey("pref_diet")
    private val alcoholKey = booleanPreferencesKey("pref_no_alcohol")
    private val startKey = stringPreferencesKey("pref_start")
    private val walkKey = intPreferencesKey("pref_walk_km")
    private val accessKey = booleanPreferencesKey("pref_access")
    private val budgetKey = stringPreferencesKey("pref_budget")

    val settings: Flow<Settings> = context.dataStore.data.map {
        Settings(it[nameKey], it[uidKey], it[briefingsKey] ?: true)
    }

    suspend fun setDisplayName(name: String) = context.dataStore.edit { it[nameKey] = name }

    suspend fun ensureLocalUid(): String {
        val existing = context.dataStore.data.first()[uidKey]
        if (existing != null) return existing
        val id = "local-" + UUID.randomUUID().toString().replace("-", "").take(20)
        context.dataStore.edit { it[uidKey] = id }
        return id
    }

    /** Signing out of demo mode forgets the name but keeps the uid, so on-device trips come back. */
    suspend fun clearLocalIdentity() = context.dataStore.edit { it.remove(nameKey) }

    suspend fun setBriefings(on: Boolean) = context.dataStore.edit { it[briefingsKey] = on }
    suspend fun briefingsOn(): Boolean = settings.first().briefingsOn

    suspend fun defaultPrefs(uid: String, name: String): MemberPrefs {
        val p = context.dataStore.data.first()
        return MemberPrefs(
            uid = uid,
            name = name,
            interests = p[interestsKey]?.mapNotNull { v -> Category.entries.firstOrNull { it.name == v } }?.toSet()
                ?: setOf(Category.FOOD, Category.LANDMARK, Category.MUSEUM),
            diet = p[dietKey]?.let { v -> Diet.entries.firstOrNull { it.name == v } } ?: Diet.ANY,
            noAlcohol = p[alcoholKey] ?: false,
            start = p[startKey]?.let { v -> StartPreference.entries.firstOrNull { it.name == v } } ?: StartPreference.NORMAL,
            maxWalkKm = p[walkKey]?.takeIf { it > 0 },
            accessibility = p[accessKey] ?: false,
            budget = p[budgetKey]?.let { v -> Budget.entries.firstOrNull { it.name == v } } ?: Budget.MID,
        )
    }

    suspend fun saveDefaultPrefs(m: MemberPrefs) = context.dataStore.edit {
        it[interestsKey] = m.interests.map { c -> c.name }.toSet()
        it[dietKey] = m.diet.name
        it[alcoholKey] = m.noAlcohol
        it[startKey] = m.start.name
        it[walkKey] = m.maxWalkKm ?: 0
        it[accessKey] = m.accessibility
        it[budgetKey] = m.budget.name
    }

    // ---- Activity "last seen", so the background digest only reports what's new. ----
    private fun seenKey(tripId: String) = longPreferencesKey("seen_$tripId")
    suspend fun lastSeen(tripId: String): Long = context.dataStore.data.first()[seenKey(tripId)] ?: 0L
    suspend fun markSeen(tripId: String, at: Long = System.currentTimeMillis()) = context.dataStore.edit { it[seenKey(tripId)] = at }

    private fun notifiedKey(tripId: String) = longPreferencesKey("notified_$tripId")
    suspend fun notifiedUpTo(tripId: String): Long = context.dataStore.data.first()[notifiedKey(tripId)] ?: 0L
    suspend fun setNotifiedUpTo(tripId: String, at: Long) = context.dataStore.edit { it[notifiedKey(tripId)] = at }

    private fun briefKey(tripId: String) = stringPreferencesKey("brief_$tripId")
    suspend fun briefedOn(tripId: String): String? = context.dataStore.data.first()[briefKey(tripId)]
    suspend fun setBriefedOn(tripId: String, day: String) = context.dataStore.edit { it[briefKey(tripId)] = day }

    // ---- Step baseline per calendar day: "walked today" survives leaving and reopening Today. ----
    private val stepDayKey = stringPreferencesKey("step_day")
    private val stepBaseKey = floatPreferencesKey("step_base")
    suspend fun stepBaseline(day: String, current: Float): Float {
        val p = context.dataStore.data.first()
        if (p[stepDayKey] == day) {
            val base = p[stepBaseKey] ?: current
            // The counter resets on reboot; if it went backwards, start again from here.
            if (current >= base) return base
        }
        context.dataStore.edit { it[stepDayKey] = day; it[stepBaseKey] = current }
        return current
    }
}
