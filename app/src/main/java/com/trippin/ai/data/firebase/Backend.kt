package com.trippin.ai.data.firebase

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The one place that knows how Firebase is wired.
 *
 * - **Connected**: `google-services.json` is in `app/`, so FirebaseApp is initialised by the
 *   Google Services plugin. Trips sync live between phones through Firestore, and Firebase Auth
 *   gives every user (including guests, via anonymous sign-in) a real uid.
 * - **Demo**: no config file. Firestore still runs, but only as an on-device database (network
 *   disabled), with a local uid. Everything works on one phone, which keeps the app usable on a
 *   lab machine; invites to other phones need the connected mode.
 *
 * Writes are fire-and-forget: Firestore applies them to the local cache at once (so the UI
 * updates immediately, even offline) and syncs later. Failures (e.g. a security-rule denial)
 * are reported on [errors] for the UI to show.
 */
class Backend(context: Context) {

    val isDemo: Boolean
    val db: FirebaseFirestore
    val auth: FirebaseAuth?

    init {
        val real = FirebaseApp.getApps(context).isNotEmpty()
        isDemo = !real
        if (real) {
            db = FirebaseFirestore.getInstance()
            auth = FirebaseAuth.getInstance()
        } else {
            val app = FirebaseApp.getApps(context).firstOrNull { it.name == DEMO_APP } ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId("trippin-offline-demo")
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("offline-demo-no-network")
                    .build(),
                DEMO_APP,
            )
            db = FirebaseFirestore.getInstance(app)
            db.disableNetwork()
            auth = null
        }
    }

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val errors: SharedFlow<String> = _errors

    /** Runs a write without waiting on the server; a failure surfaces on [errors]. */
    fun <T> fire(task: Task<T>, what: String) {
        task.addOnFailureListener { e ->
            Log.w(TAG, "$what failed", e)
            _errors.tryEmit(friendly(what, e))
        }
    }

    fun report(message: String) {
        _errors.tryEmit(message)
    }

    fun trips(): CollectionReference = db.collection("trips")
    fun trip(id: String): DocumentReference = trips().document(id)
    fun members(tripId: String) = trip(tripId).collection("members")
    fun ideas(tripId: String) = trip(tripId).collection("ideas")
    fun comments(tripId: String) = trip(tripId).collection("comments")
    fun events(tripId: String) = trip(tripId).collection("events")
    fun decisions(tripId: String) = trip(tripId).collection("decisions")
    fun prefs(tripId: String) = trip(tripId).collection("prefs")
    fun proposals(tripId: String) = trip(tripId).collection("proposals")
    fun reactions(tripId: String) = trip(tripId).collection("reactions")
    fun itinerary(tripId: String): DocumentReference = trip(tripId).collection("itinerary").document("current")
    fun invite(code: String): DocumentReference = db.collection("invites").document(code)

    /** Every sub-collection of a trip, used when the owner deletes it (Firestore does not cascade). */
    fun tripChildren(tripId: String) = listOf(
        ideas(tripId), comments(tripId), events(tripId), decisions(tripId), prefs(tripId),
        proposals(tripId), reactions(tripId), trip(tripId).collection("itinerary"), members(tripId), // members last
    )

    private fun friendly(what: String, e: Exception): String {
        val m = e.message.orEmpty()
        return when {
            "PERMISSION_DENIED" in m -> "You don't have permission to $what."
            "UNAVAILABLE" in m || "offline" in m.lowercase() -> "You're offline — \"$what\" will sync when you reconnect."
            else -> "Couldn't $what. Please try again."
        }
    }

    companion object {
        private const val TAG = "Backend"
        private const val DEMO_APP = "trippin-demo"
    }
}
