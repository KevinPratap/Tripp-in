package com.trippin.core.common

/**
 * The one traveller vocabulary, in one place.
 *
 * The engine matches a stop against exactly these twelve interest words, so the form may offer these
 * and only these: a chip the engine could never match on would be a promise the plan cannot keep.
 * Every screen that offers interests (the planner, the group sheets, the join sheet) reads this list
 * rather than carrying its own copy that could drift from it.
 */
val InterestWords: List<String> = listOf(
    "culture", "food", "nightlife", "nature", "adventure", "shopping",
    "museums", "history", "photography", "wellness", "relaxation", "landmark"
)

/**
 * The pace a trip is created with, in the wording the backend accepts for trip creation. This is a
 * different set from a traveller's own pace: a trip is planned RELAXED, MODERATE or FAST.
 */
val TripPaceChoices: List<Pair<String, String>> = listOf(
    "RELAXED" to "Relaxed",
    "MODERATE" to "Moderate",
    "FAST" to "Fast"
)

/** The pace values the traveller endpoint accepts, in the wording a person reads. */
val TravellerPaceChoices: List<Pair<String, String>> = listOf(
    "relaxed" to "Relaxed",
    "balanced" to "Balanced",
    "packed" to "Packed"
)
