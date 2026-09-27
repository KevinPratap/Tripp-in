package com.trippin.navigation

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CardTravel
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.trippin.core.design.LoadingBlock
import com.trippin.core.design.MessageState
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinType
import com.trippin.core.common.shareTokenFromPath
import com.trippin.core.design.clickableTab
import com.trippin.feature.auth.SignInScreen
import com.trippin.feature.group.GroupScreen
import com.trippin.feature.itinerary.ItineraryScreen
import com.trippin.feature.map.MapScreen
import com.trippin.feature.planner.PlannerScreen
import com.trippin.feature.profile.ProfileScreen
import com.trippin.feature.today.TodayScreen
import com.trippin.feature.trips.TripsScreen

@Composable
fun TrippinAppShell(
    /** The path of the link the app was opened or resumed with ("/t/<token>"), or null for a plain launch. */
    pendingSharePath: String? = null,
    onSharePathConsumed: () -> Unit = {},
    viewModel: ShellViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()

    when (val state = authState) {
        is AuthState.Loading -> Box(Modifier.fillMaxSize().background(TrippinTheme.colors.paper)) {
            LoadingBlock("Loading")
        }
        // A shared link opened while signed out still needs an account first: the link is not lost,
        // just held until SignedInShell exists to act on it.
        is AuthState.SignedOut -> SignInScreen(onSignedIn = {})
        is AuthState.SignedIn -> {
            LaunchedEffect(state.account.id) { viewModel.revalidate() }
            SignedInShell(viewModel, pendingSharePath, onSharePathConsumed)
        }
    }
}

private enum class ShellTab(val label: String, val icon: ImageVector) {
    TRIPS("Trips", Icons.Default.CardTravel),
    PLAN("Plan", Icons.Default.CalendarMonth),
    GROUP("Group", Icons.Default.Group),
    YOU("You", Icons.Default.Person)
}

@Composable
private fun SignedInShell(
    viewModel: ShellViewModel,
    pendingSharePath: String?,
    onSharePathConsumed: () -> Unit
) {
    val navController = rememberNavController()
    val currentTripId by viewModel.currentTripId.collectAsStateWithLifecycle()
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
            navController.navigate(Plan(tripId)) { launchSingleTop = true }
            viewModel.clearResolvedShareTrip()
        }
    }

    Column(Modifier.fillMaxSize().background(TrippinTheme.colors.paper)) {
        Box(Modifier.weight(1f)) {
            NavHost(
                navController = navController,
                startDestination = Trips
            ) {
                composable<Trips> {
                    TripsScreen(
                        onOpenTrip = { id ->
                            viewModel.openTrip(id)
                            navController.navigate(Plan(id))
                        },
                        onOpenToday = { id ->
                            viewModel.openTrip(id)
                            navController.navigate(Today(id))
                        },
                        onStartPlanner = { destination ->
                            navController.navigate(Planner(destination.orEmpty()))
                        }
                    )
                }

                composable<Plan> { entry ->
                    val tripId = entry.toRoute<Plan>().tripId
                    if (tripId.isBlank()) {
                        NoTripOpen("No trip open", "Pick a trip on the Trips tab and its plan opens here, with the days planned and the stops for the day you are on.")
                    } else {
                        LaunchedEffect(tripId) { viewModel.openTrip(tripId) }
                        ItineraryScreen(
                            tripId = tripId,
                            onBack = { navController.navigate(Trips) { launchSingleTop = true } },
                            onOpenMap = { navController.navigate(MapView(it)) }
                        )
                    }
                }

                composable<Group> { entry ->
                    val tripId = entry.toRoute<Group>().tripId
                    if (tripId.isBlank()) {
                        NoTripOpen("No trip open", "Pick a trip on the Trips tab, then the people going and what each of them wants is here.")
                    } else {
                        LaunchedEffect(tripId) { viewModel.openTrip(tripId) }
                        GroupScreen(tripId = tripId)
                    }
                }

                composable<You> {
                    ProfileScreen(
                        onOpenTrips = { navController.navigate(Trips) { launchSingleTop = true } }
                    )
                }

                composable<Planner> { entry ->
                    PlannerScreen(
                        initialDestination = entry.toRoute<Planner>().destination,
                        onBack = { navController.popBackStack() },
                        onTripCreated = { id ->
                            viewModel.openTrip(id)
                            navController.navigate(Plan(id)) { popUpTo(Trips) }
                        }
                    )
                }

                composable<Today> { entry ->
                    val tripId = entry.toRoute<Today>().tripId
                    TodayScreen(
                        tripId = tripId,
                        onBack = { navController.popBackStack() },
                        onOpenMap = { navController.navigate(MapView(tripId)) }
                    )
                }

                composable<MapView> { entry ->
                    MapScreen(
                        tripId = entry.toRoute<MapView>().tripId,
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

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
                    ShellTab.TRIPS -> navController.navigate(Trips, tabOptions)
                    ShellTab.PLAN -> navController.navigate(Plan(currentTripId.orEmpty()), tabOptions)
                    ShellTab.GROUP -> navController.navigate(Group(currentTripId.orEmpty()), tabOptions)
                    ShellTab.YOU -> navController.navigate(You, tabOptions)
                }
            }
        )
    }
}

@Composable
private fun TrippinBottomBar(
    currentDestination: androidx.navigation.NavDestination?,
    onSelect: (ShellTab) -> Unit
) {
    val colors = TrippinTheme.colors
    Column(Modifier.fillMaxWidth().background(colors.panel)) {
        Box(Modifier.fillMaxWidth().height(2.5.dp).background(colors.line))
        Row(
            Modifier
                .fillMaxWidth()
                .background(colors.panel)
                .navigationBarsPadding()
                .heightIn(min = 64.dp)
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
                        contentDescription = tab.label,
                        tint = if (selected) colors.accent else colors.inkMuted,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tab.label,
                        style = TrippinType.Caption,
                        color = if (selected) colors.ink else colors.inkMuted
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
            ShellTab.TRIPS -> node.hasRoute(Trips::class)
            ShellTab.PLAN -> node.hasRoute(Plan::class)
            ShellTab.GROUP -> node.hasRoute(Group::class)
            ShellTab.YOU -> node.hasRoute(You::class)
        }
    }
}

@Composable
private fun NoTripOpen(headline: String, body: String) {
    MessageState(
        icon = Icons.Default.CalendarMonth,
        title = headline,
        body = body
    )
}
