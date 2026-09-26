package com.trippin.ai.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.Query
import com.trippin.ai.data.model.AltPlace
import com.trippin.ai.data.model.Budget
import com.trippin.ai.data.model.Comment
import com.trippin.ai.data.model.Decision
import com.trippin.ai.data.model.DecisionType
import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.EventType
import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaStatus
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.Itinerary
import com.trippin.ai.data.model.ItineraryDay
import com.trippin.ai.data.model.ItineraryStop
import com.trippin.ai.data.model.Member
import com.trippin.ai.data.model.MemberPrefs
import com.trippin.ai.data.model.Proposal
import com.trippin.ai.data.model.ProposalStatus
import com.trippin.ai.data.model.ProposalType
import com.trippin.ai.data.model.Role
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripEvent
import com.trippin.ai.data.model.TripStatus
import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.group.Diet
import com.trippin.intelligence.group.StartPreference
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Category
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate

/*
 * Firestore ⇄ domain mapping, written by hand (no reflection) so R8 can't break it and so
 * unknown or missing fields degrade gracefully instead of crashing old clients.
 */

// ---------- Flow helpers ----------

fun Query.asFlow(onError: (Exception) -> Unit = {}): Flow<List<DocumentSnapshot>> = callbackFlow {
    val reg = addSnapshotListener { snap, err ->
        if (err != null) { onError(err); return@addSnapshotListener }
        if (snap != null) trySend(snap.documents)
    }
    awaitClose { reg.remove() }
}

/**
 * @param skipCacheMisses when true, a "doesn't exist" answer that only comes from the local
 *   cache is ignored until the server confirms it (e.g. a trip you just joined).
 */
fun DocumentReference.asFlow(skipCacheMisses: Boolean = false, onError: (Exception) -> Unit = {}): Flow<DocumentSnapshot?> = callbackFlow {
    val reg = addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snap, err ->
        if (err != null) { onError(err); trySend(null); return@addSnapshotListener }
        if (snap != null && !snap.exists() && skipCacheMisses && snap.metadata.isFromCache) return@addSnapshotListener
        trySend(snap?.takeIf { it.exists() })
    }
    awaitClose { reg.remove() }
}

// ---------- Primitive readers ----------

