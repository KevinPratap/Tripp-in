package com.trippin.core.notify

import android.Manifest
import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.trippin.MainActivity
import com.trippin.R
import com.trippin.core.common.DataResult
import com.trippin.core.repository.TripRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/** How a build ended, as far as the traveller needs to know. */
enum class PlanOutcome { READY, FAILED }

/** Null while the server is still building, so the watcher keeps waiting. */
fun planOutcomeOf(status: String): PlanOutcome? = when (status) {
    "GENERATING", "DRAFT" -> null
    "FAILED" -> PlanOutcome.FAILED
    else -> PlanOutcome.READY
}

data class PlanNotice(val title: String, val body: String)

fun planNotice(outcome: PlanOutcome, place: String?): PlanNotice {
    val name = place?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }
    return when (outcome) {
        PlanOutcome.READY -> PlanNotice(
            title = if (name != null) "Your $name plan is ready" else "Your plan is ready",
            body = "Every stop has been checked. Tap to see the days."
        )
        PlanOutcome.FAILED -> PlanNotice(
            title = if (name != null) "The $name plan could not be built" else "The plan could not be built",
            body = "Nothing was saved. Open the trip to try again."
        )
    }
}

/** Whether any of this app's screens is on screen right now. */
object AppVisibility {
    @Volatile var started = 0
        private set
    val inForeground: Boolean get() = started > 0

    fun register(app: Application) {
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { started++ }
            override fun onActivityStopped(activity: Activity) { started = (started - 1).coerceAtLeast(0) }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
    }
}

/**
 * Keeps an eye on a plan that is still building and says so when it finishes, but only if the
 * traveller has left the app: while they are in it the screen already shows the result.
 *
 * It lives as long as the app process does. If Android ends the process while the app is in the
 * background, the watch ends with it and no notification is sent; the plan itself is unaffected
 * and is there the next time the trip is opened.
 */
@Singleton
class PlanReadyWatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val tripRepository: TripRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val watching = Collections.synchronizedSet(mutableSetOf<String>())

    fun watch(tripId: String, place: String?) {
        if (!watching.add(tripId)) return
        scope.launch {
            try {
                val deadline = System.currentTimeMillis() + MAX_WATCH_MS
                while (System.currentTimeMillis() < deadline) {
                    delay(POLL_MS)
                    val result = tripRepository.getStatus(tripId)
                    if (result is DataResult.Ok) {
                        val outcome = planOutcomeOf(result.value.status) ?: continue
                        if (outcome == PlanOutcome.READY) tripRepository.refreshTripDetails(tripId)
                        if (!AppVisibility.inForeground) post(tripId, planNotice(outcome, place))
                        return@launch
                    }
                }
            } finally {
                watching.remove(tripId)
            }
        }
    }

    private fun post(tripId: String, notice: PlanNotice) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Plans", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "When a plan you started has finished building"
            }
        )
        val open = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_TRIP_ID, tripId)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            tripId.hashCode(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notice.title)
            .setContentText(notice.body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(tripId.hashCode(), notification)
        } catch (_: SecurityException) {
            // Permission was withdrawn between the check and the post; nothing to tell anyone.
        }
    }

    companion object {
        const val EXTRA_TRIP_ID = "com.trippin.extra.TRIP_ID"
        private const val CHANNEL_ID = "plans"
        private const val POLL_MS = 4_000L
        private const val MAX_WATCH_MS = 15 * 60_000L
    }
}
