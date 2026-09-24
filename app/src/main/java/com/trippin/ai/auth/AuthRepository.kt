package com.trippin.ai.auth

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.trippin.ai.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

sealed interface Session {
    data object SignedOut : Session
    data class Guest(val name: String) : Session
    data class Account(val email: String) : Session
}

/**
 * Firebase email/password sign-in, with a guest mode so the app still runs when no
 * google-services.json is configured (e.g. a lab machine).
 */
class AuthRepository(context: Context, private val prefs: PreferencesRepository) {

    val firebaseAvailable: Boolean = FirebaseApp.getApps(context).isNotEmpty()
    private val auth: FirebaseAuth? = if (firebaseAvailable) FirebaseAuth.getInstance() else null

    private val accountEmail = MutableStateFlow(auth?.currentUser?.email)

    val session: Flow<Session> = combine(accountEmail, prefs.settings) { email, settings ->
        when {
            email != null -> Session.Account(email)
            settings.guestName != null -> Session.Guest(settings.guestName)
            else -> Session.SignedOut
        }
    }

    suspend fun signIn(email: String, password: String): Result<Unit> = runCatching {
        val a = auth ?: error("Sign-in isn't set up in this build. Continue as a guest.")
        a.signInWithEmailAndPassword(email.trim(), password).await()
        accountEmail.value = a.currentUser?.email
    }

    suspend fun register(email: String, password: String): Result<Unit> = runCatching {
        val a = auth ?: error("Sign-in isn't set up in this build. Continue as a guest.")
        a.createUserWithEmailAndPassword(email.trim(), password).await()
        accountEmail.value = a.currentUser?.email
    }

    suspend fun continueAsGuest(name: String) = prefs.setGuest(name.trim().ifBlank { "Traveller" })

    suspend fun signOut() {
        auth?.signOut()
        accountEmail.value = null
        prefs.setGuest(null)
    }

    suspend fun displayName(): String = when (val s = session.first()) {
        is Session.Account -> s.email.substringBefore('@')
        is Session.Guest -> s.name
        Session.SignedOut -> "Traveller"
    }
}
