package com.trippin.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardTravel
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.trippin.core.design.LoadingBlock
import com.trippin.core.design.TrippinMotion
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.rememberAnimationsEnabled
import com.trippin.core.design.TrippinType
import com.trippin.core.common.MagicLink
import com.trippin.core.common.shareTokenFromPath
import com.trippin.core.design.clickableTab
import com.trippin.feature.auth.SignInScreen
import com.trippin.feature.budget.BudgetScreen
import com.trippin.feature.group.GroupScreen
import com.trippin.feature.history.HistoryScreen
import com.trippin.feature.home.HomeScreen
import com.trippin.feature.intro.IntroScreen
import com.trippin.feature.itinerary.ItineraryScreen
import com.trippin.feature.explore.ExploreScreen
import com.trippin.feature.map.MapScreen
import com.trippin.feature.planner.PlannerScreen
import com.trippin.feature.profile.ProfileScreen
import com.trippin.feature.stop.StopScreen
import com.trippin.feature.today.TodayScreen
import com.trippin.feature.trips.TripsScreen
import com.trippin.feature.triphub.TripHubScreen

@Composable
fun TrippinAppShell(
    /** The path of the link the app was opened or resumed with ("/t/<token>"), or null for a plain launch. */
    pendingSharePath: String? = null,
    onSharePathConsumed: () -> Unit = {},
    pendingMagicLink: MagicLink? = null,
    onMagicLinkConsumed: () -> Unit = {},
    /** A trip to open directly, from the plan-ready notification. */
    pendingTripId: String? = null,
    onTripIdConsumed: () -> Unit = {},
    viewModel: ShellViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        is AuthState.Loading -> Box(Modifier.fillMaxSize().background(TrippinTheme.colors.paper)) {
            LoadingBlock("Loading")
        }
        // A shared link opened while signed out still needs an account first: the link is not lost,
        // just held until SignedInShell exists to act on it.
        is AuthState.SignedOut -> {
            val introSeen by viewModel.introSeen.collectAsStateWithLifecycle()
            // A tapped sign-in link goes straight to signing in: whoever tapped it has started already.
            // Remembered, so the intro cannot flash back while the "seen" flag is still being saved.
            var cameFromLink by remember { mutableStateOf(false) }
            LaunchedEffect(pendingMagicLink) { if (pendingMagicLink != null) cameFromLink = true }
            when {
                cameFromLink || pendingMagicLink != null || introSeen == true -> SignInScreen(
                    onSignedIn = {},
                    magicLink = pendingMagicLink,
                    onMagicLinkConsumed = {
                        onMagicLinkConsumed()
                        viewModel.finishIntro()
                    }
                )
                introSeen == false -> IntroScreen(onDone = viewModel::finishIntro)
                else -> Box(Modifier.fillMaxSize().background(TrippinTheme.colors.paper))
            }
        }
        is AuthState.SignedIn -> {
            LaunchedEffect(state.account.id) { viewModel.revalidate() }
            // Already signed in: a sign-in link has nothing left to do.
            LaunchedEffect(pendingMagicLink) { if (pendingMagicLink != null) onMagicLinkConsumed() }
            SignedInShell(viewModel, pendingSharePath, onSharePathConsumed, pendingTripId, onTripIdConsumed)
        }
    }
}

private enum class ShellTab(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    TRIPS("Trips", Icons.Default.CardTravel),
    YOU("You", Icons.Default.Person)
}