private fun DocumentSnapshot.millis(field: String): Long =
    getTimestamp(field, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.toDate()?.time
        ?: (get(field) as? Number)?.toLong() ?: 0L

private fun DocumentSnapshot.str(field: String): String? = getString(field)
private fun DocumentSnapshot.int(field: String, default: Int = 0): Int = (get(field) as? Number)?.toInt() ?: default
private fun DocumentSnapshot.dbl(field: String): Double? = (get(field) as? Number)?.toDouble()
private fun DocumentSnapshot.bool(field: String): Boolean = getBoolean(field) ?: false

private inline fun <reified E : Enum<E>> enumOr(value: Any?, default: E): E =
    (value as? String)?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

private inline fun <reified E : Enum<E>> enumOrNull(value: Any?): E? =
    (value as? String)?.let { v -> enumValues<E>().firstOrNull { it.name == v } }

private fun Map<*, *>.d(k: String): Double = (this[k] as? Number)?.toDouble() ?: 0.0
private fun Map<*, *>.i(k: String): Int = (this[k] as? Number)?.toInt() ?: 0
private fun Map<*, *>.s(k: String): String? = this[k] as? String
private fun Map<*, *>.b(k: String): Boolean = this[k] as? Boolean ?: false

private fun votesOf(raw: Any?): Map<String, VoteKind> =
    (raw as? Map<*, *>)?.mapNotNull { (k, v) -> (k as? String)?.let { key -> enumOrNull<VoteKind>(v)?.let { key to it } } }?.toMap() ?: emptyMap()

fun now(): Timestamp = Timestamp.now()

// ---------- Trip ----------

fun Destination.toMap() = mapOf("name" to name, "country" to country, "lat" to lat, "lng" to lng)

private fun destinationOf(raw: Any?): Destination? = (raw as? Map<*, *>)?.let {
    val name = it.s("name") ?: return null
    Destination(name, it.s("country"), it.d("lat"), it.d("lng"))
}

fun DocumentSnapshot.toTrip(): Trip? {
    val start = str("startDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    @Suppress("UNCHECKED_CAST")
    return Trip(
        id = id,
        title = str("title") ?: "Trip",
        ownerId = str("ownerId") ?: "",
        memberIds = (get("memberIds") as? List<String>).orEmpty(),
        status = enumOr(get("status"), TripStatus.COLLECTING_IDEAS),
        isGroup = bool("isGroup"),
        startDate = start,
        days = int("days", 1).coerceIn(1, 14),
        pace = enumOr(get("pace"), TripBrain.Pace.STANDARD),
        destination = destinationOf(get("destination")),
        stay = destinationOf(get("stay")),
        inviteCode = str("inviteCode"),
        inviteExpiresAt = (get("inviteExpiresAt") as? Timestamp)?.toDate()?.time,
        destinationVoteClosesAt = (get("destinationVoteClosesAt") as? Timestamp)?.toDate()?.time,
        createdAt = millis("createdAt"),
    )
}

fun DocumentSnapshot.toMember(): Member = Member(
    uid = id,
    name = str("name") ?: "Traveller",
    role = enumOr(get("role"), Role.MEMBER),
    joinedAt = millis("joinedAt"),
)

// ---------- Ideas, comments, events, decisions ----------

fun DocumentSnapshot.toIdea(): Idea = Idea(
    id = id,
    type = enumOr(get("type"), IdeaType.PLACE),
    title = str("title") ?: "Idea",
    subtitle = str("subtitle"),
    country = str("country"),
    lat = dbl("lat"),
    lng = dbl("lng"),
    category = enumOrNull<Category>(get("category")),
    tags = (get("tags") as? List<*>)?.filterIsInstance<String>()?.toSet().orEmpty(),
    createdBy = str("createdBy") ?: "",
    createdByName = str("createdByName") ?: "Someone",
    createdAt = millis("createdAt"),
    votes = votesOf(get("votes")),
    status = enumOr(get("status"), IdeaStatus.OPEN),
    commentCount = int("commentCount"),
)

fun DocumentSnapshot.toComment(): Comment = Comment(
    id = id,
    ideaId = str("ideaId"),
    uid = str("uid") ?: "",
    name = str("name") ?: "Someone",
    text = str("text") ?: "",
    createdAt = millis("createdAt"),
    deleted = bool("deleted"),
)

fun DocumentSnapshot.toEvent(tripId: String): TripEvent? {
    val type = enumOrNull<EventType>(get("type")) ?: return null
    return TripEvent(id, tripId, type, str("actorId") ?: "", str("actorName") ?: "Someone", str("text") ?: "", millis("createdAt"))
}

fun DocumentSnapshot.toDecision(): Decision? {
    val type = enumOrNull<DecisionType>(get("type")) ?: return null
    return Decision(id, type, str("refId"), str("title") ?: "", str("decidedById") ?: "", str("decidedByName") ?: "", millis("at"), str("tally") ?: "")
}

// ---------- Preferences ----------

fun MemberPrefs.toMap(): Map<String, Any?> = mapOf(
    "uid" to uid,
    "name" to name,
    "interests" to interests.map { it.name },
    "diet" to diet.name,
    "noAlcohol" to noAlcohol,
    "start" to start.name,
    "maxWalkKm" to maxWalkKm,
    "accessibility" to accessibility,
    "budget" to budget.name,
    "updatedAt" to now(),
)

fun DocumentSnapshot.toPrefs(): MemberPrefs = MemberPrefs(
    uid = id,
    name = str("name") ?: "Traveller",
    interests = (get("interests") as? List<*>)?.mapNotNull { enumOrNull<Category>(it) }?.toSet().orEmpty(),
    diet = enumOr(get("diet"), Diet.ANY),
    noAlcohol = bool("noAlcohol"),
    start = enumOr(get("start"), StartPreference.NORMAL),
    maxWalkKm = (get("maxWalkKm") as? Number)?.toInt(),
    accessibility = bool("accessibility"),
    budget = enumOr(get("budget"), Budget.MID),
    updatedAt = millis("updatedAt"),
)

// ---------- Itinerary ----------

fun ItineraryStop.toMap(): Map<String, Any?> = mapOf(
    "key" to key, "name" to name, "lat" to lat, "lng" to lng, "category" to category.name,
    "arrive" to arrive, "start" to start, "leave" to leave, "travel" to travelMinutes,
    "opensAt" to opensAt, "closesAt" to closesAt,
    "hoursEstimated" to hoursEstimated, "pDisrupted" to pDisrupted, "pDelay" to pDelay,
    "pClosed" to pClosed, "pRainGivenDisrupted" to pRainGivenDisrupted, "risk" to risk,
    "reason" to reason, "optional" to optional, "ideaId" to ideaId,
)

private fun stopOf(m: Map<*, *>): ItineraryStop = ItineraryStop(
    key = m.s("key") ?: "",
    name = m.s("name") ?: "Stop",
    lat = m.d("lat"), lng = m.d("lng"),
    category = enumOr(m["category"], Category.LANDMARK),
    arrive = m.i("arrive"), start = m.i("start"), leave = m.i("leave"), travelMinutes = m.i("travel"),
    opensAt = m.i("opensAt"), closesAt = (m["closesAt"] as? Number)?.toInt() ?: (24 * 60),
    hoursEstimated = m.b("hoursEstimated"),
    pDisrupted = m.d("pDisrupted"), pDelay = m.d("pDelay"), pClosed = m.d("pClosed"),
    pRainGivenDisrupted = m.d("pRainGivenDisrupted"),
    risk = m.s("risk") ?: "Low", reason = m.s("reason") ?: "", optional = m.b("optional"), ideaId = m.s("ideaId"),
)

fun ItineraryDay.toMap(): Map<String, Any?> = mapOf(
    "date" to date.toString(), "rain" to rainProbability, "rainKnown" to rainKnown, "verified" to verified,
    "km" to km, "best" to evolutionBest, "avg" to evolutionAverage, "stops" to stops.map { it.toMap() },
)

private fun dayOf(m: Map<*, *>): ItineraryDay? {
    val date = m.s("date")?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
    return ItineraryDay(
        date = date, rainProbability = m.d("rain"), rainKnown = m.b("rainKnown"), verified = m.b("verified"), km = m.d("km"),
        evolutionBest = (m["best"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }.orEmpty(),
        evolutionAverage = (m["avg"] as? List<*>)?.mapNotNull { (it as? Number)?.toDouble() }.orEmpty(),
        stops = (m["stops"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.map(::stopOf).orEmpty(),
    )
}

fun Itinerary.toMap(): Map<String, Any?> = mapOf(
    "version" to version,
    "generatedAt" to now(),
    "generatedById" to generatedById,
    "generatedByName" to generatedByName,
    "locked" to locked,
    "dayStart" to dayStart,
    "origin" to mapOf("name" to origin.name, "lat" to origin.lat, "lng" to origin.lng),
    "days" to days.map { it.toMap() },
    "alternates" to alternates.map { mapOf("name" to it.name, "lat" to it.lat, "lng" to it.lng, "category" to it.category.name) },
    "notes" to notes,
    "gaImprovement" to gaImprovementPercent,
)

fun DocumentSnapshot.toItinerary(): Itinerary = Itinerary(
    version = int("version"),
    generatedAt = millis("generatedAt"),
    generatedById = str("generatedById") ?: "",
    generatedByName = str("generatedByName") ?: "",
    locked = bool("locked"),
    dayStart = int("dayStart", 9 * 60 + 30),
    origin = (get("origin") as? Map<*, *>)?.let { AltPlace(it.s("name") ?: "Start", it.d("lat"), it.d("lng"), Category.LANDMARK) }
        ?: AltPlace("Start", 0.0, 0.0, Category.LANDMARK),
    days = (get("days") as? List<*>)?.filterIsInstance<Map<*, *>>()?.mapNotNull(::dayOf).orEmpty(),
    alternates = (get("alternates") as? List<*>)?.filterIsInstance<Map<*, *>>()?.map {
        AltPlace(it.s("name") ?: "", it.d("lat"), it.d("lng"), enumOr(it["category"], Category.LANDMARK))
    }.orEmpty(),
    notes = (get("notes") as? List<*>)?.filterIsInstance<String>().orEmpty(),
    gaImprovementPercent = dbl("gaImprovement") ?: 0.0,
)

/** Folds every member's reactions doc (uid → {stopKey → vote}) into stopKey → (uid → vote). */
fun reactionsOf(docs: List<DocumentSnapshot>): Map<String, Map<String, VoteKind>> {
    val out = HashMap<String, HashMap<String, VoteKind>>()
    docs.forEach { d -> votesOf(d.get("votes")).forEach { (stopKey, v) -> out.getOrPut(stopKey) { HashMap() }[d.id] = v } }
    return out
}

fun DocumentSnapshot.toProposal(): Proposal = Proposal(
    id = id,
    type = enumOr(get("type"), ProposalType.REMOVE_STOP),
    stopKey = str("stopKey") ?: "",
    stopName = str("stopName") ?: "a stop",
    toDay = (get("toDay") as? Number)?.toInt(),
    reason = str("reason") ?: "",
    createdBy = str("createdBy") ?: "",
    createdByName = str("createdByName") ?: "Someone",
    status = enumOr(get("status"), ProposalStatus.PENDING),
    createdAt = millis("createdAt"),
    resolvedByName = str("resolvedByName"),
)
