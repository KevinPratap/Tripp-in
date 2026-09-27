package com.trippin.core.design

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the phone itself reports no usable network. This is asked of the system, never inferred
 * from a request that happened to fail, which is the only reason a screen is allowed to say the word
 * "offline": a failed request can mean a dead server or a missing trip just as easily as a dead
 * connection, and the app does not claim a cause it did not check.
 */
@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    var offline by remember { mutableStateOf(!context.hasValidatedInternet()) }

    DisposableEffect(context) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { offline = false }
            override fun onLost(network: Network) { offline = !context.hasValidatedInternet() }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                offline = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        manager?.registerDefaultNetworkCallback(callback)
        onDispose { manager?.unregisterNetworkCallback(callback) }
    }
    return offline
}

private fun Context.hasValidatedInternet(): Boolean {
    val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
    val network = manager.activeNetwork ?: return false
    val caps = manager.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
