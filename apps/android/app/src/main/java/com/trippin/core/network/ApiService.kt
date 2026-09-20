package com.trippin.core.network

import retrofit2.http.*

interface ApiService {
    @GET("api/v1/home")
    suspend fun getHome(): HomeFeedDto

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

    @POST("api/v1/trips/{id}/lock")
    suspend fun lockTrip(@Path("id") tripId: String): LockResponseDto

    @POST("api/v1/trips/{id}/unlock")
    suspend fun unlockTrip(@Path("id") tripId: String): LockResponseDto

    @POST("api/v1/trips/{id}/replan")
    suspend fun replanTrip(
        @Path("id") tripId: String,
        @Body body: ReplanRequestDto
    ): ReplanResponseDto

    @DELETE("api/v1/trips/{id}")
    suspend fun deleteTrip(@Path("id") tripId: String): DeleteResponseDto

    @GET("api/v1/trips/{id}/travellers")
    suspend fun getTravellers(@Path("id") tripId: String): List<TravellerDto>

    @POST("api/v1/trips/{id}/travellers")
    suspend fun addTraveller(
        @Path("id") tripId: String,
        @Body body: CreateTravellerRequestDto
    ): TravellerDto

    @PATCH("api/v1/trips/{id}/travellers/{tid}")
    suspend fun updateTraveller(
        @Path("id") tripId: String,
        @Path("tid") travellerId: String,
        @Body body: UpdateTravellerRequestDto
    ): TravellerDto

    @DELETE("api/v1/trips/{id}/travellers/{tid}")
    suspend fun deleteTraveller(
        @Path("id") tripId: String,
        @Path("tid") travellerId: String
    ): retrofit2.Response<Unit>

    @POST("api/v1/trips/join")
    suspend fun joinTrip(@Body body: JoinTripRequestDto): JoinTripResponseDto
}
