package com.trippin.ai.auth

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.UserProfileChangeRequest
import com.trippin.ai.data.firebase.Backend
import com.trippin.ai.data.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/** Who is using the app. Every session has a stable [uid], guests included, so they can join trips. */
data class Session(
    val uid: String,
    val name: String,
    val email: String?,
    /** A guest: anonymous Firebase user (connected) or a local id (demo). */
    val isGuest: Boolean,
    val isDemo: Boolean,
)

/**
 * Sign-in. Connected mode uses Firebase Auth: email/password, or anonymous sign-in for guests —
 * a guest can later attach an email with [saveGuestAccount] and keep every trip. Demo mode keeps
 * a local id and name in DataStore.
 */
class AuthRepository(private val backend: Backend, private val prefs: PreferencesRepository, scope: CoroutineScope) {

    private val auth: FirebaseAuth? = backend.auth
    val firebaseAvailable: Boolean get() = auth != null

    private val firebaseUser = MutableStateFlow(auth?.currentUser?.let { Triple(it.uid, it.displayName, it.email) to it.isAnonymous })

    init {
        auth?.addAuthStateListener { a ->
            firebaseUser.value = a.currentUser?.let { Triple(it.uid, it.displayName, it.email) to it.isAnonymous }
        }
    }

    /** null while loading, then the current session (or SignedOut via [Session] == null in [signedIn]). */
    val state: StateFlow<AuthState> = combine(firebaseUser, prefs.settings) { fb, settings ->
        when {
            fb != null -> {
                val (ids, anon) = fb
                val (uid, display, email) = ids
                AuthState.SignedIn(
                    Session(uid, display?.takeIf { it.isNotBlank() } ?: settings.displayName ?: email?.substringBefore('@') ?: "Traveller", email, anon, false),
                )
            }
            auth == null && settings.localUid != null && settings.displayName != null ->
                AuthState.SignedIn(Session(settings.localUid, settings.displayName, null, true, true))
            else -> AuthState.SignedOut
        }
    }.stateIn(scope, SharingStarted.Eagerly, AuthState.Loading)

    suspend fun current(): Session? = (state.first { it !is AuthState.Loading } as? AuthState.SignedIn)?.session

    suspend fun signIn(email: String, password: String): Result<Unit> = guard {
        val a = auth ?: error(NO_FIREBASE)
        a.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun register(name: String, email: String, password: String): Result<Unit> = guard {
        val a = auth ?: error(NO_FIREBASE)
        val user = a.createUserWithEmailAndPassword(email.trim(), password).await().user ?: error("Sign-up failed")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).await()
        prefs.setDisplayName(name.trim())
        firebaseUser.value = Triple(user.uid, name.trim(), user.email) to false
    }

    suspend fun continueAsGuest(name: String): Result<Unit> = guard {
        val clean = name.trim().ifBlank { "Traveller" }
        prefs.setDisplayName(clean)
        val a = auth
        if (a == null) {
            prefs.ensureLocalUid()
        } else {
            val user = withTimeout(20_000) { a.signInAnonymously().await() }.user ?: error("Guest sign-in failed")
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean).build()).await()
            firebaseUser.value = Triple(user.uid, clean, null) to true
        }
    }

    /** Turns a guest into a full account without losing their trips (same uid). */
    suspend fun saveGuestAccount(email: String, password: String): Result<Unit> = guard {
        val user = auth?.currentUser ?: error(NO_FIREBASE)
        user.linkWithCredential(EmailAuthProvider.getCredential(email.trim(), password)).await()
        user.reload().await()
        firebaseUser.value = Triple(user.uid, user.displayName, user.email) to false
    }

    suspend fun resetPassword(email: String): Result<Unit> = guard {
        (auth ?: error(NO_FIREBASE)).sendPasswordResetEmail(email.trim()).await()
    }

    suspend fun rename(name: String): Result<Unit> = guard {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Name can't be empty" }
        prefs.setDisplayName(clean)
        auth?.currentUser?.let { u ->
            u.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean).build()).await()
            firebaseUser.value = Triple(u.uid, clean, u.email) to u.isAnonymous
        }
    }

    suspend fun signOut() {
        auth?.signOut()
        firebaseUser.value = null
        if (auth == null) prefs.clearLocalIdentity()
    }

    private suspend fun guard(block: suspend () -> Unit): Result<Unit> = try {
        block(); Result.success(Unit)
    } catch (e: FirebaseAuthInvalidCredentialsException) {
        Result.failure(IllegalStateException("That email or password doesn't look right."))
    } catch (e: FirebaseAuthInvalidUserException) {
        Result.failure(IllegalStateException("No account with that email. Create one instead?"))
    } catch (e: FirebaseAuthUserCollisionException) {
        Result.failure(IllegalStateException("That email already has an account. Sign in instead."))
    } catch (e: FirebaseAuthWeakPasswordException) {
        Result.failure(IllegalStateException("Use a password with at least 6 characters."))
    } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
        Result.failure(IllegalStateException("No connection. Check your internet and try again."))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message?.takeIf { it.length < 120 } ?: "Something went wrong. Try again."))
    }

    companion object {
        const val NO_FIREBASE = "Accounts need Firebase, which isn't set up in this build. Continue as a guest."
    }
}

sealed interface AuthState {
    data object Loading : AuthState
    data object SignedOut : AuthState
    data class SignedIn(val session: Session) : AuthState
}
