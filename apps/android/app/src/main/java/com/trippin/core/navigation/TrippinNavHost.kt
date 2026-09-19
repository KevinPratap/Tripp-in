package com.trippin.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.trippin.feature.home.HomeScreen
import com.trippin.feature.planner.TripPlannerScreen
import com.trippin.feature.generating.GeneratingScreen
import com.trippin.feature.itinerary.ItineraryScreen
import com.trippin.feature.explore.ExploreScreen
import com.trippin.feature.map.MapScreen
import com.trippin.feature.profile.ProfileScreen
import com.trippin.feature.today.TodayScreen
import com.trippin.feature.trips.TripsScreen

@Composable
fun TrippinNavHost(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onNavigateToPlanner = { city ->
                    navController.navigate(Screen.Planner.createRoute(city))
                },
                onNavigateToTrip = { tripId ->
                    navController.navigate(Screen.Itinerary.createRoute(tripId))
                },
                onNavigateToTrips = {
                    navController.navigate(Screen.Trips.route)
                },
                onNavigateToExplore = {
                    navController.navigate(Screen.Explore.route)
                },
                onNavigateToProfile = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }

        composable(Screen.Trips.route) {
            TripsScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTrip = { tripId ->
                    navController.navigate(Screen.Itinerary.createRoute(tripId))
                },
                onNavigateToToday = { tripId ->
                    navController.navigate(Screen.Today.createRoute(tripId))
                },
                onNavigateToPlanner = {
                    navController.navigate(Screen.Planner.createRoute())
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
        ) { backStackEntry ->
            val initialCity = backStackEntry.arguments?.getString("destination") ?: ""
            TripPlannerScreen(
                initialDestination = initialCity,
                onNavigateBack = { navController.popBackStack() },
                onTripCreated = { tripId ->
                    navController.navigate(Screen.Generating.createRoute(tripId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Generating.route) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "default"
            GeneratingScreen(
                tripId = tripId,
                onGenerationComplete = {
                    navController.navigate(Screen.Itinerary.createRoute(tripId)) {
                        popUpTo(Screen.Home.route)
                    }
                }
            )
        }

        composable(Screen.Itinerary.route) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "default"
            ItineraryScreen(
                tripId = tripId,
                onNavigateBack = { navController.navigate(Screen.Home.route) { popUpTo(0) } },
                onOpenMap = { navController.navigate(Screen.Map.createRoute(tripId)) },
                onNavigateToToday = { navController.navigate(Screen.Today.createRoute(tripId)) }
            )
        }

        composable(Screen.Today.route) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "default"
            TodayScreen(
                tripId = tripId,
                onNavigateBack = { navController.popBackStack() },
                onOpenMap = { navController.navigate(Screen.Map.createRoute(tripId)) }
            )
        }

        composable(Screen.Explore.route) {
            ExploreScreen(
                onNavigateBack = { navController.popBackStack() },
                onPlanCity = { city ->
                    navController.navigate(Screen.Planner.createRoute(city))
                }
            )
        }

        composable(Screen.Map.route) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "default"
            MapScreen(
                tripId = tripId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToTrips = { navController.navigate(Screen.Trips.route) }
            )
        }
    }
}
