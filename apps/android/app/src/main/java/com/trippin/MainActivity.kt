package com.trippin

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.datastore.SettingsManager
import com.trippin.core.design.ThemeMode
import com.trippin.core.design.TrippinTheme
import com.trippin.navigation.TrippinAppShell
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsManager: SettingsManager

    /**
     * The path of the link this activity is currently holding ("/t/<token>" for a shared trip), or
     * null. `singleTask` in the manifest is what makes a link tapped while the app is already running
     * arrive here as [onNewIntent] rather than as a second instance of this activity, so this is the
     * one place both a cold launch and a resume from a link are captured. Parsing what the path means
     * is [com.trippin.core.common.shareTokenFromPath]'s job, not this activity's.
     */
    private var pendingSharePath by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        pendingSharePath = intent?.data?.path
        enableEdgeToEdge()
        setContent {
            val mode by settingsManager.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val dark = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            TrippinTheme(darkTheme = dark) {
                TrippinAppShell(
                    pendingSharePath = pendingSharePath,
                    onSharePathConsumed = { pendingSharePath = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingSharePath = intent.data?.path
    }
}
