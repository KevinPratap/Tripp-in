package com.trippin.ai.ui.navigation

import androidx.activity.ComponentActivity
import com.trippin.ai.ui.icons.TripIcons
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.trippin.ai.ui.screens.swipe.SwipeHubScreen
import com.trippin.ai.ui.screens.swipe.SwipeScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.util.Consumer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.trippin.ai.AppContainer
import com.trippin.ai.auth.AuthState
import com.trippin.ai.auth.Session
import com.trippin.ai.ui.brand.OnboardingScreen
import com.trippin.ai.ui.brand.SplashScreen
import com.trippin.ai.ui.screens.auth.AuthScreen
import com.trippin.ai.ui.screens.create.CreateTripScreen
import com.trippin.ai.ui.screens.explore.ExploreScreen
import com.trippin.ai.ui.screens.home.HomeScreen
import com.trippin.ai.ui.screens.join.JoinScreen
import com.trippin.ai.ui.screens.prefs.PrefsScreen
import com.trippin.ai.ui.screens.profile.ProfileScreen
import com.trippin.ai.ui.screens.today.TodayScreen
import com.trippin.ai.ui.screens.trip.TripScreen
import com.trippin.ai.ui.screens.trips.TripsScreen
import com.trippin.ai.ui.theme.Trip
import kotlinx.coroutines.launch

object Routes {
    const val HOME = "home"
    const val TRIPS = "trips"
    const val PROFILE = "profile"
    const val CREATE = "create"
    const val TRIP = "trip/{tripId}?tab={tab}"
    const val JOIN = "join?code={code}"
    const val TODAY = "today/{tripId}"
    const val EXPLORE = "explore"
    const val CREATE_FOR = "createfor/{name}/{country}/{lat}/{lng}"
    const val SWIPE_HUB = "swipehub"
    const val SWIPE = "swipe/{tripId}"
    const val PREFS = "prefs?tripId={tripId}"
    fun trip(id: String, tab: String? = null) = "trip/$id" + (tab?.let { "?tab=$it" } ?: "")
    fun join(code: String? = null) = "join" + (code?.let { "?code=$it" } ?: "")
    fun createFor(d: com.trippin.ai.data.model.Destination) =
        "createfor/${android.net.Uri.encode(d.name)}/${android.net.Uri.encode(d.country ?: "-")}/${d.lat}/${d.lng}"
    fun today(id: String) = "today/$id"
    fun swipe(id: String) = "swipe/$id"
    fun prefs(tripId: String? = null) = "prefs" + (tripId?.let { "?tripId=$it" } ?: "")
}

/** Shows a short message at the bottom of the screen, from anywhere in the UI. */
val LocalSnack = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The signed-in user, for screens that need to know who "you" are. */
val LocalSession = staticCompositionLocalOf<Session> { error("No session") }

private data class Tab(val route: String, val label: String, val icon: ImageVector, val raised: Boolean = false)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", TripIcons.Home),
    Tab(Routes.TRIPS, "Trips", TripIcons.Luggage),
    Tab(Routes.SWIPE_HUB, "Swipe", TripIcons.Style, raised = true),
    Tab(Routes.EXPLORE, "Explore", TripIcons.Explore),
    Tab(Routes.PROFILE, "You", TripIcons.Person),
)

@Composable
fun TrippinNavHost(container: AppContainer) {
    val auth by container.authRepository.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = remember { { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } } }

    LaunchedEffect(Unit) { container.backend.errors.collect { show(it) } }

    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("trippin_ui", android.content.Context.MODE_PRIVATE) }
    var onboarded by remember { mutableStateOf(uiPrefs.getBoolean("onboarded", false)) }
    var splash by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1300); splash = false }

    CompositionLocalProvider(LocalSnack provides show) {
        when (val a = auth) {
            AuthState.Loading -> SplashScreen()
            else -> if (splash) SplashScreen() else when (a) {
                AuthState.SignedOut -> if (!onboarded) {
                    OnboardingScreen(onDone = { uiPrefs.edit().putBoolean("onboarded", true).apply(); onboarded = true })
                } else Scaffold(containerColor = Trip.Paper, snackbarHost = { SnackbarHost(snackbar) }) { p ->
                    Box(Modifier.padding(p)) { AuthScreen(container) }
                }
                is AuthState.SignedIn -> CompositionLocalProvider(LocalSession provides a.session) {
                    // Keyed on the uid so a different account never sees the previous back stack.
                    androidx.compose.runtime.key(a.session.uid) { SignedInApp(container, snackbar) }
                }
                AuthState.Loading -> SplashScreen()
            }
        }
    }
}

