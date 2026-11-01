package com.trippin.intelligence.group

import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.bayes.StopRiskModel
import com.trippin.intelligence.genetic.RouteOptimizer
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Geo
import com.trippin.intelligence.model.Place
import java.time.LocalDate
import kotlin.math.cos

/**
 * Turns what a group decided into a day-by-day plan. The AI is the planner, not the voter:
 *
 *   votes + individual constraints ──► [GroupPreferenceAggregator] ──► [GroupProfile]
 *   GroupProfile + real places     ──► clustering by area ──► GA per day ──► Bayes risk per stop
 *
 * Everything here is plain Kotlin so it is unit-tested without Android or Firebase.
 */
enum class VoteKind(val weight: Int) { MUST(3), UP(1), DOWN(-2) }

/** One place the group suggested, with each member's vote kept separately (not just a total). */
data class IdeaSignal(val ideaId: String, val place: Place, val votes: Map<String, VoteKind>) {
    val score: Int get() = votes.values.sumOf { it.weight }
    val mustCount: Int get() = votes.values.count { it == VoteKind.MUST }
    val downCount: Int get() = votes.values.count { it == VoteKind.DOWN }
    val upCount: Int get() = votes.values.count { it != VoteKind.DOWN }
}

enum class Diet { ANY, VEGETARIAN, VEGAN }
enum class StartPreference { EARLY, NORMAL, LATE }

data class MemberConstraints(
    val memberId: String,
    val name: String,
    val interests: Set<Category> = emptySet(),
    val diet: Diet = Diet.ANY,
    val noAlcohol: Boolean = false,
    val start: StartPreference = StartPreference.NORMAL,
    /** Most the member wants to walk in a day, in km; null = no limit. */
    val maxWalkKm: Int? = null,
    val accessibility: Boolean = false,
)

/** Everything the planner needs, gathered in one place (the spec's TripPlanningContext). */
data class PlanningContext(
    val start: Place,
    val startDate: LocalDate,
    val days: Int,
    val pace: TripBrain.Pace,
    val members: List<MemberConstraints>,
    val ideas: List<IdeaSignal>,
    /** Real nearby places (OpenStreetMap) used to fill the days around the group's picks. */
    val candidates: List<Place>,
    /** Categories this device's Q-learning agent has learned the traveller likes, best first. */
    val learnedOrder: List<Category> = Category.entries,
    val rainByDay: List<Double> = List(days) { 0.3 },
)

data class GroupProfile(
    val mustHave: List<IdeaSignal>,
    val preferred: List<IdeaSignal>,
    val avoided: List<IdeaSignal>,
    /** Share of members interested in each category, 0–1, tie-broken by learned taste. */
    val interestWeights: Map<Category, Double>,
    val dayStart: Int,
    val pace: TripBrain.Pace,
    val diet: Diet,
    val nightlifeOptional: Boolean,
    val accessibility: Boolean,
    /** Plain-English reasons, shown to the group so the plan never feels arbitrary. */
    val notes: List<String>,
)

object GroupPreferenceAggregator {

