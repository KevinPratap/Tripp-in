package com.trippin.core.navigation

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardTravel
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trippin.core.design.ComicInk
import com.trippin.core.design.ComicPanel
import com.trippin.core.design.ComicRed
import com.trippin.feature.generating.GeneratingScreen
import com.trippin.feature.group.GroupScreen
import com.trippin.feature.itinerary.ItineraryScreen
import com.trippin.feature.map.MapScreen
import com.trippin.feature.planner.TripPlannerScreen
import com.trippin.feature.profile.ProfileScreen
import com.trippin.feature.today.TodayScreen
import com.trippin.feature.trips.TripsScreen

/**
 * The shell.
 *
 * There is exactly one bottom bar in this app and it lives here. Before this, the only bottom bar was
 * hardcoded inside the home screen with "Home" permanently selected, so six of the nine screens had no
 * way out except the system back gesture. The bar was not useless because it had the wrong items,
 * although it did: it was useless because it existed on one screen and led away from the product.
 *
 * Each tab below is a job rather than a feature:
 *
 *  Trips  the trips you have, and starting a new one
 *  Plan   the trip you are working on: its days, today, its options, what changed, what it costs
 *  Group  the people you are going with and what each of them wants
 *  You    your own defaults, and where you have been
 *
 * Places that are not jobs are not tabs: the map is a view inside Today, adding a place is a sheet
 * inside Plan, and the planner and the generation progress are states of Plan rather than
 * destinations. Home is gone: its only defensible content was the list of trips, which is the first
 * tab.
 */
private enum class ShellTab(val route: String, val label: String, val icon: ImageVector) {
    TRIPS(Screen.Trips.route, "Trips", Icons.Default.CardTravel),
    PLAN(Screen.Plan.route, "Plan", Icons.Default.DateRange),
    GROUP(Screen.Group.route, "Group", Icons.Default.Group),
    YOU(Screen.You.route, "You", Icons.Default.Person)
}

