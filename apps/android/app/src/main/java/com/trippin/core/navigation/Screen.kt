package com.trippin.core.navigation

/**
 * Every route the app can actually reach.
 *
 * A route object exists here only while something navigates to it. Home, Explore and Profile were
 * declared for screens that are gone, and Itinerary was declared twice over: the Plan tab renders
 * the itinerary screen, so "itinerary/{tripId}" was a second way to the same screen that nothing
 * navigated to. All four are deleted rather than left as dead entries, because a route nothing
 * reaches is a screen that looks reachable and is not, and the bottom bar's own route checks had to
 * carry legacy prefixes ("itinerary", "profile") to keep them highlighted.
 */
sealed class Screen(val route: String) {
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
    object Map : Screen("map/{tripId}") {
        fun createRoute(tripId: String) = "map/$tripId"
    }
    object Today : Screen("today/{tripId}") {
        fun createRoute(tripId: String) = "today/$tripId"
    }
}
