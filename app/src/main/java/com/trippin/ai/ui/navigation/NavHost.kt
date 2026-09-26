package com.trippin.ai.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
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
import com.trippin.ai.ui.screens.auth.AuthScreen
import com.trippin.ai.ui.screens.create.CreateTripScreen
import com.trippin.ai.ui.screens.home.HomeScreen
import com.trippin.ai.ui.screens.join.JoinScreen
import com.trippin.ai.ui.screens.lab.LabScreen
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
    const val LAB = "lab"
    const val PREFS = "prefs?tripId={tripId}"
    fun trip(id: String, tab: String? = null) = "trip/$id" + (tab?.let { "?tab=$it" } ?: "")
    fun join(code: String? = null) = "join" + (code?.let { "?code=$it" } ?: "")
    fun today(id: String) = "today/$id"
    fun prefs(tripId: String? = null) = "prefs" + (tripId?.let { "?tripId=$it" } ?: "")
}

/** Shows a short message at the bottom of the screen, from anywhere in the UI. */
val LocalSnack = staticCompositionLocalOf<(String) -> Unit> { {} }

/** The signed-in user, for screens that need to know who "you" are. */
val LocalSession = staticCompositionLocalOf<Session> { error("No session") }

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Home", Icons.Filled.Home),
    Tab(Routes.TRIPS, "Trips", Icons.Filled.Luggage),
    Tab(Routes.PROFILE, "Profile", Icons.Filled.Person),
)

@Composable
fun TrippinNavHost(container: AppContainer) {
    val auth by container.authRepository.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val show: (String) -> Unit = remember { { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } } }

    LaunchedEffect(Unit) { container.backend.errors.collect { show(it) } }

    CompositionLocalProvider(LocalSnack provides show) {
        when (val a = auth) {
            AuthState.Loading -> Box(Modifier.fillMaxSize().background(Trip.Paper), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Trip.Ink)
            }
            AuthState.SignedOut -> Scaffold(containerColor = Trip.Paper, snackbarHost = { SnackbarHost(snackbar) }) { p ->
                Box(Modifier.padding(p)) { AuthScreen(container) }
            }
            is AuthState.SignedIn -> CompositionLocalProvider(LocalSession provides a.session) {
                // Keyed on the uid so a different account never sees the previous back stack.
                androidx.compose.runtime.key(a.session.uid) { SignedInApp(container, snackbar) }
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
                )
            }
            composable(Routes.TRIPS) {
                TripsScreen(container, onOpen = { nav.navigate(Routes.trip(it)) }, onCreate = { nav.navigate(Routes.CREATE) }, onJoin = { nav.navigate(Routes.join()) })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(container, onLab = { nav.navigate(Routes.LAB) }, onDefaults = { nav.navigate(Routes.prefs()) })
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
                    onPrefs = { nav.navigate(Routes.prefs(id)) },
                    onLab = { nav.navigate(Routes.LAB) },
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
            composable(Routes.LAB) { LabScreen(container, onBack = { if (!nav.popBackStack()) nav.navigate(Routes.HOME) }) }
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
    NavigationBar(containerColor = Trip.Ink) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = route == tab.route,
                onClick = {
                    nav.navigate(tab.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(tab.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Trip.Ink,
                    selectedTextColor = Trip.Paper,
                    indicatorColor = Trip.Signal,
                    unselectedIconColor = Trip.OnInkMuted,
                    unselectedTextColor = Trip.OnInkMuted,
                ),
            )
        }
    }
}
