package com.trippin.core.network

import kotlinx.serialization.Serializable

@Serializable
data class HomeFeedDto(
    val user: UserProfileDto,
    val recentTrips: List<TripSummaryDto>,
    val recommendedDestinations: List<DestinationCardDto>,
    val popularDestinations: List<DestinationCardDto>
)

@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    val displayName: String,
    val photoUrl: String? = null
)

@Serializable
data class TripSummaryDto(
    val id: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val travelersCount: Int,
    val status: String,
    val heroImageUrl: String? = null,
    val totalActivitiesCount: Int = 0,
    val currentVersion: Int = 1
)

@Serializable
data class DestinationCardDto(
    val id: String,
    val name: String,
    val country: String,
    val imageUrl: String,
    val description: String,
    val averageRating: Double,
    val tags: List<String> = emptyList()
)

@Serializable
data class CreateTripDto(
    val destination: String,
    val startDate: String,
    val endDate: String,
    val travelersCount: Int,
    val budgetTotal: Double? = null,
    val currency: String = "USD",
    val travelStyles: List<String> = emptyList(),
    val interests: List<String> = emptyList(),
    val pace: String = "MODERATE"
)

@Serializable
data class CreateTripResponseDto(
    val tripId: String,
    val status: String
)

@Serializable
data class GenerateTripResponseDto(
    val tripId: String,
    val jobId: String,
    val status: String,
    val estimatedSeconds: Int
)

@Serializable
data class TripStatusResponseDto(
    val tripId: String,
    val status: String,
    val progressPercentage: Int,
    val currentStepMessage: String,
    val errorMessage: String? = null
)

@Serializable
data class TripDetailsDto(
    val trip: TripSummaryDto,
    val itinerary: ItineraryDto? = null
)

@Serializable
data class ItineraryDto(
    val id: String,
    val tripId: String,
    val version: Int,
    val status: String,
    val days: List<ItineraryDayDto> = emptyList()
)

@Serializable
data class ItineraryDayDto(
    val id: String,
    val date: String,
    val dayIndex: Int,
    val summary: String? = null,
    val weatherSummary: String? = null,
    val activities: List<ActivityDto> = emptyList()
)

@Serializable
data class ActivityDto(
    val id: String,
    val placeId: String,
    val title: String,
    val type: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val travelTimeFromPreviousMinutes: Int = 0,
    val estimatedCost: Double? = null,
    val currency: String? = null,
    val reason: String? = null
)

@Serializable
data class ModifyItineraryRequestDto(
    val instruction: String
)

@Serializable
data class ModifyItineraryResponseDto(
    val itineraryId: String? = null,
    val newVersion: Int? = null,
    val appliedChangesSummary: String? = null,
    val updatedItinerary: ItineraryDto? = null
)

/**
 * A real place from the API (OpenStreetMap backed). The API deliberately returns no
 * ratings or review counts, so none are modelled here and none may be invented.
 */
@Serializable
data class PlaceSearchResultDto(
    val id: String = "",
    val name: String = "",
    val formattedAddress: String = "",
    val types: List<String> = emptyList(),
    val location: GeoPointDto? = null
)

@Serializable
data class GeoPointDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
