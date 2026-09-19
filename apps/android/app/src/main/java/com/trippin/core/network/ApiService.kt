package com.trippin.core.network

import com.trippin.core.sdui.SDUIScreenDto
import retrofit2.http.*

interface ApiService {
    @GET("api/v1/home")
    suspend fun getHome(): HomeFeedDto

    @GET("api/v1/sdui/screens/{screenId}")
    suspend fun getSDUIScreen(@Path("screenId") screenId: String): SDUIScreenDto

    @POST("api/v1/trips")
    suspend fun createTrip(@Body body: CreateTripDto): CreateTripResponseDto

    @POST("api/v1/trips/{id}/generate")
    suspend fun triggerGeneration(@Path("id") tripId: String): GenerateTripResponseDto

    @GET("api/v1/trips/{id}/status")
    suspend fun getTripStatus(@Path("id") tripId: String): TripStatusResponseDto

    @GET("api/v1/trips/{id}")
    suspend fun getTripDetails(@Path("id") tripId: String): TripDetailsDto

    @POST("api/v1/itineraries/{id}/modify")
    suspend fun modifyItinerary(
        @Path("id") itineraryId: String,
        @Body body: ModifyItineraryRequestDto
    ): ModifyItineraryResponseDto

    @GET("api/v1/places/search")
    suspend fun searchPlaces(@Query("q") query: String): List<PlaceSearchResultDto>
}
