package com.trippin.ai.data.repository

import com.trippin.ai.data.local.GenerationEntity
import com.trippin.ai.data.local.StopEntity
import com.trippin.ai.data.local.TripDao
import com.trippin.ai.data.local.TripEntity
import com.trippin.ai.data.local.TripWithStops
import com.trippin.ai.data.remote.ForecastResponse
import com.trippin.ai.data.remote.GeocodingApi
import com.trippin.ai.data.remote.OverpassApi
import com.trippin.ai.data.remote.OverpassQuery
import com.trippin.ai.data.remote.WeatherApi
import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Geo
import com.trippin.intelligence.model.OsmMapper
import com.trippin.intelligence.model.Place
import com.trippin.intelligence.model.SampleData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class PlannerInput(
    val destination: String,
    val startDate: LocalDate,
    val days: Int,
    val travellers: Int,
    val pace: TripBrain.Pace,
    val interests: List<Category>,
)

/** The stages the Building screen shows, in order. */
enum class BuildStage(val label: String, val technique: String) {
    GEOCODE("Finding the city", "Open-Meteo geocoding"),
    PLACES("Collecting real places", "OpenStreetMap · Overpass"),
    WEATHER("Reading the forecast", "Open-Meteo"),
    EVOLVE("Evolving the best route", "Genetic algorithm"),
    RISK("Scoring every stop's risk", "Bayesian network"),
    SAVE("Saving to your phone", "Room database"),
}

data class DayWeather(val rainProbability: Double, val temperatureC: Double)

class TripRepository(
    private val dao: TripDao,
    private val geocoding: GeocodingApi,
    private val weather: WeatherApi,
    private val overpass: OverpassApi,
    private val learning: LearningRepository,
) {
    fun trips(): Flow<List<TripEntity>> = dao.observeTrips()
    fun trip(id: Long): Flow<TripWithStops?> = dao.observeTrip(id)
    fun generations(tripId: Long, day: Int): Flow<List<GenerationEntity>> = dao.observeGenerations(tripId, day)
    suspend fun delete(id: Long) = dao.delete(id)

    /**
     * Builds and saves a whole trip. Network steps fall back to the offline demo city, so the
     * app still works (and the demo still runs) with no connection.
     */
    suspend fun createTrip(input: PlannerInput, onStage: suspend (BuildStage) -> Unit): Long = withContext(Dispatchers.IO) {
        onStage(BuildStage.GEOCODE)
        val geo = runCatching { geocoding.search(input.destination).results?.firstOrNull() }.getOrNull()

        onStage(BuildStage.PLACES)
        var usedDemo = false
        val centre: Place
        var candidates: List<Place> = emptyList()
        if (geo != null) {
            centre = Place("centre", "Centre of ${geo.name}", geo.latitude, geo.longitude, Category.LANDMARK, 0)
            candidates = runCatching { fetchPlaces(geo.latitude, geo.longitude) }.getOrDefault(emptyList())
        } else {
            centre = SampleData.hotel
        }
        if (candidates.size < input.days * 2) {
            usedDemo = true
            candidates = SampleData.mumbai
        }
        val start = if (usedDemo) SampleData.hotel else centre
        candidates = candidates.sortedBy { Geo.distanceKm(start, it) }

        onStage(BuildStage.WEATHER)
        val forecast = runCatching { weather.forecast(start.lat, start.lng) }.getOrNull()
        val dayWeather = (0 until input.days).map { d -> summarise(forecast, input.startDate.plusDays(d.toLong())) }

        onStage(BuildStage.EVOLVE)
        val preference = learning.preferenceOrder(input.interests)
        val remaining = candidates.toMutableList()
        val plans = (0 until input.days).map { d ->
            val stops = TripBrain.pickStops(remaining, preference, input.pace)
            remaining.removeAll(stops.toSet())
            TripBrain.planDay(start, stops, dayStart = 9 * 60 + 30, rainProbability = dayWeather[d].rainProbability)
        }

        onStage(BuildStage.RISK) // risk is computed inside planDay; this stage is shown for clarity.

        onStage(BuildStage.SAVE)
        val trip = TripEntity(
            destination = if (usedDemo) "Mumbai" else (geo?.name ?: input.destination),
            lat = start.lat,
            lng = start.lng,
            startDate = input.startDate.toString(),
            days = input.days,
            travellers = input.travellers,
            pace = input.pace.name,
            interests = input.interests.joinToString(",") { it.name },
            rainProbability = dayWeather.map { it.rainProbability }.average(),
            temperatureC = dayWeather.maxOf { it.temperatureC },
            verified = plans.all { it.verified },
            totalKm = plans.sumOf { it.totalKm },
            gaImprovementPercent = plans.map { it.evolution.improvementPercent }.average(),
            usedDemoData = usedDemo,
        )
        dao.insertPlan(
            trip,
            stops = { id ->
                plans.flatMapIndexed { day, plan ->
                    plan.stops.mapIndexed { i, ps ->
                        val s = ps.stop
                        StopEntity(
                            tripId = id, dayIndex = day, orderInDay = i, name = s.place.name,
                            lat = s.place.lat, lng = s.place.lng, category = s.place.category.name,
                            arrive = s.arrive, start = s.start, leave = s.leave, travelMinutes = s.travelMinutes,
                            hoursEstimated = s.place.hoursEstimated,
                            pDisrupted = ps.risk.pDisrupted, pDelay = ps.risk.pDelay, pClosed = ps.risk.pClosed,
                            pRainGivenDisrupted = ps.risk.pRainGivenDisrupted, riskLevel = ps.risk.level,
                        )
                    }
                }
            },
            generations = { id ->
                plans.flatMapIndexed { day, plan ->
                    plan.evolution.history.map { GenerationEntity(id, day, it.index, it.bestCost, it.averageCost) }
                }
            },
        )
    }

    private suspend fun fetchPlaces(lat: Double, lng: Double): List<Place> =
        overpass.query(OverpassQuery.around(lat, lng)).elements.mapNotNull { e ->
            val pLat = e.lat ?: e.center?.lat ?: return@mapNotNull null
            val pLng = e.lon ?: e.center?.lon ?: return@mapNotNull null
            OsmMapper.toPlace(e.id.toString(), pLat, pLng, e.tags)
        }.distinctBy { it.name }

    /** Daytime (09–18h) average rain chance and peak temperature; a neutral guess past the forecast range. */
    private fun summarise(f: ForecastResponse?, date: LocalDate): DayWeather {
        val h = f?.hourly ?: return DayWeather(0.3, 28.0)
        val prefix = date.toString()
        val idx = h.time.indices.filter { i ->
            h.time[i].startsWith(prefix) && h.time[i].substringAfter('T').take(2).toIntOrNull() in 9..18
        }
        if (idx.isEmpty()) return DayWeather(0.3, 28.0)
        val rain = idx.mapNotNull { h.rain.getOrNull(it) }.average().takeIf { !it.isNaN() } ?: 30.0
        val temp = idx.mapNotNull { h.temperature.getOrNull(it) }.maxOrNull() ?: 28.0
        return DayWeather(rain / 100.0, temp)
    }

    /** Used by the background worker to refresh the rain figure for an upcoming trip. */
    suspend fun refreshRain(trip: TripEntity): Double {
        val f = weather.forecast(trip.lat, trip.lng)
        val rain = summarise(f, LocalDate.parse(trip.startDate)).rainProbability
        dao.updateRain(trip.id, rain)
        return rain
    }

    suspend fun nextTrip(): TripEntity? = dao.nextTrip(LocalDate.now().toString())
}
