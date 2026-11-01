package com.trippin.ai

import android.app.Application
import android.content.Context
import com.trippin.ai.auth.AuthRepository
import com.trippin.ai.data.firebase.Backend
import com.trippin.ai.data.local.TrippinDatabase
import com.trippin.ai.data.remote.Network
import com.trippin.ai.data.repository.CollabRepository
import com.trippin.ai.data.repository.LearningRepository
import com.trippin.ai.data.repository.PlacesRepository
import com.trippin.ai.data.repository.PlanRepository
import com.trippin.ai.data.repository.PreferencesRepository
import com.trippin.ai.data.repository.TripRepository
import com.trippin.ai.sensors.LocationHelper
import com.trippin.ai.sensors.StepSensor
import com.trippin.ai.work.TripBriefWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manual dependency injection: one container built once in Application.onCreate and shared by
 * every ViewModel. (Hilt would generate the same wiring; doing it by hand keeps it visible.)
 */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val backend = Backend(context)
    private val db = TrippinDatabase.create(context)
    val preferences = PreferencesRepository(context)
    val learningRepository = LearningRepository(db.learningDao())
    val authRepository = AuthRepository(backend, preferences, appScope)
    val tripRepository = TripRepository(backend, preferences)
    val collabRepository = CollabRepository(backend, tripRepository)
    val placesRepository = PlacesRepository(Network.photon, Network.overpass, Network.overpassMirror, Network.weather)
    val planRepository = PlanRepository(backend, tripRepository, placesRepository, learningRepository)
    val stepSensor = StepSensor(context, preferences)
    val location = LocationHelper(context)
}

class TrippinApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        TripBriefWorker.createChannel(this)
        // Respect the user's switch: only (re)schedule the background briefing when it is on.
        container.appScope.launch {
            if (container.preferences.briefingsOn()) TripBriefWorker.schedule(this@TrippinApp) else TripBriefWorker.cancel(this@TrippinApp)
        }
    }
}
