package com.trippin

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TrippinApp : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
