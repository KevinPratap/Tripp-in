package com.trippin.core.sdui

import kotlinx.serialization.Serializable

@Serializable
data class SDUIScreenDto(
    val screenId: String,
    val title: String,
    val sections: List<SDUISectionDto> = emptyList()
)

@Serializable
data class SDUIActionDto(
    val type: String, // NAVIGATE, DEEP_LINK, API_CALL, OPEN_MODAL
    val target: String
)

@Serializable
data class SDUISectionDto(
    val id: String,
    val type: String, // HEADER_GREETING, SEARCH_BAR, HERO_BANNER, QUICK_ACTIONS, HORIZONTAL_CAROUSEL, RECENT_TRIPS_LIST, WEATHER_BANNER, TIMELINE_DAY, CALL_TO_ACTION
    val orderIndex: Int = 0,
    val title: String? = null,
    val subtitle: String? = null,
    val action: SDUIActionDto? = null
)
