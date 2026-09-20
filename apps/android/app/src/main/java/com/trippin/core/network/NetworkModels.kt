package com.trippin.core.network

import kotlinx.serialization.Serializable

@Serializable
data class HomeFeedDto(
    val user: UserProfileDto,
    val recentTrips: List<TripSummaryDto>,
    val recommendedDestinations: List<DestinationCardDto>,
    val popularDestinations: List<DestinationCardDto>,
    val totalTripsCount: Int? = null
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
    val currentVersion: Int = 1,
    val isLocked: Boolean = false,
    val lockedAt: String? = null,
    val departureCity: String? = null,
    val currency: String? = null,
    val shareToken: String? = null,
    val travellers: List<TravellerDto> = emptyList(),
    val perTravellerCost: List<PerTravellerCostDto> = emptyList(),
    val options: List<TripOptionDto> = emptyList()
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
data class PlaceDto(
    val id: String = "",
    val googlePlaceId: String? = null,
    val name: String = "",
    val formattedAddress: String = "",
    val types: List<String> = emptyList(),
    val location: GeoPointDto? = null,
    val photoUrls: List<String> = emptyList()
)

/**
 * A single provenance receipt attached to an activity by the deterministic validator.
 * Each activity carries one check per verification dimension (geographic containment,
 * operating hours, transit feasibility, schedule buffer, weather forecast).
 */
@Serializable
data class VerificationCheckDto(
    val code: String,
    val label: String,
    val source: String,
    val status: String,
    val details: String? = null
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
    val reason: String? = null,
    val place: PlaceDto? = null,
    val photoUrls: List<String> = emptyList(),
    val checks: List<VerificationCheckDto> = emptyList(),
    val support: StopSupportDto? = null
) {
    val effectivePhotoUrl: String?
        get() = photoUrls.firstOrNull() ?: place?.photoUrls?.firstOrNull()
}

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
    val location: GeoPointDto? = null,
    val photoUrls: List<String> = emptyList(),
    val description: String? = null
)

@Serializable
data class GeoPointDto(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

@Serializable
data class ReplanRequestDto(
    val intent: String
)

@Serializable
data class ReplanResponseDto(
    val success: Boolean = true,
    val version: Int? = null,
    val itinerary: ItineraryDto? = null
)

@Serializable
data class LockResponseDto(
    val success: Boolean = true,
    val isLocked: Boolean = false,
    val lockedAt: String? = null
)

@Serializable
data class DeleteResponseDto(
    val success: Boolean = true,
    val id: String? = null
)

@Serializable
data class TravellerDto(
    val id: String,
    val name: String,
    val budgetCap: Double? = null,
    val interests: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val pace: String? = null,
    val joinedAt: String
)

@Serializable
data class PerTravellerCostDto(
    val travellerId: String,
    val shareMin: Double,
    val shareMax: Double,
    val overCap: Boolean
)

@Serializable
data class TripOptionDto(
    val id: String,
    val objective: String,
    val totalMin: Double,
    val totalMax: Double,
    val currency: String,
    val isFloor: Boolean,
    val headline: String
)

@Serializable
data class StopSupportDto(
    val want: Int,
    val total: Int,
    val against: List<String> = emptyList()
)

@Serializable
data class CreateTravellerRequestDto(
    val name: String,
    val budgetCap: Double? = null,
    val interests: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val pace: String? = null
)

@Serializable
data class UpdateTravellerRequestDto(
    val name: String? = null,
    val budgetCap: Double? = null,
    val interests: List<String>? = null,
    val dislikes: List<String>? = null,
    val pace: String? = null
)

@Serializable
data class JoinTripRequestDto(
    val token: String,
    val name: String,
    val budgetCap: Double? = null,
    val interests: List<String> = emptyList(),
    val dislikes: List<String> = emptyList(),
    val pace: String? = null
)

@Serializable
data class JoinTripResponseDto(
    val tripId: String,
    val traveller: TravellerDto
)


@Serializable
data class RequestMagicLinkDto(
    val email: String
)

/**
 * What POST /api/v1/auth/request-link answers with.
 *
 * `delivery` says how the link was delivered and this build renders it as it is: no mail provider is
 * configured on the server, so the answer is "console" and the link is in the log. The app states
 * that plainly instead of claiming an email was sent.
 */
@Serializable
data class RequestedMagicLinkDto(
    val email: String,
    val expiresAt: String,
    val delivery: String,
    val loginUrl: String
)

@Serializable
data class VerifyMagicLinkDto(
    val token: String,
    val email: String? = null,
    val guestSessionId: String? = null
)

@Serializable
data class SignedInUserDto(
    val id: String,
    val email: String,
    val displayName: String
)

/** The session token is the credential the app sends as Authorization: Bearer. */
@Serializable
data class VerifiedSessionDto(
    val sessionToken: String,
    val expiresAt: String,
    val user: SignedInUserDto,
    val migratedTrips: Int = 0
)

/** A public link to one trip, created on demand by its owner. */
@Serializable
data class ShareLinkDto(
    val token: String,
    val url: String,
    val createdAt: String
)

/**
 * One trip as GET /api/v1/me/trips summarises it. A different shape from TripSummaryDto on purpose:
 * this is the account's own list, not the home feed.
 */
@Serializable
data class SavedTripSummaryDto(
    val id: String,
    val destinationName: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val status: String = "",
    val itineraryStatus: String? = null,
    val dayCount: Int = 0,
    val stopCount: Int = 0,
    val currency: String? = null,
    val totalEstimatedCost: Double? = null,
    val shareToken: String? = null
)

@Serializable
data class MyTripsDto(
    val trips: List<SavedTripSummaryDto> = emptyList()
)
