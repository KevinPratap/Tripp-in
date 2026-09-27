package com.trippin.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trippin.core.network.VerifiedSessionDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/** The account the app is signed in as. Nothing here is invented: it comes from POST /auth/verify. */
data class SignedInAccount(
    val id: String,
    val email: String,
    val displayName: String
)

private val Context.sessionStore by preferencesDataStore(name = "trippin_session")

/**
 * The signed-in account and its session token, held in DataStore.
 *
 * There is no guest identity. An install with no token is an install that has not signed in: the API
 * answers it 401 and the app asks it to sign in. The token is read fresh on every request by the
 * auth interceptor, so signing in or out takes effect on the very next call.
 */
@Singleton
class SessionManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val TOKEN = stringPreferencesKey("session_token")
        val USER_ID = stringPreferencesKey("user_id")
        val EMAIL = stringPreferencesKey("email")
        val NAME = stringPreferencesKey("display_name")
    }

    /** Emits the account, or null when signed out. Observed by the shell to gate the app. */
    val account: Flow<SignedInAccount?> = context.sessionStore.data.map { prefs ->
        val token = prefs[Keys.TOKEN]?.takeIf { it.isNotBlank() } ?: return@map null
        val id = prefs[Keys.USER_ID] ?: return@map null
        val email = prefs[Keys.EMAIL] ?: return@map null
        val name = prefs[Keys.NAME]?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')
        if (token.isBlank()) null else SignedInAccount(id, email, name)
    }

    /** Read synchronously by the OkHttp interceptor, which runs off the main thread. */
    fun currentToken(): String? = runBlocking {
        context.sessionStore.data.first()[Keys.TOKEN]?.takeIf { it.isNotBlank() }
    }

    suspend fun save(session: VerifiedSessionDto) {
        context.sessionStore.edit { prefs ->
            prefs[Keys.TOKEN] = session.sessionToken
            prefs[Keys.USER_ID] = session.user.id
            prefs[Keys.EMAIL] = session.user.email
            prefs[Keys.NAME] = session.user.displayName
        }
    }

    suspend fun clear() {
        context.sessionStore.edit { it.clear() }
    }
}