    fun profile(ctx: PlanningContext): GroupProfile {
        val members = ctx.members.ifEmpty { listOf(MemberConstraints("me", "You")) }
        val half = members.size / 2.0
        val notes = ArrayList<String>()

        // A MUST vote wins unless at least half the group voted against the place.
        val must = ctx.ideas.filter { it.mustCount > 0 && it.downCount < half }.sortedByDescending { it.score }
        val preferred = ctx.ideas.filter { it !in must && it.score > 0 }.sortedByDescending { it.score }
        val avoided = ctx.ideas.filter { it !in must && it.score <= 0 && it.downCount > 0 }
        ctx.ideas.filter { it.mustCount > 0 && it !in must }.forEach {
            notes += "${it.place.name} was a must-have for someone, but half the group voted it down, so it's left out."
        }

        val counts = Category.entries.associateWith { c -> members.count { c in it.interests }.toDouble() / members.size }
        val anyInterest = counts.values.any { it > 0 }
        val weights = Category.entries.associateWith { c ->
            val base = if (anyInterest) counts.getValue(c) else 0.5
            val learnedRank = ctx.learnedOrder.indexOf(c).let { if (it < 0) Category.entries.size else it }
            base + (Category.entries.size - learnedRank) * 0.01 // learned taste only breaks ties
        }

        // Start time: anyone asking for a late start is respected, because a late start costs the
        // early risers little, while an early start forces the late sleepers to skip a stop.
        val late = members.filter { it.start == StartPreference.LATE }
        val early = members.filter { it.start == StartPreference.EARLY }
        val dayStart = when {
            late.isNotEmpty() -> 10 * 60 + 30
            early.size > half -> 8 * 60 + 30
            else -> 9 * 60 + 30
        }
        if (late.isNotEmpty()) notes += "${names(late)} prefer${if (late.size == 1) "s" else ""} a late start, so days begin at 10:30."
        else if (early.size > half) notes += "Most of the group are early risers, so days begin at 08:30."

        // Walking limit: the whole group moves at the pace of whoever wants to walk least.
        var pace = ctx.pace
        val walkers = members.filter { it.maxWalkKm != null }
        val minWalk = walkers.minOfOrNull { it.maxWalkKm!! }
        if (minWalk != null && minWalk <= 6 && pace != TripBrain.Pace.RELAXED) {
            pace = if (minWalk <= 4) TripBrain.Pace.RELAXED else TripBrain.Pace.entries[maxOf(0, pace.ordinal - 1)]
            notes += "${names(walkers.filter { it.maxWalkKm == minWalk })} would rather walk under $minWalk km a day, so the pace is ${pace.name.lowercase()}."
        }

        val diet = when {
            members.any { it.diet == Diet.VEGAN } -> Diet.VEGAN
            members.any { it.diet == Diet.VEGETARIAN } -> Diet.VEGETARIAN
            else -> Diet.ANY
        }
        if (diet != Diet.ANY) {
            val who = members.filter { it.diet != Diet.ANY }
            notes += "Food stops favour ${diet.name.lowercase()}-friendly places for ${names(who)}."
        }

        val noAlcohol = members.filter { it.noAlcohol }
        val nightlifeOptional = noAlcohol.isNotEmpty() || early.isNotEmpty()
        if (noAlcohol.isNotEmpty() && (counts[Category.NIGHTLIFE] ?: 0.0) > 0) {
            notes += "Nightlife is marked optional, so ${names(noAlcohol)} can skip it and meet up after."
        }

        val access = members.filter { it.accessibility }
        if (access.isNotEmpty()) notes += "Step-free places are preferred for ${names(access)}."

        return GroupProfile(must, preferred, avoided, weights, dayStart, pace, diet, nightlifeOptional, access.isNotEmpty(), notes)
    }

    private fun names(ms: List<MemberConstraints>): String = when (ms.size) {
        0 -> ""
        1 -> ms[0].name
        2 -> "${ms[0].name} and ${ms[1].name}"
        else -> ms.dropLast(1).joinToString(", ") { it.name } + " and " + ms.last().name
    }
}

data class GroupStop(
    val stop: RouteOptimizer.ScheduledStop,
    val risk: StopRiskModel.Assessment,
    /** Set when the stop came from a group suggestion rather than the planner's own pick. */
    val ideaId: String?,
    /** Why it's here, e.g. "Must-have for 3" or "Fills the museum slot". */
    val reason: String,
    /** Optional stops (e.g. late nightlife) can be skipped by some members. */
    val optional: Boolean,
)

data class GroupDay(
    val date: LocalDate,
    val stops: List<GroupStop>,
    val totalKm: Double,
    val violationMinutes: Int,
    val evolution: RouteOptimizer.Result,
    val rainProbability: Double,
) {
    val verified: Boolean get() = violationMinutes == 0
}

data class GroupPlan(
    val profile: GroupProfile,
    val days: List<GroupDay>,
    /** Group picks that could not fit, so nobody's suggestion silently disappears. */
    val didNotFit: List<IdeaSignal>,
    /** Good places left over, offered as swaps in Today mode. */
    val alternates: List<Place>,
) {
    val notes: List<String> get() = profile.notes + didNotFit.map { "${it.place.name} didn't fit in ${days.size} day(s)." }
}

object GroupPlanner {

