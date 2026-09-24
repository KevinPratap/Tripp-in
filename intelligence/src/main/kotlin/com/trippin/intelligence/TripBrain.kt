package com.trippin.intelligence

import com.trippin.intelligence.bayes.StopRiskModel
import com.trippin.intelligence.genetic.RouteOptimizer
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Place

/**
 * The one entry point the Android app talks to. It chains the four techniques:
 *
 *  1. Q-learning ranks which categories this traveller prefers (learned from feedback).
 *  2. Stops are picked from candidates matching those preferences and the pace budget.
 *  3. The genetic algorithm orders them into a feasible route.
 *  4. The Bayesian network scores each scheduled stop's risk of going wrong.
 *
 * The fuzzy fatigue controller runs live during the day, in Today mode.
 */
object TripBrain {

    enum class Pace(val activeMinutes: Int, val maxStops: Int) {
        RELAXED(330, 3), STANDARD(450, 4), PACKED(570, 6)
    }

    data class PlannedStop(val stop: RouteOptimizer.ScheduledStop, val risk: StopRiskModel.Assessment)

    data class DayPlan(
        val stops: List<PlannedStop>,
        val totalKm: Double,
        val violationMinutes: Int,
        val evolution: RouteOptimizer.Result,
    ) {
        /** "Verified" means the evolved route breaks no opening hours. */
        val verified: Boolean get() = violationMinutes == 0
    }

    /** Peak-hour windows used as Bayesian evidence: 08:30–10:30 and 17:30–20:30. */
    fun isPeak(minute: Int): Boolean = minute in 510..630 || minute in 1050..1230

    /**
     * Picks a day's stops round-robin across the preferred categories (best-liked first), so a
     * day mixes a museum, a meal and a sight instead of three museums. Candidates should already
     * be sorted nearest-first; stops are added while they fit the pace's time budget.
     */
    fun pickStops(candidates: List<Place>, preference: List<Category>, pace: Pace): List<Place> {
        val order = preference.ifEmpty { Category.entries }
        val queues = order.map { c -> ArrayDeque(candidates.filter { it.category == c }) }
        val picked = ArrayList<Place>()
        var minutes = 0
        var progress = true
        while (picked.size < pace.maxStops && progress) {
            progress = false
            for (q in queues) {
                if (picked.size == pace.maxStops) break
                val p = q.removeFirstOrNull() ?: continue
                progress = true
                if (minutes + p.visitMinutes <= pace.activeMinutes) {
                    picked += p
                    minutes += p.visitMinutes
                }
            }
        }
        return picked
    }

    fun planDay(
        start: Place,
        stops: List<Place>,
        dayStart: Int,
        rainProbability: Double,
        optimizer: RouteOptimizer = RouteOptimizer(),
    ): DayPlan {
        val evolved = optimizer.optimise(start, stops, dayStart)
        val planned = evolved.best.schedule.map { s ->
            PlannedStop(
                s,
                StopRiskModel.assess(
                    StopRiskModel.StopEvidence(
                        rainProbability = rainProbability,
                        peakHour = isPeak(s.arrive),
                        travelMinutes = s.travelMinutes,
                        hoursEstimated = s.place.hoursEstimated,
                    ),
                ),
            )
        }
        return DayPlan(planned, evolved.best.km, evolved.best.violationMinutes, evolved)
    }
}
