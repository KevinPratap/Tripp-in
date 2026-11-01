package com.trippin.ai.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.trippin.ai.data.repository.PreferencesRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * Step counter → distance walked today. TYPE_STEP_COUNTER counts steps since boot, so a
 * baseline is stored per calendar day: leaving and re-opening Today mode keeps the day's total.
 */
class StepSensor(private val context: Context, private val prefs: PreferencesRepository) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val available: Boolean get() = sensor != null

    fun hasPermission(): Boolean = Build.VERSION.SDK_INT < 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    /** Emits kilometres walked today (average stride 0.75 m). Emits nothing without the sensor or permission. */
    fun kilometresToday(): Flow<Double> = callbackFlow {
        val s = sensor
        if (s == null || !hasPermission()) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values[0]
                launch {
                    val base = prefs.stepBaseline(LocalDate.now().toString(), total)
                    trySend(((total - base) * 0.75) / 1000.0)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, s, SensorManager.SENSOR_DELAY_UI)
        awaitClose { manager.unregisterListener(listener) }
    }
}

/** Fused location: last known position, used to measure "how far is my next stop". */
class LocationHelper(private val context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission()
    suspend fun lastLocation(): Location? = if (!hasPermission()) null else runCatching { client.lastLocation.await() }.getOrNull()
}