@Composable
private fun SignedInApp(container: AppContainer, snackbar: SnackbarHostState) {
    val nav = rememberNavController()
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(nav) {
        // Deep links that arrive while the app is already open (notification taps, invite links).
        val listener = Consumer<android.content.Intent> { nav.handleDeepLink(it) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = Trip.Paper,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { if (route in tabs.map { it.route }) BottomBar(nav, route) },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.HOME) {
                HomeScreen(
                    container,
                    onCreate = { nav.navigate(Routes.CREATE) },
                    onJoin = { nav.navigate(Routes.join()) },
                    onOpen = { id, tab -> nav.navigate(Routes.trip(id, tab)) },
                    onToday = { nav.navigate(Routes.today(it)) },
                    onSwipe = { nav.navigate(Routes.swipe(it)) },
                    onSwipeHub = { nav.navigate(Routes.SWIPE_HUB) { launchSingleTop = true } },
                )
            }
            composable(Routes.EXPLORE) {
                ExploreScreen(container, onPick = { nav.navigate(Routes.createFor(it)) })
            }
            composable(
                Routes.CREATE_FOR,
                arguments = listOf(
                    navArgument("name") { type = NavType.StringType },
                    navArgument("country") { type = NavType.StringType },
                    navArgument("lat") { type = NavType.StringType },
                    navArgument("lng") { type = NavType.StringType },
                ),
            ) { entry ->
                val a = entry.arguments
                val dest = com.trippin.ai.data.model.Destination(
                    a?.getString("name").orEmpty(),
                    a?.getString("country")?.takeIf { it != "-" },
                    a?.getString("lat")?.toDoubleOrNull() ?: 0.0,
                    a?.getString("lng")?.toDoubleOrNull() ?: 0.0,
                )
                CreateTripScreen(container, onBack = { nav.popBackStack() }, onCreated = { id, invite ->
                    nav.navigate(Routes.trip(id, if (invite) "people" else null)) { popUpTo(Routes.EXPLORE) }
                }, prefill = dest)
            }
            composable(Routes.SWIPE_HUB) {
                SwipeHubScreen(container, onOpen = { nav.navigate(Routes.swipe(it)) }, onCreate = { nav.navigate(Routes.CREATE) })
            }
            composable(Routes.SWIPE, arguments = listOf(navArgument("tripId") { type = NavType.StringType })) { entry ->
                val id = entry.arguments?.getString("tripId").orEmpty()
                SwipeScreen(
                    container, id,
                    onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                    onPlan = { nav.navigate(Routes.trip(id, "plan")) { popUpTo(Routes.SWIPE) { inclusive = true } } },
                )
            }
            composable(Routes.TRIPS) {
                TripsScreen(container, onOpen = { nav.navigate(Routes.trip(it)) }, onCreate = { nav.navigate(Routes.CREATE) }, onJoin = { nav.navigate(Routes.join()) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(container, onDefaults = { nav.navigate(Routes.prefs()) })
            }
            composable(Routes.CREATE) {
                CreateTripScreen(container, onBack = { nav.popBackStack() }, onCreated = { id, invite ->
                    nav.navigate(Routes.trip(id, if (invite) "people" else null)) { popUpTo(Routes.CREATE) { inclusive = true } }
                })
            }
            composable(
                Routes.TRIP,
                arguments = listOf(
                    navArgument("tripId") { type = NavType.StringType },
                    navArgument("tab") { type = NavType.StringType; nullable = true; defaultValue = null },
                ),
                deepLinks = listOf(navDeepLink { uriPattern = "trippin://trip/{tripId}" }),
            ) { entry ->
                val id = entry.arguments?.getString("tripId").orEmpty()
                TripScreen(
                    container, id, initialTab = entry.arguments?.getString("tab"),
                    onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                    onToday = { nav.navigate(Routes.today(id)) },
                    onSwipe = { nav.navigate(Routes.swipe(id)) },
                    onPrefs = { nav.navigate(Routes.prefs(id)) },
                    onDeleted = { nav.navigate(Routes.TRIPS) { popUpTo(Routes.HOME) } },
                )
            }
            composable(
                Routes.JOIN,
                arguments = listOf(navArgument("code") { type = NavType.StringType; nullable = true; defaultValue = null }),
                deepLinks = listOf(navDeepLink { uriPattern = "trippin://join/{code}" }),
            ) { entry ->
                JoinScreen(
                    container, entry.arguments?.getString("code"),
                    onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) },
                    onJoined = { id -> nav.navigate(Routes.trip(id)) { popUpTo(Routes.HOME) } },
                )
            }
            composable(Routes.TODAY, arguments = listOf(navArgument("tripId") { type = NavType.StringType })) { entry ->
                TodayScreen(container, entry.arguments?.getString("tripId").orEmpty(), onClose = { nav.popBackStack() })
            }
            composable(
                Routes.PREFS,
                arguments = listOf(navArgument("tripId") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) { entry ->
                PrefsScreen(container, entry.arguments?.getString("tripId"), onDone = { nav.popBackStack() })
            }
        }
    }
}

@Composable
private fun BottomBar(nav: NavHostController, route: String?) {
    Row(
        Modifier.fillMaxWidth().background(Trip.Card)
            .drawBehind { drawLine(Trip.Line, Offset.Zero, Offset(size.width, 0f), strokeWidth = 1.dp.toPx()) }
            .navigationBarsPadding().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = route == tab.route
            Box(
                Modifier.weight(1f).fillMaxHeight().selectable(selected = selected, role = Role.Tab) {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                contentAlignment = Alignment.Center,
            ) {
                if (tab.raised) {
                    Box(
                        Modifier.offset(y = (-8).dp).size(width = 58.dp, height = 46.dp)
                            .shadow(10.dp, RoundedCornerShape(23.dp), ambientColor = Color(0x2214151A), spotColor = Color(0x4414151A))
                            .clip(RoundedCornerShape(23.dp)).background(Trip.Brand)
                            .border(1.dp, Trip.Gilt, RoundedCornerShape(23.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(tab.icon, contentDescription = tab.label, tint = Trip.Signal) }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Icon(tab.icon, contentDescription = null, tint = if (selected) Trip.Ink else Trip.Muted, modifier = Modifier.size(24.dp))
                        Text(tab.label, style = MaterialTheme.typography.labelSmall, color = if (selected) Trip.Ink else Trip.Muted)
                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (selected) Trip.Signal else Color.Transparent))
                    }
                }
            }
        }
    }
}