@Composable
fun TrippinAppShell() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            val itemColors = NavigationBarItemDefaults.colors(
                selectedIconColor = ComicRed,
                selectedTextColor = ComicInk,
                indicatorColor = ComicRed.copy(alpha = 0.15f),
                unselectedIconColor = ComicInk.copy(alpha = 0.55f),
                unselectedTextColor = ComicInk.copy(alpha = 0.55f)
            )
            NavigationBar(
                containerColor = ComicPanel,
                tonalElevation = 0.dp,
                modifier = Modifier.border(width = 2.dp, color = ComicInk)
            ) {
                ShellTab.entries.forEach { tab ->
                    val selected = when (tab) {
                        ShellTab.TRIPS -> route?.startsWith("trips") == true
                        ShellTab.PLAN ->
                            route?.startsWith("plan") == true || route?.startsWith("itinerary") == true
                        ShellTab.GROUP -> route?.startsWith("group") == true
                        ShellTab.YOU ->
                            route?.startsWith("you") == true || route?.startsWith("profile") == true
                    }
                    NavigationBarItem(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label, fontWeight = FontWeight.Bold) },
                        selected = selected,
                        colors = itemColors,
                        onClick = {
                            when (tab) {
                                // Plan and Group are about a specific trip, so they resolve the trip
                                // that is currently open, and say so plainly when there is none.
                                ShellTab.PLAN -> {
                                    val tripId = CurrentTripStore.tripId(context)
                                    val target = if (tripId != null) {
                                        Screen.Plan.createRoute(tripId)
                                    } else {
                                        Screen.Plan.noTripRoute
                                    }
                                    navController.navigate(target) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                ShellTab.GROUP -> {
                                    val tripId = CurrentTripStore.tripId(context)
                                    val target = if (tripId != null) {
                                        Screen.Group.createRoute(tripId)
                                    } else {
                                        Screen.Group.noTripRoute
                                    }
                                    navController.navigate(target) {
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                else -> navController.navigate(tab.route) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            NavHost(
                navController = navController,
                startDestination = Screen.Trips.route
            ) {
                composable(Screen.Trips.route) {
                    TripsScreen(
                        // This is the start destination now, so there is nothing behind it to pop to.
                        onNavigateBack = {
                            if (navController.previousBackStackEntry != null) navController.popBackStack()
                        },
                        onNavigateToTrip = { tripId ->
                            CurrentTripStore.setTripId(context, tripId)
                            navController.navigate(Screen.Plan.createRoute(tripId))
                        },
                        onNavigateToToday = { tripId ->
                            CurrentTripStore.setTripId(context, tripId)
                            navController.navigate(Screen.Today.createRoute(tripId))
                        },
                        onNavigateToPlanner = {
                            navController.navigate(Screen.Planner.createRoute())
                        }
                    )
                }

                composable(
                    route = Screen.Plan.route,
                    arguments = listOf(
                        navArgument("tripId") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = true
                        }
                    )
                ) { entry ->
                    val tripId = entry.arguments?.getString("tripId").orEmpty()
                    if (tripId.isBlank()) {
                        NoTripSelected(
                            headline = "No trip open",
                            body = "Pick a trip on the Trips tab and its plan opens here, with the days, today, the options, what changed and what it costs."
                        )
                    } else {
                        CurrentTripStore.setTripId(context, tripId)
                        ItineraryScreen(
                            tripId = tripId,
                            onNavigateBack = { navController.navigate(Screen.Trips.route) { launchSingleTop = true } },
                            onOpenMap = { navController.navigate(Screen.Map.createRoute(it)) },
                            onNavigateToToday = { navController.navigate(Screen.Today.createRoute(it)) }
                        )
                    }
                }

                composable(
                    route = Screen.Group.route,
                    arguments = listOf(
                        navArgument("tripId") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = true
                        }
                    )
                ) { entry ->
                    val tripId = entry.arguments?.getString("tripId").orEmpty()
                    if (tripId.isBlank()) {
                        NoTripSelected(
                            headline = "No trip open",
                            body = "Pick a trip on the Trips tab, then the people going and what each of them wants is here."
                        )
                    } else {
                        CurrentTripStore.setTripId(context, tripId)
                        GroupScreen(tripId = tripId)
                    }
                }

                composable(Screen.You.route) {
                    ProfileScreen(
                        onNavigateBack = { navController.navigate(Screen.Trips.route) { launchSingleTop = true } },
                        onNavigateToTrips = {
                            navController.navigate(Screen.Trips.route) { launchSingleTop = true }
                        }
                    )
                }

                composable(
                    route = Screen.Planner.route,
                    arguments = listOf(
                        navArgument("destination") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = true
                        }
                    )
                ) { entry ->
                    TripPlannerScreen(
                        initialDestination = entry.arguments?.getString("destination").orEmpty(),
                        onNavigateBack = { navController.popBackStack() },
                        onTripCreated = { tripId ->
                            CurrentTripStore.setTripId(context, tripId)
                            navController.navigate(Screen.Generating.createRoute(tripId))
                        }
                    )
                }

                composable(Screen.Generating.route) { entry ->
                    val tripId = entry.arguments?.getString("tripId").orEmpty()
                    GeneratingScreen(
                        tripId = tripId,
                        onGenerationComplete = {
                            CurrentTripStore.setTripId(context, tripId)
                            navController.navigate(Screen.Plan.createRoute(tripId)) {
                                popUpTo(Screen.Trips.route)
                            }
                        }
                    )
                }

                composable(Screen.Itinerary.route) { entry ->
                    val tripId = entry.arguments?.getString("tripId").orEmpty()
                    CurrentTripStore.setTripId(context, tripId)
                    ItineraryScreen(
                        tripId = tripId,
                        onNavigateBack = { navController.navigate(Screen.Trips.route) { launchSingleTop = true } },
                        onOpenMap = { navController.navigate(Screen.Map.createRoute(tripId)) },
                        onNavigateToToday = { navController.navigate(Screen.Today.createRoute(tripId)) }
                    )
                }

                composable(Screen.Today.route) { entry ->
                    val tripId = entry.arguments?.getString("tripId").orEmpty()
                    TodayScreen(
                        tripId = tripId,
                        onNavigateBack = { navController.popBackStack() },
                        onOpenMap = { navController.navigate(Screen.Map.createRoute(tripId)) }
                    )
                }

                composable(Screen.Map.route) { entry ->
                    val tripId = entry.arguments?.getString("tripId").orEmpty()
                    MapScreen(
                        tripId = tripId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

/** Shown by a tab whose whole purpose is a trip, when no trip has been opened yet. */
@Composable
private fun NoTripSelected(headline: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = headline,
            fontWeight = FontWeight.Black,
            fontSize = 26.sp,
            color = ComicInk
        )
        Text(
            text = body,
            fontSize = 14.sp,
            color = ComicInk.copy(alpha = 0.7f),
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
