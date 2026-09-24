package com.trippin.ai

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.trippin.ai.ui.navigation.TrippinNavHost
import com.trippin.ai.ui.theme.TrippinTheme

/**
 * Single-activity app: every screen is a Compose destination in [TrippinNavHost].
 * Lifecycle callbacks are logged (filter Logcat by "Lifecycle") to demonstrate the activity
 * lifecycle during the viva.
 */
class MainActivity : ComponentActivity() {

    private val permissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        Log.d(TAG, "Permissions: $result")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate (restored=${savedInstanceState != null})")
        enableEdgeToEdge()
        requestRuntimePermissions()
        val container = (application as TrippinApp).container
        setContent {
            TrippinTheme {
                TrippinNavHost(container)
            }
        }
    }

    private fun requestRuntimePermissions() {
        val wanted = buildList {
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissions.launch(wanted.toTypedArray())
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Log.d(TAG, "onNewIntent ${intent.data}")
        setIntent(intent)
    }

    override fun onStart() { super.onStart(); Log.d(TAG, "onStart") }
    override fun onResume() { super.onResume(); Log.d(TAG, "onResume") }
    override fun onPause() { Log.d(TAG, "onPause"); super.onPause() }
    override fun onStop() { Log.d(TAG, "onStop"); super.onStop() }
    override fun onDestroy() { Log.d(TAG, "onDestroy"); super.onDestroy() }

    private companion object {
        const val TAG = "Lifecycle"
    }
}
