package com.trippin.ai.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.firestore.Query
import com.trippin.ai.MainActivity
import com.trippin.ai.R
import com.trippin.ai.TrippinApp
import com.trippin.ai.data.firebase.toEvent
import com.trippin.ai.data.firebase.toTrip
import com.trippin.ai.data.model.Trip
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Background job (WorkManager, every few hours, only with a network):
 *  1. Group digest — "3 new updates in Goa trip" when friends voted, suggested or changed the plan.
 *  2. Weather briefing — once a day for a trip starting within 3 days (or under way), with the
 *     rain chance from the live forecast. Never for trips beyond the forecast range.
 * Each notification deep-links into the trip.
 */
class TripBriefWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val c = (applicationContext as TrippinApp).container
        if (c.backend.isDemo) return Result.success() // nothing arrives from other phones in demo mode
        val me = c.authRepository.current() ?: return Result.success()
        val trips = runCatching {
            c.backend.trips().whereArrayContains("memberIds", me.uid).get().await().documents.mapNotNull { it.toTrip() }
        }.getOrElse { return Result.retry() }
        val today = LocalDate.now()
        trips.filterNot { it.isPast(today) }.forEach { trip ->
            runCatching { digest(trip, me.uid) }
            runCatching { briefing(trip, today) }
        }
        return Result.success()
    }

    private suspend fun digest(trip: Trip, myUid: String) {
        val c = (applicationContext as TrippinApp).container
        val since = maxOf(c.preferences.lastSeen(trip.id), c.preferences.notifiedUpTo(trip.id))
        val events = c.backend.events(trip.id).orderBy("createdAt", Query.Direction.DESCENDING).limit(20).get().await()
            .documents.mapNotNull { it.toEvent(trip.id) }
            .filter { it.createdAt > since && it.actorId != myUid }
        if (events.isEmpty()) return
        val title = if (events.size == 1) trip.title else "${events.size} new updates in ${trip.title}"
        val text = events.take(4).joinToString("\n") { it.text }
        notify(trip.id, NOTIF_DIGEST, title, text)
        c.preferences.setNotifiedUpTo(trip.id, events.maxOf { it.createdAt })
    }

    private suspend fun briefing(trip: Trip, today: LocalDate) {
        val c = (applicationContext as TrippinApp).container
        val dest = trip.stay ?: trip.destination ?: return
        val daysAway = ChronoUnit.DAYS.between(today, trip.startDate)
        if (daysAway > 3) return
        if (c.preferences.briefedOn(trip.id) == today.toString()) return
        val day = if (trip.isOngoing(today)) today else trip.startDate
        val w = c.placesRepository.forecast(dest.lat, dest.lng, day, 1).firstOrNull() ?: return
        if (!w.known) return
        val rain = (w.rainProbability * 100).toInt()
        val lead = if (trip.isOngoing(today)) "Today in ${dest.name}" else if (daysAway == 0L) "${trip.title} starts today" else "${trip.title} starts in $daysAway day${if (daysAway == 1L) "" else "s"}"
        val tip = when {
            rain >= 60 -> "Pack a rain jacket — indoor stops are safer bets."
            w.maxTempC >= 34 -> "It'll be hot. Plan breaks in the shade."
            else -> "Looks good for getting around."
        }
        notify(trip.id, NOTIF_BRIEF, lead, "$rain% chance of rain, up to ${w.maxTempC.toInt()}°C. $tip")
        c.preferences.setBriefedOn(trip.id, today.toString())
    }

    private fun notify(tripId: String, kind: Int, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("trippin://trip/$tripId"), ctx, MainActivity::class.java)
        val id = tripId.hashCode() * 31 + kind
        val pending = PendingIntent.getActivity(ctx, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(id, n)
    }

    companion object {
        const val CHANNEL_ID = "trip_briefings"
        private const val WORK_NAME = "trip-brief"
        private const val NOTIF_DIGEST = 1
        private const val NOTIF_BRIEF = 2

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_trip_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_trip_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TripBriefWorker>(3, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
