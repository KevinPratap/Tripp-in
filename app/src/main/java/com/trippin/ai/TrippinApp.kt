package com.trippin.ai

import android.app.Application
import android.content.Context
import com.trippin.ai.auth.AuthRepository
import com.trippin.ai.data.local.TrippinDatabase
import com.trippin.ai.data.remote.Network
import com.trippin.ai.data.repository.LearningRepository
import com.trippin.ai.data.repository.PreferencesRepository
import com.trippin.ai.data.repository.TripRepository
import com.trippin.ai.sensors.LocationHelper
import com.trippin.ai.sensors.StepSensor
import com.trippin.ai.work.TripBriefWorker

/**
 * Manual dependency injection: one container built once in Application.onCreate and shared by
 * every ViewModel. (Hilt would generate the same wiring; doing it by hand keeps it visible.)
 */
class AppContainer(context: Context) {
    private val db = TrippinDatabase.create(context)
    val preferences = PreferencesRepository(context)
    val learningRepository = LearningRepository(db.learningDao())
    val tripRepository = TripRepository(db.tripDao(), Network.geocoding, Network.weather, Network.overpass, learningRepository)
    val authRepository = AuthRepository(context, preferences)
    val stepSensor = StepSensor(context)
    val location = LocationHelper(context)
}

class TrippinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TripBriefWorker.createChannel(this)
        TripBriefWorker.schedule(this)
    }
}
