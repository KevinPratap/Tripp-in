package com.trippin.core.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Planner : Screen("planner")
    object Generating : Screen("generating/{tripId}") {
        fun createRoute(tripId: String) = "generating/$tripId"
    }
    object Itinerary : Screen("itinerary/{tripId}") {
        fun createRoute(tripId: String) = "itinerary/$tripId"
    }
    object Explore : Screen("explore")
    object Map : Screen("map/{tripId}") {
        fun createRoute(tripId: String) = "map/$tripId"
    }
    object Profile : Screen("profile")
}
