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
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Step counter → distance walked today. TYPE_STEP_COUNTER reports steps since boot, so the
 * first reading is used as the baseline for this session.
 */
class StepSensor(context: Context) {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = manager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val available: Boolean get() = sensor != null

    /** Emits kilometres walked since collection started (average stride 0.75 m). */
    fun kilometres(): Flow<Double> = callbackFlow {
        val s = sensor
        if (s == null) {
            close()
            return@callbackFlow
        }
        var baseline = -1f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val total = event.values[0]
                if (baseline < 0) baseline = total
                trySend(((total - baseline) * 0.75) / 1000.0)
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
