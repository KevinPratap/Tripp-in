package com.trippin.ai

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trippin.ai.ui.navigation.TrippinNavHost
import com.trippin.ai.ui.theme.TrippinTheme

/**
 * Single-activity app: every screen is a Compose destination in [TrippinNavHost].
 * Permissions are asked in context (location and steps in Today mode, notifications when you
 * turn briefings on or join a group), never all at once on first launch.
 * Lifecycle callbacks are logged (filter Logcat by "Lifecycle") to demonstrate the lifecycle.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate (restored=${savedInstanceState != null})")
        enableEdgeToEdge()
        val container = (application as TrippinApp).container
        setContent {
            TrippinTheme {
                TrippinNavHost(container)
            }
        }
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
