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
}