@Composable
private fun SignedInShell(
    viewModel: ShellViewModel,
    pendingSharePath: String?,
    onSharePathConsumed: () -> Unit,
    pendingTripId: String?,
    onTripIdConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val resolvedShareTripId by viewModel.resolvedShareTripId.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // A share link is consumed the moment it is looked at, whether or not it turns out to be one:
    // there is nothing to gain by asking again for a path that already failed to resolve.
    LaunchedEffect(pendingSharePath) {
        val token = shareTokenFromPath(pendingSharePath)
        if (pendingSharePath != null) onSharePathConsumed()
        if (token != null) viewModel.openSharedTrip(token)
    }

    LaunchedEffect(resolvedShareTripId) {
        resolvedShareTripId?.let { tripId ->
            navController.navigate(TripHub(tripId)) { launchSingleTop = true }
            viewModel.clearResolvedShareTrip()
        }
    }

    val openTrip: (String) -> Unit = { id ->
        viewModel.openTrip(id)
        navController.navigate(TripHub(id)) { launchSingleTop = true }
    }

    LaunchedEffect(pendingTripId) {
        pendingTripId?.let { id ->
            onTripIdConsumed()
            viewModel.openTrip(id)
            navController.navigate(TripHub(id)) { launchSingleTop = true }
            navController.navigate(Plan(id)) { launchSingleTop = true }
        }
    }

    Column(Modifier.fillMaxSize().background(TrippinTheme.colors.paper)) {
        Box(Modifier.weight(1f)) {
            val animate = rememberAnimationsEnabled()
            NavHost(
                navController = navController,
                startDestination = Home,
                enterTransition = { screenEnter(animate, forward = true, betweenTabs = isTabSwitch()) },
                exitTransition = { screenExit(animate, forward = true, betweenTabs = isTabSwitch()) },
                popEnterTransition = { screenEnter(animate, forward = false, betweenTabs = isTabSwitch()) },
                popExitTransition = { screenExit(animate, forward = false, betweenTabs = isTabSwitch()) }
            ) {
                composable<Home> {
                    HomeScreen(
                        onOpenTrip = openTrip,
                        onOpenToday = { id ->
                            viewModel.openTrip(id)
                            navController.navigate(Today(id))
                        },
                        onOpenPlan = { id ->
                            viewModel.openTrip(id)
                            navController.navigate(Plan(id))
                        },
                        onStartPlanner = { navController.navigate(Planner()) },
                        onStartPlannerFor = { city -> navController.navigate(Planner(city)) }
                    )
                }

                composable<Trips> {
                    TripsScreen(
                        onOpenTrip = openTrip,
                        onOpenToday = { id ->
                            viewModel.openTrip(id)
                            navController.navigate(Today(id))
                        },
                        onStartPlanner = { destination ->
                            navController.navigate(Planner(destination.orEmpty()))
                        }
                    )
                }

                composable<You> {
                    ProfileScreen(
                        onOpenTrips = { navController.navigate(Trips) { launchSingleTop = true } }
                    )
                }

                composable<TripHub> {
                    TripHubScreen(
                        onBack = { navController.popBackStack() },
                        onOpenPlan = { navController.navigate(Plan(it)) },
                        onOpenToday = { navController.navigate(Today(it)) },
                        onOpenMap = { navController.navigate(MapView(it)) },
                        onOpenGroup = { navController.navigate(Group(it)) },
                        onOpenBudget = { navController.navigate(Budget(it)) },
                        onOpenHistory = { navController.navigate(History(it)) }
                    )
                }

                composable<Plan> { entry ->
                    val tripId = entry.toRoute<Plan>().tripId
                    LaunchedEffect(tripId) { viewModel.openTrip(tripId) }
                    ItineraryScreen(
                        tripId = tripId,
                        onBack = { navController.popBackStack() },
                        onOpenMap = { navController.navigate(MapView(it)) },
                        onOpenStop = { activityId -> navController.navigate(Stop(tripId, activityId)) }
                    )
                }

                composable<Group> { entry ->
                    val tripId = entry.toRoute<Group>().tripId
                    GroupScreen(tripId = tripId, onBack = { navController.popBackStack() })
                }

                composable<Planner> { entry ->
                    PlannerScreen(
                        initialDestination = entry.toRoute<Planner>().destination,
                        onBack = { navController.popBackStack() },
                        onTripCreated = { id ->
                            viewModel.openTrip(id)
                            // The new trip's hub sits under its plan, so backing out of the build lands
                            // on the trip rather than on the planner that made it.
                            navController.navigate(TripHub(id)) { popUpTo(Home) }
                            navController.navigate(Plan(id))
                        }
                    )
                }

                composable<Today> { entry ->
                    val tripId = entry.toRoute<Today>().tripId
                    TodayScreen(
                        tripId = tripId,
                        onBack = { navController.popBackStack() },
                        onOpenMap = { navController.navigate(MapView(tripId)) },
                        onOpenStop = { activityId -> navController.navigate(Stop(tripId, activityId)) },
                        onExploreAround = { a ->
                            a.place?.location?.let { p ->
                                navController.navigate(Explore(p.latitude, p.longitude, a.title, tripId, a.id))
                            }
                        }
                    )
                }

                composable<Stop> { entry ->
                    val tripId = entry.toRoute<Stop>().tripId
                    StopScreen(
                        onBack = { navController.popBackStack() },
                        onFindNearby = { lat, lng, title, activityId ->
                            navController.navigate(Explore(lat, lng, title, tripId, activityId))
                        }
                    )
                }

                composable<Explore> {
                    ExploreScreen(
                        onBack = { navController.popBackStack() },
                        onSwapped = { navController.popBackStack() }
                    )
                }

                composable<Budget> {
                    BudgetScreen(onBack = { navController.popBackStack() })
                }

                composable<History> {
                    HistoryScreen(onBack = { navController.popBackStack() })
                }

                composable<MapView> { entry ->
                    val tripId = entry.toRoute<MapView>().tripId
                    MapScreen(
                        tripId = tripId,
                        onBack = { navController.popBackStack() },
                        onExploreAround = { point, title ->
                            navController.navigate(Explore(point.latitude, point.longitude, title))
                        }
                    )
                }
            }
        }

        // The bar belongs to the three top-level places. Inside a trip the screen has its own way
        // back and its own action at the bottom, and a second row of navigation would compete with it.
        val onTab = ShellTab.entries.any { currentDestination.isTab(it) }
        if (onTab) {
            val tabOptions: androidx.navigation.NavOptionsBuilder.() -> Unit = {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
            TrippinBottomBar(
                currentDestination = currentDestination,
                onSelect = { tab ->
                    // Navigate with the concrete route type at the call site: type-safe navigate resolves
                    // the serializer from the reified type, so upcasting to Destination first would fail.
                    when (tab) {
                        ShellTab.HOME -> navController.navigate(Home, tabOptions)
                        ShellTab.TRIPS -> navController.navigate(Trips, tabOptions)
                        ShellTab.YOU -> navController.navigate(You, tabOptions)
                    }
                }
            )
        }
    }
}

@Composable
private fun TrippinBottomBar(
    currentDestination: androidx.navigation.NavDestination?,
    onSelect: (ShellTab) -> Unit
) {
    val colors = TrippinTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.paper)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.hairline))
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .heightIn(min = 64.dp)
                .padding(horizontal = 8.dp)
        ) {
            ShellTab.entries.forEach { tab ->
                val selected = currentDestination.isTab(tab)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clickableTab { onSelect(tab) }
                        .padding(vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = if (selected) colors.ink else colors.inkMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        style = TrippinType.Caption,
                        color = if (selected) colors.ink else colors.inkMuted
                    )
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier
                            .size(width = 16.dp, height = 3.dp)
                            .background(if (selected) colors.accent else androidx.compose.ui.graphics.Color.Transparent)
                    )
                }
            }
        }
    }
}

