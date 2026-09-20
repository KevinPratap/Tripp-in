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

    // ---- Accounts. The guest model is gone: these are the only ways to get an identity. ----

    @POST("api/v1/auth/request-link")
    suspend fun requestMagicLink(@Body body: RequestMagicLinkDto): RequestedMagicLinkDto

    @POST("api/v1/auth/verify")
    suspend fun verifyMagicLink(@Body body: VerifyMagicLinkDto): VerifiedSessionDto

    /**
     * The signed-in account's own trips, newest first. Also the session check: this route is
     * `@RequireIdentity`, so a 401 means the stored token is no longer good.
     */
    @GET("api/v1/me/trips")
    suspend fun getMyTrips(): MyTripsDto

    /** The invite link for a trip, created on demand. The owner only. */
    @POST("api/v1/trips/{id}/share")
    suspend fun createShareLink(@Path("id") tripId: String): ShareLinkDto
}
