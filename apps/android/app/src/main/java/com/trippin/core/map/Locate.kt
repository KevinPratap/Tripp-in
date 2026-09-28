package com.trippin.core.map

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.trippin.core.network.GeoPointDto
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Where the phone is, from Android's own location service (no Google Play services needed). A fresh
 * fix is asked for first; a recent last-known fix is the fallback. Null without permission or a fix.
 */
@SuppressLint("MissingPermission")
suspend fun currentLocation(context: Context): GeoPointDto? {
    if (!hasLocationPermission(context)) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
        .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
    if (Build.VERSION.SDK_INT >= 30) {
        for (provider in providers.filter { it != LocationManager.PASSIVE_PROVIDER }) {
            val fix = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine<Location?> { cont ->
                    try {
                        manager.getCurrentLocation(provider, null, context.mainExecutor) { cont.resume(it) }
                    } catch (_: Exception) {
                        cont.resume(null)
                    }
                }
            }
            if (fix != null) return GeoPointDto(fix.latitude, fix.longitude)
        }
    }
    val last = providers
        .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
    return last?.let { GeoPointDto(it.latitude, it.longitude) }
}
