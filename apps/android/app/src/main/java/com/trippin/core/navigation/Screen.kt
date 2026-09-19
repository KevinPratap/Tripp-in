package com.trippin.core.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Trips : Screen("trips")
    object Planner : Screen("planner?destination={destination}") {
        fun createRoute(destination: String? = null): String =
            if (destination.isNullOrBlank()) "planner?destination=" else "planner?destination=${destination.trim()}"
    }
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
    object Today : Screen("today/{tripId}") {
        fun createRoute(tripId: String) = "today/$tripId"
    }
    object Profile : Screen("profile")
}
