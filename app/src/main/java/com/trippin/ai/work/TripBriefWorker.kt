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
import com.trippin.ai.MainActivity
import com.trippin.ai.R
import com.trippin.ai.TrippinApp
import com.trippin.intelligence.bayes.StopRiskModel
import java.util.concurrent.TimeUnit

/**
 * Background job (WorkManager): twice a day, refresh the forecast for the next trip, re-run the
 * Bayesian risk model and post a notification that deep-links into the trip.
 */
class TripBriefWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TrippinApp).container
        val trip = container.tripRepository.nextTrip() ?: return Result.success()
        val rain = runCatching { container.tripRepository.refreshRain(trip) }.getOrElse { return Result.retry() }
        val risk = StopRiskModel.assess(StopRiskModel.StopEvidence(rain, peakHour = true, travelMinutes = 15, hoursEstimated = false))
        notify(
            trip.id,
            "${trip.destination}: ${(rain * 100).toInt()}% chance of rain",
            "Typical stop risk is ${risk.level.lowercase()} (${(risk.pDisrupted * 100).toInt()}%). Tap to review the plan.",
        )
        return Result.success()
    }

    private fun notify(tripId: Long, title: String, text: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = Intent(Intent.ACTION_VIEW, Uri.parse("trippin://trip/$tripId"), ctx, MainActivity::class.java)
        val pending = PendingIntent.getActivity(ctx, tripId.toInt(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(ctx).notify(tripId.toInt(), n)
    }

    companion object {
        const val CHANNEL_ID = "trip_briefings"
        private const val WORK_NAME = "trip-brief"

        fun createChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_trip_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.channel_trip_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TripBriefWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
