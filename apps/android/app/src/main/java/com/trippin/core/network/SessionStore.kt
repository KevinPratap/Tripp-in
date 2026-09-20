package com.trippin.core.network

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf

/** The account the app is signed in as. Nothing here is invented: it comes from POST /auth/verify. */
data class SignedInAccount(
    val id: String,
    val email: String,
    val displayName: String
)

/**
 * The signed-in account and its session token, kept in one place.
 *
 * There is no guest identity any more. Before this, every install minted its own anonymous account
 * from an X-Guest-Session header, so a trip belonged to a device rather than to a person, and there
 * was no way to reach the same trips from anywhere else. Now an install with no session is an
 * install that has not signed in, the API answers it 401, and the app asks it to sign in.
 *
 * The token is written as it arrives from the server. If the server 401s it, [clear] drops it.
 */
object SessionStore {
    private const val PREFS = "trippin_account"
    private const val KEY_TOKEN = "session_token"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "display_name"
    private const val KEY_USER_ID = "user_id"

    private var prefs: SharedPreferences? = null

    /** Observed by the shell, which shows the sign-in screen while this is null. */
    val account = mutableStateOf<SignedInAccount?>(null)

    /** Read on every request by the auth interceptor, so signing out takes effect immediately. */
    val token: String?
        get() = prefs?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        account.value = readAccount()
    }

    fun save(session: VerifiedSessionDto) {
        val store = prefs ?: return
        store.edit()
            .putString(KEY_TOKEN, session.sessionToken)
            .putString(KEY_EMAIL, session.user.email)
            .putString(KEY_NAME, session.user.displayName)
            .putString(KEY_USER_ID, session.user.id)
            .apply()
        account.value = SignedInAccount(
            id = session.user.id,
            email = session.user.email,
            displayName = session.user.displayName
        )
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        account.value = null
    }

    private fun readAccount(): SignedInAccount? {
        val store = prefs ?: return null
        val token = store.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val email = store.getString(KEY_EMAIL, null) ?: return null
        val id = store.getString(KEY_USER_ID, null) ?: return null
        val name = store.getString(KEY_NAME, null) ?: email.substringBefore('@')
        return if (token.isNotBlank()) SignedInAccount(id, email, name) else null
    }
}
