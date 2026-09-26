package com.trippin.ai.data.repository

import com.google.firebase.firestore.FieldValue
import com.trippin.ai.auth.Session
import com.trippin.ai.data.firebase.Backend
import com.trippin.ai.data.firebase.asFlow
import com.trippin.ai.data.firebase.now
import com.trippin.ai.data.firebase.toItinerary
import com.trippin.ai.data.firebase.toMap
import com.trippin.ai.data.firebase.toProposal
import com.trippin.ai.data.firebase.reactionsOf
import com.google.firebase.firestore.SetOptions
import com.trippin.intelligence.group.VoteKind
import com.trippin.ai.data.model.AltPlace
import com.trippin.ai.data.model.DecisionType
import com.trippin.ai.data.model.EventType
import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaStatus
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.ItineraryDay
import com.trippin.ai.data.model.ItineraryStop
import com.trippin.ai.data.model.MemberPrefs
import com.trippin.ai.data.model.Proposal
import com.trippin.ai.data.model.ProposalStatus
import com.trippin.ai.data.model.ProposalType
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripStatus
import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.bayes.StopRiskModel
import com.trippin.intelligence.genetic.RouteOptimizer
import com.trippin.intelligence.group.GroupPlan
import com.trippin.intelligence.group.GroupPlanner
import com.trippin.intelligence.group.IdeaSignal
import com.trippin.intelligence.group.MemberConstraints
import com.trippin.intelligence.group.PlanningContext
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Geo
import com.trippin.intelligence.model.OsmMapper
import com.trippin.intelligence.model.Place
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

/** Stages shown while a plan is generated, each naming the technique doing the work. */
enum class PlanStage(val label: String, val technique: String) {
    CONTEXT("Reading the group's votes and preferences", "Group preference model"),
    PLACES("Collecting real places nearby", "OpenStreetMap · Overpass"),
    WEATHER("Reading the forecast", "Open-Meteo"),
    EVOLVE("Splitting days by area and ordering stops", "k-means + genetic algorithm"),
    RISK("Scoring each stop's risk", "Bayesian network"),
    SAVE("Sharing the plan with the group", "Firestore"),
}

/**
 * The AI planner boundary. [buildContext] gathers everything (the spec's TripPlanningContext) so
 * the planner never reads raw database records; [generate] runs it and publishes a new version.
 * Edits after that are explicit: direct for owners/admins before the plan is confirmed, otherwise
 * through change proposals — the plan is never changed silently.
 */