    fun plan(ctx: PlanningContext, optimizer: RouteOptimizer = RouteOptimizer()): GroupPlan {
        require(ctx.days >= 1) { "A trip needs at least one day" }
        val profile = GroupPreferenceAggregator.profile(ctx)
        val pace = profile.pace

        // 1. The pool: group picks first, then real places ranked by group interest.
        val avoidedNames = profile.avoided.map { it.place.name.lowercase() }.toSet()
        val ideaNames = ctx.ideas.map { it.place.name.lowercase() }.toSet()
        val fillers = ctx.candidates
            .filter { it.name.lowercase() !in avoidedNames && it.name.lowercase() !in ideaNames }
            .sortedWith(
                compareByDescending<Place> { fillerScore(it, profile) }
                    .thenBy { Geo.distanceKm(ctx.start, it) },
            )

        val capacity = pace.maxStops * ctx.days
        val budget = pace.activeMinutes * ctx.days
        val chosen = ArrayList<Pair<Place, Pick>>()
        var minutes = 0
        val didNotFit = ArrayList<IdeaSignal>()
        for (idea in profile.mustHave) {
            // Must-haves may exceed the pace a little: one extra stop per day at most.
            if (chosen.size < capacity + ctx.days) {
                chosen += idea.place to Pick(idea.ideaId, "Must-have for ${idea.mustCount}")
                minutes += idea.place.visitMinutes
            } else didNotFit += idea
        }
        for (idea in profile.preferred) {
            if (chosen.size < capacity && minutes + idea.place.visitMinutes <= budget) {
                chosen += idea.place to Pick(idea.ideaId, "${idea.upCount} up-vote${if (idea.upCount == 1) "" else "s"}")
                minutes += idea.place.visitMinutes
            } else didNotFit += idea
        }
        // Fill round-robin over the group's favourite categories so days stay varied.
        val order = Category.entries.sortedByDescending { profile.interestWeights[it] ?: 0.0 }
            .filter { (profile.interestWeights[it] ?: 0.0) > 0.05 }
        val queues = order.map { c -> ArrayDeque(fillers.filter { it.category == c }) }
        var progress = true
        while (chosen.size < capacity && progress) {
            progress = false
            for (q in queues) {
                if (chosen.size >= capacity) break
                val p = q.removeFirstOrNull() ?: continue
                progress = true
                if (minutes + p.visitMinutes <= budget) {
                    chosen += p to Pick(null, "Fits the group's interest in ${p.category.name.lowercase()}")
                    minutes += p.visitMinutes
                }
            }
        }

        // 2. Split into days by area, so each day stays in one part of town.
        val dates = (0 until ctx.days).map { ctx.startDate.plusDays(it.toLong()) }
        val perDay = pace.maxStops + 1
        val buckets = DayClustering.cluster(chosen.map { it.first }, ctx.days, perDay)
        val assignment = repairClosedDays(buckets, dates)

        // 3. Order each day with the GA and score every stop with the Bayesian network.
        val pickOf = chosen.associate { it.first.id to it.second }
        val days = assignment.mapIndexed { d, places ->
            val dow = dates[d].dayOfWeek.value
            val dayPlaces = withMealWindows(places.mapNotNull { it.forDay(dow) })
            val result = optimizer.optimise(ctx.start, dayPlaces, profile.dayStart)
            val rain = ctx.rainByDay.getOrElse(d) { 0.3 }
            val stops = result.best.schedule.map { s ->
                val pick = pickOf[s.place.id]
                GroupStop(
                    stop = s,
                    risk = StopRiskModel.assess(
                        StopRiskModel.StopEvidence(rain, TripBrain.isPeak(s.arrive), s.travelMinutes, s.place.hoursEstimated),
                    ),
                    ideaId = pick?.ideaId,
                    reason = pick?.reason ?: "",
                    optional = profile.nightlifeOptional && s.place.category == Category.NIGHTLIFE,
                )
            }
            GroupDay(dates[d], stops, result.best.km, result.best.violationMinutes, result, rain)
        }

        val used = chosen.map { it.first.id }.toSet()
        val alternates = fillers.filter { it.id !in used }.take(24)
        return GroupPlan(profile, days, didNotFit, alternates)
    }

    private data class Pick(val ideaId: String?, val reason: String)

    private fun foodFits(p: Place, diet: Diet) = when (diet) {
        Diet.ANY -> true
        Diet.VEGETARIAN -> Place.TAG_VEGETARIAN in p.tags
        Diet.VEGAN -> Place.TAG_VEGAN in p.tags
    }

    private fun fillerScore(p: Place, profile: GroupProfile): Double {
        var s = profile.interestWeights[p.category] ?: 0.0
        if (p.category == Category.FOOD && profile.diet != Diet.ANY && foodFits(p, profile.diet)) s += 0.5
        if (profile.accessibility && Place.TAG_WHEELCHAIR in p.tags) s += 0.3
        if (!p.hoursEstimated) s += 0.05 // published hours are more trustworthy
        return s
    }

