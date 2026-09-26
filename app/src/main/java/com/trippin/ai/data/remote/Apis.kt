package com.trippin.ai.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

// All three services are free and need no API key.

/** Open-Meteo geocoding: city name → coordinates. */
interface GeocodingApi {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1,
        @Query("language") language: String = "en",
    ): GeocodingResponse
}

@Serializable
data class GeocodingResponse(val results: List<GeoPlace>? = null)

@Serializable
data class GeoPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val country: String? = null,
)

/** Open-Meteo forecast: hourly rain probability and temperature (feeds Bayes + fuzzy). */
interface WeatherApi {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") lat: Double,
        @Query("longitude") lng: Double,
        @Query("hourly") hourly: String = "precipitation_probability,temperature_2m",
        @Query("forecast_days") days: Int = 16,
        @Query("timezone") timezone: String = "auto",
    ): ForecastResponse
}

@Serializable
data class ForecastResponse(val hourly: Hourly? = null)

@Serializable
data class Hourly(
    val time: List<String> = emptyList(),
    @SerialName("precipitation_probability") val rain: List<Int?> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
)

/** Photon (komoot, OpenStreetMap data): free-text search for cities and places, no API key. */
interface PhotonApi {
    @GET("api/")
    suspend fun search(
        @Query("q") q: String,
        @Query("limit") limit: Int = 8,
        @Query("lang") lang: String = "en",
        @Query("lat") lat: Double? = null,
        @Query("lon") lon: Double? = null,
        @Query("osm_tag") osmTag: String? = null,
    ): PhotonResponse
}

@Serializable
data class PhotonResponse(val features: List<PhotonFeature> = emptyList())

@Serializable
data class PhotonFeature(val geometry: PhotonGeometry, val properties: PhotonProperties)

@Serializable
data class PhotonGeometry(val coordinates: List<Double> = emptyList())

@Serializable
data class PhotonProperties(
    val name: String? = null,
    val country: String? = null,
    val state: String? = null,
    val city: String? = null,
    val street: String? = null,
    val type: String? = null,
    @SerialName("osm_key") val osmKey: String? = null,
    @SerialName("osm_value") val osmValue: String? = null,
)

/** Overpass (OpenStreetMap): real places, with opening hours when venues publish them. */
interface OverpassApi {
    @GET("api/interpreter")
    suspend fun query(@Query("data") data: String): OverpassResponse
}

@Serializable
data class OverpassResponse(val elements: List<OsmElement> = emptyList())

@Serializable
data class OsmElement(
    val id: Long,
    val lat: Double? = null,
    val lon: Double? = null,
    val center: OsmCenter? = null,
    val tags: Map<String, String> = emptyMap(),
)

@Serializable
data class OsmCenter(val lat: Double, val lon: Double)

object OverpassQuery {
    /** Museums, sights, food, parks, markets and bars within [radiusM] of a point. */
    fun around(lat: Double, lng: Double, radiusM: Int = 4000): String {
        val a = "around:$radiusM,$lat,$lng"
        return """
            [out:json][timeout:25];
            (
              nwr($a)["tourism"~"museum|gallery|attraction|viewpoint"]["name"];
              nwr($a)["amenity"~"restaurant|cafe|marketplace|bar|pub"]["name"]["opening_hours"];
              nwr($a)["leisure"~"park|garden"]["name"];
              nwr($a)["historic"]["name"];
            );
            out center 250;
        """.trimIndent()
    }
}