class PlanRepository(
    private val backend: Backend,
    private val trips: TripRepository,
    private val places: PlacesRepository,
    private val learning: LearningRepository,
) {
    fun itinerary(tripId: String): Flow<Itinerary?> = backend.itinerary(tripId).asFlow().map { it?.toItinerary() }

    fun proposals(tripId: String): Flow<List<Proposal>> =
        backend.proposals(tripId).asFlow().map { d -> d.map { it.toProposal() }.sortedByDescending { it.createdAt } }

    suspend fun buildContext(trip: Trip, prefs: List<MemberPrefs>, ideas: List<Idea>, onStage: suspend (PlanStage) -> Unit): Result<PlanningContext> {
        val dest = trip.destination ?: return Result.failure(IllegalStateException("Pick a destination first."))
        onStage(PlanStage.CONTEXT)
        val start = trip.stay?.let { Place("start", it.name, it.lat, it.lng, Category.LANDMARK, 0) }
            ?: Place("start", "Centre of ${dest.name}", dest.lat, dest.lng, Category.LANDMARK, 0)

        onStage(PlanStage.PLACES)
        val nearby = places.nearby(start.lat, start.lng, trip.days)
        val candidates = nearby.getOrDefault(emptyList())
        val placeIdeas = ideas.filter { it.type == IdeaType.PLACE && it.status != IdeaStatus.DISMISSED && it.lat != null && it.lng != null }
        if (candidates.size < trip.days * 2 && placeIdeas.isEmpty()) {
            return Result.failure(
                IllegalStateException(
                    if (nearby.isFailure) "Couldn't reach OpenStreetMap to find places. Check your connection and try again."
                    else "Found too few places around ${start.name}. Add some suggestions in Ideas, or set a more central stay.",
                ),
            )
        }

        onStage(PlanStage.WEATHER)
        val weather = places.forecast(start.lat, start.lng, trip.startDate, trip.days)

        val memberSet = trip.memberIds.toSet()
        val signals = placeIdeas.map { idea ->
            val cat = idea.category ?: Category.LANDMARK
            // Borrow real opening hours from the matching OpenStreetMap place when there is one.
            val match = candidates.firstOrNull { it.name.equals(idea.title, true) && Geo.haversineKm(it.lat, it.lng, idea.lat!!, idea.lng!!) < 0.5 }
            val base = match ?: Place("x", idea.title, idea.lat!!, idea.lng!!, cat, OsmMapper.defaults(cat).first, OsmMapper.defaults(cat).second, OsmMapper.defaults(cat).third, hoursEstimated = true, tags = idea.tags)
            IdeaSignal(idea.id, base.copy(id = "idea:${idea.id}", name = idea.title, lat = idea.lat!!, lng = idea.lng!!), idea.votes.filterKeys { it in memberSet })
        }
        val members = prefs.filter { it.uid in memberSet }.map {
            MemberConstraints(it.uid, it.name, it.interests, it.diet, it.noAlcohol, it.start, it.maxWalkKm, it.accessibility)
        }
        return Result.success(
            PlanningContext(
                start = start,
                startDate = trip.startDate,
                days = trip.days,
                pace = trip.pace,
                members = members,
                ideas = signals,
                candidates = candidates,
                learnedOrder = learning.preferenceOrder(Category.entries),
                rainByDay = weather.map { it.rainProbability },
            ).also { lastWeather = weather },
        )
    }

    @Volatile private var lastWeather: List<DayWeather> = emptyList()

    /** Runs the planner and publishes the result as the next version for the whole group. */
    suspend fun generate(
        trip: Trip, me: Session, prefs: List<MemberPrefs>, ideas: List<Idea>, current: Itinerary?,
        onStage: suspend (PlanStage) -> Unit,
    ): Result<Itinerary> {
        val ctx = buildContext(trip, prefs, ideas, onStage).getOrElse { return Result.failure(it) }
        val weather = lastWeather
        onStage(PlanStage.EVOLVE)
        val plan = withContext(Dispatchers.Default) { GroupPlanner.plan(ctx) }
        onStage(PlanStage.RISK)
        val itinerary = toItinerary(plan, ctx.start, weather, (current?.version ?: 0) + 1, me)
        onStage(PlanStage.SAVE)
        val batch = backend.db.batch()
        batch.set(backend.itinerary(trip.id), itinerary.toMap())
        batch.update(backend.trip(trip.id), mapOf("status" to TripStatus.PLAN_READY.name, "updatedAt" to now()))
        trips.addEvent(batch, trip.id, me, EventType.ITINERARY_UPDATED, "${me.name} generated plan v${itinerary.version}")
        backend.fire(batch.commit(), "share the plan")
        return Result.success(itinerary)
    }

    private fun toItinerary(plan: GroupPlan, origin: Place, weather: List<DayWeather>, version: Int, me: Session): Itinerary {
        val days = plan.days.mapIndexed { d, day ->
            val w = weather.getOrNull(d)
            ItineraryDay(
                date = day.date,
                rainProbability = day.rainProbability,
                rainKnown = w?.known ?: false,
                verified = day.verified,
                km = day.totalKm,
                evolutionBest = day.evolution.history.filterIndexed { i, _ -> i % 5 == 0 }.map { it.bestCost },
                evolutionAverage = day.evolution.history.filterIndexed { i, _ -> i % 5 == 0 }.map { it.averageCost },
                stops = day.stops.mapIndexed { i, s ->
                    val p = s.stop.place
                    ItineraryStop(
                        key = newKey(d, i), name = p.name, lat = p.lat, lng = p.lng, category = p.category,
                        arrive = s.stop.arrive, start = s.stop.start, leave = s.stop.leave, travelMinutes = s.stop.travelMinutes,
                        opensAt = p.opensAt, closesAt = p.closesAt, hoursEstimated = p.hoursEstimated,
                        pDisrupted = s.risk.pDisrupted, pDelay = s.risk.pDelay, pClosed = s.risk.pClosed,
                        pRainGivenDisrupted = s.risk.pRainGivenDisrupted, risk = s.risk.level,
                        reason = s.reason, optional = s.optional, ideaId = s.ideaId,
                    )
                },
            )
        }
        val improvements = plan.days.map { it.evolution.improvementPercent }
        return Itinerary(
            version = version, generatedAt = System.currentTimeMillis(), generatedById = me.uid, generatedByName = me.name,
            locked = false, dayStart = plan.profile.dayStart,
            origin = AltPlace(origin.name, origin.lat, origin.lng, Category.LANDMARK), days = days,
            alternates = plan.alternates.map { AltPlace(it.name, it.lat, it.lng, it.category) },
            notes = plan.notes,
            gaImprovementPercent = if (improvements.isEmpty()) 0.0 else improvements.average(),
        )
    }

    /** Every member's 👍/👎 per stop: stop key → (uid → vote). Each member owns one reactions doc. */
    fun reactions(tripId: String): Flow<Map<String, Map<String, VoteKind>>> =
        backend.reactions(tripId).asFlow().map { reactionsOf(it) }

    /** 👍/👎 on a single stop; passing null clears it. */
    fun react(trip: Trip, me: Session, stopKey: String, kind: VoteKind?) {
        val value: Any = kind?.name ?: FieldValue.delete()
        backend.fire(
            backend.reactions(trip.id).document(me.uid).set(mapOf("votes" to mapOf(stopKey to value)), SetOptions.merge()),
            "save your reaction",
        )
    }

    // ---------------- Editing ----------------

    fun removeStop(trip: Trip, me: Session, itin: Itinerary, key: String, via: String? = null) {
        val stop = itin.stop(key) ?: return
        val days = itin.days.map { d -> if (d.stops.any { it.key == key }) retime(d.copy(stops = d.stops.filter { it.key != key }), itin, reorder = false) else d }
        publishEdit(trip, me, itin, days, via ?: "${me.name} removed ${stop.name}")
    }

    fun moveStop(trip: Trip, me: Session, itin: Itinerary, key: String, toDay: Int, via: String? = null) {
        val stop = itin.stop(key) ?: return
        if (toDay !in itin.days.indices) return
        val days = itin.days.mapIndexed { i, d ->
            when {
                i == toDay && d.stops.none { it.key == key } -> retime(d.copy(stops = d.stops + stop), itin, reorder = true)
                d.stops.any { it.key == key } && i != toDay -> retime(d.copy(stops = d.stops.filter { it.key != key }), itin, reorder = false)
                else -> d
            }
        }
        publishEdit(trip, me, itin, days, via ?: "${me.name} moved ${stop.name} to day ${toDay + 1}")
    }

    /** Adds a leftover place (from the planner's alternates) to a day. */
    fun addAlternate(trip: Trip, me: Session, itin: Itinerary, alt: AltPlace, toDay: Int) {
        if (toDay !in itin.days.indices) return
        val (visit, open, close) = OsmMapper.defaults(alt.category)
        val stop = ItineraryStop(
            key = newKey(toDay, itin.days[toDay].stops.size), name = alt.name, lat = alt.lat, lng = alt.lng, category = alt.category,
            arrive = 0, start = 0, leave = visit, travelMinutes = 0, opensAt = open, closesAt = close, hoursEstimated = true,
            pDisrupted = 0.0, pDelay = 0.0, pClosed = 0.0, pRainGivenDisrupted = 0.0, risk = "Low",
            reason = "Added by ${me.name}", optional = false, ideaId = null,
        )
        val days = itin.days.mapIndexed { i, d -> if (i == toDay) retime(d.copy(stops = d.stops + stop), itin, reorder = true) else d }
        publishEdit(trip, me, itin, days, "${me.name} added ${alt.name} to day ${toDay + 1}", alternates = itin.alternates - alt)
    }

    private fun publishEdit(trip: Trip, me: Session, itin: Itinerary, days: List<ItineraryDay>, text: String, alternates: List<AltPlace> = itin.alternates) {
        val next = itin.copy(version = itin.version + 1, days = days, alternates = alternates, generatedById = me.uid, generatedByName = me.name)
        val batch = backend.db.batch()
        batch.set(backend.itinerary(trip.id), next.toMap())
        trips.addEvent(batch, trip.id, me, EventType.ITINERARY_UPDATED, "$text (plan v${next.version})")
        backend.fire(batch.commit(), "update the plan")
    }

    /**
     * Recomputes a day's times after an edit. With [reorder] the GA re-orders the day (a stop was
     * added); otherwise the group's order is kept and only times and risks change.
     */
    private fun retime(day: ItineraryDay, itin: Itinerary, reorder: Boolean): ItineraryDay {
        if (day.stops.isEmpty()) return day.copy(km = 0.0, verified = true)
        val dayStart = itin.dayStart
        val start = Place("start", itin.origin.name, itin.origin.lat, itin.origin.lng, Category.LANDMARK, 0)
        val placeFor = day.stops.associate { s -> s.key to Place(s.key, s.name, s.lat, s.lng, s.category, (s.leave - s.start).coerceAtLeast(20), s.opensAt, s.closesAt, s.hoursEstimated) }
        val list = day.stops.map { placeFor.getValue(it.key) }
        val opt = RouteOptimizer()
        val eval = if (reorder && list.size >= 3) opt.optimise(start, list, dayStart).best else opt.evaluate(start, list, list.indices.toList(), dayStart)
        val byKey = day.stops.associateBy { it.key }
        val stops = eval.schedule.map { s ->
            val old = byKey.getValue(s.place.id)
            val risk = StopRiskModel.assess(StopRiskModel.StopEvidence(day.rainProbability, TripBrain.isPeak(s.arrive), s.travelMinutes, old.hoursEstimated))
            old.copy(
                arrive = s.arrive, start = s.start, leave = s.leave, travelMinutes = s.travelMinutes,
                pDisrupted = risk.pDisrupted, pDelay = risk.pDelay, pClosed = risk.pClosed, pRainGivenDisrupted = risk.pRainGivenDisrupted, risk = risk.level,
            )
        }
        return day.copy(stops = stops, km = eval.km, verified = eval.violationMinutes == 0)
    }

    fun confirm(trip: Trip, me: Session, itin: Itinerary) {
        val batch = backend.db.batch()
        batch.update(backend.itinerary(trip.id), "locked", true)
        batch.update(backend.trip(trip.id), mapOf("status" to TripStatus.CONFIRMED.name, "updatedAt" to now()))
        batch.set(
            backend.decisions(trip.id).document(),
            mapOf(
                "type" to DecisionType.ITINERARY_CONFIRMED.name, "refId" to "v${itin.version}", "title" to "Plan v${itin.version}",
                "decidedById" to me.uid, "decidedByName" to me.name, "at" to now(),
                "tally" to "${itin.days.sumOf { it.stops.size }} stops over ${itin.days.size} days",
            ),
        )
        trips.addEvent(batch, trip.id, me, EventType.ITINERARY_CONFIRMED, "${me.name} confirmed plan v${itin.version}")
        backend.fire(batch.commit(), "confirm the plan")
    }

    fun unlock(trip: Trip, me: Session) {
        val batch = backend.db.batch()
        batch.update(backend.itinerary(trip.id), "locked", false)
        batch.update(backend.trip(trip.id), mapOf("status" to TripStatus.PLAN_READY.name, "updatedAt" to now()))
        trips.addEvent(batch, trip.id, me, EventType.TRIP_UPDATED, "${me.name} re-opened the plan for changes")
        backend.fire(batch.commit(), "re-open the plan")
    }

    // ---------------- Change proposals ----------------

    fun propose(trip: Trip, me: Session, type: ProposalType, stop: ItineraryStop, toDay: Int?, reason: String) {
        val batch = backend.db.batch()
        val p = Proposal("", type, stop.key, stop.name, toDay, reason.trim().take(300), me.uid, me.name, ProposalStatus.PENDING, 0, null)
        batch.set(
            backend.proposals(trip.id).document(),
            mapOf(
                "type" to type.name, "stopKey" to stop.key, "stopName" to stop.name, "toDay" to toDay, "reason" to p.reason,
                "createdBy" to me.uid, "createdByName" to me.name, "status" to ProposalStatus.PENDING.name, "createdAt" to now(),
            ),
        )
        trips.addEvent(batch, trip.id, me, EventType.PROPOSAL_CREATED, "${me.name} proposed: ${p.summary}")
        backend.fire(batch.commit(), "send the proposal")
    }

    fun resolve(trip: Trip, me: Session, p: Proposal, approve: Boolean, itin: Itinerary?) {
        val batch = backend.db.batch()
        batch.update(
            backend.proposals(trip.id).document(p.id),
            mapOf("status" to (if (approve) ProposalStatus.APPROVED else ProposalStatus.REJECTED).name, "resolvedByName" to me.name, "resolvedAt" to now()),
        )
        trips.addEvent(
            batch, trip.id, me,
            if (approve) EventType.PROPOSAL_APPROVED else EventType.PROPOSAL_REJECTED,
            "${me.name} ${if (approve) "approved" else "declined"}: ${p.summary}",
        )
        backend.fire(batch.commit(), "resolve the proposal")
        if (approve && itin != null && itin.stop(p.stopKey) != null) {
            when (p.type) {
                ProposalType.REMOVE_STOP -> removeStop(trip, me, itin, p.stopKey, via = "${p.createdByName}'s proposal: ${p.summary}")
                ProposalType.MOVE_STOP -> p.toDay?.let { moveStop(trip, me, itin, p.stopKey, it, via = "${p.createdByName}'s proposal: ${p.summary}") }
            }
        }
    }

    private fun newKey(day: Int, index: Int) = "d${day}s${index}x" + UUID.randomUUID().toString().take(4)
}
