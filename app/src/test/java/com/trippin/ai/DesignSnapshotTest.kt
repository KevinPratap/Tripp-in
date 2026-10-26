package com.trippin.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.brand.Art
import com.trippin.ai.ui.brand.OnboardingScreen
import com.trippin.ai.ui.brand.SplashScreen
import com.trippin.ai.ui.brand.TrippinLockup
import com.trippin.ai.ui.components.Avatar
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.Pill
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.icons.TripIcons
import com.trippin.ai.ui.theme.Trip
import com.trippin.ai.ui.theme.TrippinTheme
import java.io.File
import java.io.FileOutputStream
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders the design system to PNGs in build/snapshots so the look can be reviewed without a device. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi", application = android.app.Application::class)
class DesignSnapshotTest {
    @get:Rule val rule = createComposeRule()

    private fun snap(name: String, content: @Composable () -> Unit) {
        rule.setContent { TrippinTheme { Box(Modifier.fillMaxSize().background(Trip.Paper)) { content() } } }
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val f = File("build/snapshots/$name.png").apply { parentFile?.mkdirs() }
        FileOutputStream(f).use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun splash() = snap("01-splash") { SplashScreen() }

    @Test fun onboarding() = snap("02-onboarding") { OnboardingScreen(onDone = {}) }

    @Test fun sheet() = snap("03-design-sheet") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            TrippinLockup()
            Kicker("Your trips")
            Headline("Where to next?")
            SectionTitle("Upcoming")
            Panel {
                Text("Lisbon, Portugal", style = MaterialTheme.typography.titleLarge)
                Text("12 to 16 Nov, 4 travellers", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Voting open", Trip.Ink, Trip.Paper); Pill("Low risk", Trip.Mint, androidx.compose.ui.graphics.Color.White)
                }
                Row { listOf("Asha", "Ben", "Chen", "Dev").forEach { Avatar(it, it, 34.dp) } }
            }
            SignalButton("Plan a trip", onClick = {})
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SmallButton("Share recap", {}); SmallButton("Add to calendar", {}) }
        }
    }

    @Test fun icons() = snap("04-icons") {
        val all = listOf(
            TripIcons.Home, TripIcons.Explore, TripIcons.Luggage, TripIcons.Style, TripIcons.Person, TripIcons.Add,
            TripIcons.Close, TripIcons.Check, TripIcons.Back, TripIcons.Forward, TripIcons.Send, TripIcons.Search,
            TripIcons.Place, TripIcons.ThumbUp, TripIcons.Restore, TripIcons.Lock, TripIcons.Chat, TripIcons.Star,
            TripIcons.ShoppingBag, TripIcons.Settings, TripIcons.Restaurant, TripIcons.Park, TripIcons.Nightlife,
            TripIcons.Map, TripIcons.Edit, TripIcons.Delete, TripIcons.Museum, TripIcons.Wallet, TripIcons.Share,
            TripIcons.Calendar, TripIcons.Heart, TripIcons.Sparkle,
        )
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            all.chunked(6).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    row.forEach { Icon(it, contentDescription = null, tint = Trip.Ink, modifier = Modifier.size(40.dp)) }
                }
            }
        }
    }

    @Test fun empties() = snap("05-empty-states") {
        Column(Modifier.padding(20.dp)) {
            EmptyState("No trips yet", "Plan one, or join a friend's with an invite code.", art = Art.SUITCASE)
            EmptyState("No expenses yet", "Add the hotel, the train or a dinner and Trippin works out who owes whom.", art = Art.SPLIT)
        }
    }

    @Test fun art() = snap("06-illustrations") {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { com.trippin.ai.ui.brand.Illustration(Art.ROUTE, size = 150.dp); com.trippin.ai.ui.brand.Illustration(Art.CARDS, size = 150.dp) }
            Row { com.trippin.ai.ui.brand.Illustration(Art.SPLIT, size = 150.dp); com.trippin.ai.ui.brand.Illustration(Art.COMPASS, size = 150.dp) }
            com.trippin.ai.ui.brand.Illustration(Art.SUITCASE, size = 150.dp)
        }
    }
}
