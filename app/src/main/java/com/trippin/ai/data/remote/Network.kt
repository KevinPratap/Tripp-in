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
            chain.proceed(chain.request().newBuilder().header("User-Agent", "TrippinAI/2.0 (Android)").build())
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = if (com.trippin.ai.BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE })
        .build()

    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val geocoding: GeocodingApi by lazy { retrofit("https://geocoding-api.open-meteo.com/").create(GeocodingApi::class.java) }
    val weather: WeatherApi by lazy { retrofit("https://api.open-meteo.com/").create(WeatherApi::class.java) }
    val overpass: OverpassApi by lazy { retrofit("https://overpass-api.de/").create(OverpassApi::class.java) }
    /** Second Overpass server, tried when the main one is busy (it rate-limits heavily). */
    val overpassMirror: OverpassApi by lazy { retrofit("https://overpass.kumi.systems/").create(OverpassApi::class.java) }
    val photon: PhotonApi by lazy { retrofit("https://photon.komoot.io/").create(PhotonApi::class.java) }
}
