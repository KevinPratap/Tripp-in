package com.trippin.core.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Trips : Screen("trips")

    /**
     * The Plan tab. Carries the trip it is about as an argument, because a tab needs a stable route
     * to be selectable while the trip itself is chosen at runtime.
     */
    object Plan : Screen("plan?tripId={tripId}") {
        fun createRoute(tripId: String): String = "plan?tripId=$tripId"
        val noTripRoute: String = "plan?tripId="
    }

    /** The Group tab: the people, their budgets and interests, and the open decisions. */
    object Group : Screen("group?tripId={tripId}") {
        fun createRoute(tripId: String): String = "group?tripId=$tripId"
        val noTripRoute: String = "group?tripId="
    }

    /** The You tab. */
    object You : Screen("you")
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