    /**
     * Moves a place off a day it is closed on, swapping with a place on another day that is open
     * on both. Keeps day sizes unchanged.
     */
    internal fun repairClosedDays(days: List<List<Place>>, dates: List<LocalDate>): List<List<Place>> {
        val out = days.map { it.toMutableList() }
        for (d in out.indices) {
            val dow = dates[d].dayOfWeek.value
            for (i in out[d].indices) {
                val p = out[d][i]
                if (p.isOpenOn(dow)) continue
                swap@ for (e in out.indices) {
                    if (e == d || !p.isOpenOn(dates[e].dayOfWeek.value)) continue
                    for (j in out[e].indices) {
                        val q = out[e][j]
                        if (q.isOpenOn(dow)) {
                            out[d][i] = q
                            out[e][j] = p
                            break@swap
                        }
                    }
                }
            }
        }
        return out
    }

    /**
     * The first food stop of a day is lunch (12:00–15:00) and a second one is dinner
     * (18:30–21:30), narrowed to the place's own hours. The GA then schedules around meals.
     */
    internal fun withMealWindows(places: List<Place>): List<Place> {
        var meal = 0
        return places.map { p ->
            if (p.category != Category.FOOD) return@map p
            val (wOpen, wClose) = if (meal++ == 0) 12 * 60 to 15 * 60 else 18 * 60 + 30 to 21 * 60 + 30
            val open = maxOf(p.opensAt, wOpen)
            val close = minOf(p.closesAt, wClose)
            if (close - open >= p.visitMinutes) p.copy(opensAt = open, closesAt = close) else p
        }
    }
}

/**
 * Splits places into day-sized groups by location (k-means on a flat projection, seeded
 * deterministically), with a cap per day so one busy area can't swallow the trip.
 */
object DayClustering {

    fun cluster(places: List<Place>, k: Int, capacity: Int, iterations: Int = 12): List<List<Place>> {
        if (k <= 1 || places.isEmpty()) return listOf(places) + List(maxOf(0, k - 1)) { emptyList() }
        val lat0 = places.map { it.lat }.average()
        val kx = cos(Math.toRadians(lat0))
        fun xy(p: Place) = (p.lng * kx) to p.lat
        fun d2(a: Pair<Double, Double>, b: Pair<Double, Double>) =
            (a.first - b.first) * (a.first - b.first) + (a.second - b.second) * (a.second - b.second)

        // Farthest-point seeding: deterministic and spreads the days across the city.
        val pts = places.map(::xy)
        val centres = ArrayList<Pair<Double, Double>>()
        centres += pts.first()
        while (centres.size < k) centres += pts.maxBy { p -> centres.minOf { d2(it, p) } }

        var assignment = IntArray(places.size)
        repeat(iterations) {
            assignment = assignWithCapacity(pts, centres, maxOf(capacity, (places.size + k - 1) / k))
            for (c in 0 until k) {
                val members = pts.indices.filter { assignment[it] == c }
                if (members.isNotEmpty()) {
                    centres[c] = members.map { pts[it].first }.average() to members.map { pts[it].second }.average()
                }
            }
        }
        val groups = List(k) { c -> places.indices.filter { assignment[it] == c }.map { places[it] } }
        return balance(groups)
    }

    /** Greedy: the closest (point, centre) pairs are matched first, skipping full centres. */
    private fun assignWithCapacity(pts: List<Pair<Double, Double>>, centres: List<Pair<Double, Double>>, cap: Int): IntArray {
        val pairs = ArrayList<Triple<Double, Int, Int>>()
        pts.forEachIndexed { i, p ->
            centres.forEachIndexed { c, q ->
                pairs += Triple((p.first - q.first) * (p.first - q.first) + (p.second - q.second) * (p.second - q.second), i, c)
            }
        }
        pairs.sortBy { it.first }
        val out = IntArray(pts.size) { -1 }
        val load = IntArray(centres.size)
        for ((_, i, c) in pairs) {
            if (out[i] != -1 || load[c] >= cap) continue
            out[i] = c
            load[c]++
        }
        return out
    }

    /** No empty days while another day has two or more stops to spare. */
    private fun balance(groups: List<List<Place>>): List<List<Place>> {
        val g = groups.map { it.toMutableList() }
        while (true) {
            val empty = g.indexOfFirst { it.isEmpty() }
            val big = g.indices.maxBy { g[it].size }
            if (empty < 0 || g[big].size < 2) break
            g[empty] += g[big].removeAt(g[big].lastIndex)
        }
        return g
    }
}
