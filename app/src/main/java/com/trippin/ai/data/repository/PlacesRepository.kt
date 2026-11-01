package com.trippin.ai.data.repository

import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.PlaceHit
import com.trippin.ai.data.remote.ForecastResponse
import com.trippin.ai.data.remote.OverpassApi
import com.trippin.ai.data.remote.OverpassQuery
import com.trippin.ai.data.remote.PhotonApi
import com.trippin.ai.data.remote.PhotonFeature
import com.trippin.ai.data.remote.WeatherApi
import com.trippin.intelligence.model.OsmMapper
import com.trippin.intelligence.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class DayWeather(val date: LocalDate, val rainProbability: Double, val maxTempC: Double, val known: Boolean)

/**
 * Real-world data from free, key-less OpenStreetMap services:
 * Photon for search, Overpass for places near a point, Open-Meteo for weather.
 * Failures are reported as failures — the app never silently swaps in another city.
 */
class PlacesRepository(
    private val photon: PhotonApi,
    private val overpass: OverpassApi,
    private val overpassMirror: OverpassApi,
    private val weather: WeatherApi,
) {
    private val cacheLock = Mutex()
    private val nearbyCache = HashMap<String, List<Place>>()

    /** Cities, towns and regions matching [query]. */
    suspend fun searchDestinations(query: String): Result<List<PlaceHit>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        photon.search(query.trim(), limit = 8, osmTag = "place").features
            .mapNotNull { it.toHit(asDestination = true) }
            .distinctBy { it.name + it.country }
    }

    /** Named places near a destination, e.g. "Louvre" near Paris. */
    suspend fun searchPlaces(query: String, near: Destination): Result<List<PlaceHit>> = runCatching {
        if (query.isBlank()) return@runCatching emptyList()
        photon.search(query.trim(), limit = 10, lat = near.lat, lon = near.lng).features
            .mapNotNull { it.toHit(asDestination = false) }
            .filter { com.trippin.intelligence.model.Geo.haversineKm(near.lat, near.lng, it.lat, it.lng) < 80 }
    }

    /**
     * Real places around a point, from OpenStreetMap. Tries a second Overpass server when the
     * first is busy, and caches per area for the session.
     */
    suspend fun nearby(lat: Double, lng: Double, days: Int): Result<List<Place>> = withContext(Dispatchers.IO) {
        val radius = (3000 + days * 1200).coerceAtMost(9000)
        val key = "%.3f,%.3f,%d".format(lat, lng, radius)
        cacheLock.withLock { nearbyCache[key] }?.let { return@withContext Result.success(it) }
        val query = OverpassQuery.around(lat, lng, radius)
        val result = runCatching { overpass.query(query) }.recoverCatching { overpassMirror.query(query) }
        result.map { r ->
            r.elements.mapNotNull { e ->
                val pLat = e.lat ?: e.center?.lat ?: return@mapNotNull null
                val pLng = e.lon ?: e.center?.lon ?: return@mapNotNull null
                OsmMapper.toPlace("osm:${e.id}", pLat, pLng, e.tags)
            }.distinctBy { it.name.lowercase() }
        }.onSuccess { places -> if (places.isNotEmpty()) cacheLock.withLock { nearbyCache[key] = places } }
    }

    /**
     * Daytime (09–18h) rain chance and peak temperature per trip day. Days beyond the 16-day
     * forecast get a neutral 30% marked `known = false`, so the UI can say it's a guess.
     */
    suspend fun forecast(lat: Double, lng: Double, start: LocalDate, days: Int): List<DayWeather> {
        val f = runCatching { weather.forecast(lat, lng) }.getOrNull()
        return (0 until days).map { d -> summarise(f, start.plusDays(d.toLong())) }
    }

    private fun summarise(f: ForecastResponse?, date: LocalDate): DayWeather {
        val h = f?.hourly ?: return DayWeather(date, 0.3, 28.0, false)
        val prefix = date.toString()
        val idx = h.time.indices.filter { i ->
            h.time[i].startsWith(prefix) && h.time[i].substringAfter('T').take(2).toIntOrNull() in 9..18
        }
        val rains = idx.mapNotNull { h.rain.getOrNull(it) }
        if (rains.isEmpty()) return DayWeather(date, 0.3, 28.0, false)
        val temp = idx.mapNotNull { h.temperature.getOrNull(it) }.maxOrNull() ?: 28.0
        return DayWeather(date, rains.average() / 100.0, temp, true)
    }

    private fun PhotonFeature.toHit(asDestination: Boolean): PlaceHit? {
        val p = properties
        val name = p.name ?: return null
        val c = geometry.coordinates
        if (c.size < 2) return null
        val category = if (asDestination) null else OsmMapper.category(mapOf((p.osmKey ?: "") to (p.osmValue ?: "")))
        val detail = listOfNotNull(
            p.osmValue?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }?.takeIf { !asDestination },
            p.city?.takeIf { it != name },
            p.state?.takeIf { asDestination && it != name },
        ).joinToString(" · ").ifBlank { null }
        return PlaceHit(name = name, detail = detail, country = p.country, lat = c[1], lng = c[0], category = category)
    }
}
