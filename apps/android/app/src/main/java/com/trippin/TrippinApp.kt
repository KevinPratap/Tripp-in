package com.trippin

import android.app.Application
import com.trippin.core.network.NetworkModule
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrippinApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // The static ApiService accessor needs the application context for the
        // per-install guest session id it sends with every request.
        NetworkModule.init(this)
    }
}
