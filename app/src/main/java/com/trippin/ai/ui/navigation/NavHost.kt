package com.trippin.ai.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Luggage
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.trippin.ai.auth.Session
import com.trippin.ai.ui.screens.auth.AuthScreen
import com.trippin.ai.ui.screens.home.HomeScreen
import com.trippin.ai.ui.screens.lab.LabScreen
import com.trippin.ai.ui.screens.plan.PlanScreen
import com.trippin.ai.ui.screens.planner.PlannerScreen
import com.trippin.ai.ui.screens.profile.ProfileScreen
import com.trippin.ai.ui.screens.today.TodayScreen
import com.trippin.ai.ui.theme.Trip

object Routes {
    const val AUTH = "auth"
    const val HOME = "home"
    const val PLANNER = "planner"
    const val PLAN = "plan/{tripId}"
    const val TODAY = "today/{tripId}"
    const val LAB = "lab"
    const val YOU = "you"
    fun plan(id: Long) = "plan/$id"
    fun today(id: Long) = "today/$id"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.HOME, "Trips", Icons.Filled.Luggage),
    Tab(Routes.LAB, "AI Lab", Icons.Filled.Science),
    Tab(Routes.YOU, "You", Icons.Filled.Person),
)

@Composable
fun TrippinNavHost(container: AppContainer) {
    val session by container.authRepository.session.collectAsStateWithLifecycle(initialValue = null)
    val nav = rememberNavController()

    // Deep links that arrive while the app is already open (e.g. tapping a notification).
    val activity = LocalContext.current as ComponentActivity
    DisposableEffect(nav) {
        val listener = Consumer<android.content.Intent> { nav.handleDeepLink(it) }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }

    val current = session ?: run {
        Box(Modifier.fillMaxSize().background(Trip.Paper))
        return
    }
    // Decided once, so a later sign-in or sign-out does not rebuild the graph under the user.
    val start = remember { if (current is Session.SignedOut) Routes.AUTH else Routes.HOME }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        containerColor = Trip.Paper,
        bottomBar = { if (route in tabs.map { it.route }) BottomBar(nav, route) },
    ) { padding ->
        NavHost(nav, startDestination = start, modifier = Modifier.padding(bottom = padding.calculateBottomPadding())) {
            composable(Routes.AUTH) {
                AuthScreen(container) {
                    nav.navigate(Routes.HOME) { popUpTo(Routes.AUTH) { inclusive = true } }
                }
            }
            composable(Routes.HOME) {
                HomeScreen(
                    container,
                    onPlan = { nav.navigate(Routes.PLANNER) },
                    onOpen = { nav.navigate(Routes.plan(it)) },
                )
            }
            composable(Routes.PLANNER) {
                PlannerScreen(
                    container,
                    onBack = { nav.popBackStack() },
                    onBuilt = { id -> nav.navigate(Routes.plan(id)) { popUpTo(Routes.HOME) } },
                )
            }
            composable(
                Routes.PLAN,
                arguments = listOf(navArgument("tripId") { type = NavType.LongType }),
                deepLinks = listOf(navDeepLink { uriPattern = "trippin://trip/{tripId}" }),
            ) { entry ->
                val id = entry.arguments?.getLong("tripId") ?: 0L
                PlanScreen(container, id, onBack = { nav.popBackStack() }, onToday = { nav.navigate(Routes.today(id)) })
            }
            composable(Routes.TODAY, arguments = listOf(navArgument("tripId") { type = NavType.LongType })) { entry ->
                TodayScreen(container, entry.arguments?.getLong("tripId") ?: 0L, onClose = { nav.popBackStack() })
            }
            composable(Routes.LAB) { LabScreen(container) }
            composable(Routes.YOU) {
                ProfileScreen(container) {
                    nav.navigate(Routes.AUTH) { popUpTo(0) { inclusive = true } }
                }
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
