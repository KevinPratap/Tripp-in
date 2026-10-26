package com.trippin.ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.trippin.ai.ui.navigation.TrippinNavHost
import com.trippin.ai.ui.theme.TrippinTheme

/**
 * Single-activity app: every screen is a Compose destination in [TrippinNavHost].
 * Permissions are asked in context (location and steps in Today mode, notifications when you
 * turn briefings on or join a group), never all at once on first launch.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        setIntent(intent)
    }
}
