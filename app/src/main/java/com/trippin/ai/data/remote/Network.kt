package com.trippin.ai.data.remote

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object Network {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Overpass asks clients to identify themselves.
            chain.proceed(chain.request().newBuilder().header("User-Agent", "TrippinAI-student-project/1.0").build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val geocoding: GeocodingApi by lazy { retrofit("https://geocoding-api.open-meteo.com/").create(GeocodingApi::class.java) }
    val weather: WeatherApi by lazy { retrofit("https://api.open-meteo.com/").create(WeatherApi::class.java) }
    val overpass: OverpassApi by lazy { retrofit("https://overpass-api.de/").create(OverpassApi::class.java) }
}
