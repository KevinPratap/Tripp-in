package com.trippin.core.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.trippin.feature.home.HomeScreen
import com.trippin.feature.planner.TripPlannerScreen
import com.trippin.feature.generating.GeneratingScreen
import com.trippin.feature.itinerary.ItineraryScreen
import com.trippin.feature.explore.ExploreScreen
import com.trippin.feature.map.MapScreen
import com.trippin.feature.profile.ProfileScreen

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
                onNavigateToPlanner = { navController.navigate(Screen.Planner.route) },
                onNavigateToTrip = { tripId -> navController.navigate(Screen.Itinerary.createRoute(tripId)) },
                onNavigateToExplore = { navController.navigate(Screen.Explore.route) },
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) }
            )
        }

        composable(Screen.Planner.route) {
            TripPlannerScreen(
                onNavigateBack = { navController.popBackStack() },
                onTripCreated = { tripId ->
                    navController.navigate(Screen.Generating.createRoute(tripId)) {
                        popUpTo(Screen.Planner.route) { inclusive = true }
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
                        popUpTo(Screen.Generating.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Itinerary.route) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "default"
            ItineraryScreen(
                tripId = tripId,
                onNavigateBack = { navController.navigate(Screen.Home.route) { popUpTo(0) } },
                onOpenMap = { navController.navigate(Screen.Map.createRoute(tripId)) }
            )
        }

        composable(Screen.Explore.route) {
            ExploreScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Map.route) { backStackEntry ->
            val tripId = backStackEntry.arguments?.getString("tripId") ?: "default"
            MapScreen(
                tripId = tripId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Profile.route) {
            ProfileScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
