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

/*
 * The phone's own answer to "is there a network", which is the only honest source for that sentence.
 *
 * The app used to say "You are offline" from a catch block, and a catch cannot tell being offline
 * from a trip that is not there, so 1b609fa had to take that sentence off the Today screen rather
 * than print a cause it had not read. This file asks the system instead, which is a fact and not an
 * inference: it is true before a request is made, and it is never derived from a request that failed.
 *
 * It lives in core/design because that is the only shared UI package the Android UI lane owns, and
 * it is UI support rather than design: it reads a device state and hands it to a screen.
 *
 * android.permission.ACCESS_NETWORK_STATE is already declared in the manifest, so nothing new is
 * asked of the person installing the app.
 */

/**
 * True only while the phone itself reports no way of reaching the network.
 *
 * False whenever the app cannot tell, which is the answer that asserts nothing: a phone with no
 * ConnectivityManager, and any device that still holds a usable network, both read as not offline
 * and draw no offline line. The value follows the device while the screen is composed, through the
 * default network callback, so a phone that loses its signal under a screen that is already open
 * updates without a reload.
 */
@Composable
fun rememberIsOffline(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    }
    var isOffline by remember { mutableStateOf(false) }

    DisposableEffect(manager) {
        if (manager == null) {
            onDispose { }
        } else {
            isOffline = phoneReportsNoNetwork(manager)
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    isOffline = phoneReportsNoNetwork(manager)
                }

                override fun onLost(network: Network) {
                    isOffline = phoneReportsNoNetwork(manager)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    isOffline = phoneReportsNoNetwork(manager)
                }
            }
            manager.registerDefaultNetworkCallback(callback)
            onDispose { manager.unregisterNetworkCallback(callback) }
        }
    }

    return isOffline
}

/**
 * The phone has no usable network when it holds no active network at all, or when the one it holds
 * does not report the capacity to reach the internet.
 *
 * NET_CAPABILITY_VALIDATED is deliberately NOT required, and this is the honesty decision in the
 * file: a network that has not validated may still be a working connection, and a phone behind a
 * sign-in page would then be called offline while the app has simply been refused a page. A wrong
 * "you are offline" is the same defect this app keeps closing, only from the other side, so the
 * read errs towards silence and a failed request is never dressed up as a dead phone.
 */
private fun phoneReportsNoNetwork(manager: ConnectivityManager): Boolean {
    val active = manager.activeNetwork ?: return true
    val capabilities = manager.getNetworkCapabilities(active) ?: return true
    return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