private fun androidx.navigation.NavDestination?.isTab(tab: ShellTab): Boolean {
    val dest = this ?: return false
    return dest.hierarchy.any { node ->
        when (tab) {
            ShellTab.HOME -> node.hasRoute(Home::class)
            ShellTab.TRIPS -> node.hasRoute(Trips::class)
            ShellTab.YOU -> node.hasRoute(You::class)
        }
    }
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch(): Boolean =
    ShellTab.entries.any { initialState.destination.isTab(it) } && ShellTab.entries.any { targetState.destination.isTab(it) }

/**
 * Going deeper slides the new screen in a short way from the right while it fades up; going back
 * reverses it. Between tabs there is no direction, so it is a plain crossfade. All on the shared curve.
 */
private fun screenEnter(animate: Boolean, forward: Boolean, betweenTabs: Boolean): EnterTransition = when {
    !animate -> EnterTransition.None
    betweenTabs -> fadeIn(TrippinMotion.fast())
    else -> fadeIn(TrippinMotion.medium()) +
        slideInHorizontally(TrippinMotion.medium()) { width -> if (forward) width / 6 else -width / 6 }
}

private fun screenExit(animate: Boolean, forward: Boolean, betweenTabs: Boolean): ExitTransition = when {
    !animate -> ExitTransition.None
    betweenTabs -> fadeOut(TrippinMotion.fast())
    else -> fadeOut(TrippinMotion.fast()) +
        slideOutHorizontally(TrippinMotion.medium()) { width -> if (forward) -width / 10 else width / 6 }
}
