package com.trippin.ai.data.model

import com.trippin.intelligence.TripBrain
import com.trippin.intelligence.group.Diet
import com.trippin.intelligence.group.StartPreference
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Category
import java.time.LocalDate

/**
 * Domain models for a shared trip. They mirror the Firestore documents (see [FirestoreSchema])
 * but are plain Kotlin, so screens never touch Firebase types.
 *
 * The important distinction (from the product spec) is between a suggestion ([Idea]), a
 * preference ([VoteKind], [MemberPrefs]), a group decision ([Decision]) and the approved plan
 * ([Itinerary] once `locked`). They are separate records and are never collapsed into "likes".
 */

enum class Role(val label: String) {
    OWNER("Owner"), ADMIN("Admin"), MEMBER("Member"), VIEWER("Viewer");

    /** Owner and admins run the trip: close votes, generate and confirm the plan, manage people. */
    val canManage: Boolean get() = this == OWNER || this == ADMIN

    /** Everyone except viewers can suggest, vote, comment and set preferences. */
    val canContribute: Boolean get() = this != VIEWER
}

/** The backend-owned lifecycle of a trip. "Completed" is derived from the dates, not stored. */
enum class TripStatus(val label: String) {
    DECIDING_DESTINATION("Choosing where to go"),
    COLLECTING_IDEAS("Collecting ideas"),
    PLAN_READY("Plan ready for review"),
    CONFIRMED("Plan confirmed"),
}

data class Destination(val name: String, val country: String?, val lat: Double, val lng: Double) {
    val label: String get() = if (country.isNullOrBlank()) name else "$name, $country"
}

data class Trip(
    val id: String,
    val title: String,
    val ownerId: String,
    val memberIds: List<String>,
    val status: TripStatus,
    val isGroup: Boolean,
    val startDate: LocalDate,
    val days: Int,
    val pace: TripBrain.Pace,
    val destination: Destination?,
    val stay: Destination?,
    val inviteCode: String?,
    val inviteExpiresAt: Long?,
    val destinationVoteClosesAt: Long?,
    val createdAt: Long,
) {
    val endDate: LocalDate get() = startDate.plusDays((days - 1).toLong())
    fun isPast(today: LocalDate = LocalDate.now()): Boolean = endDate.isBefore(today)
    fun isOngoing(today: LocalDate = LocalDate.now()): Boolean = !today.isBefore(startDate) && !today.isAfter(endDate)
    fun inviteActive(now: Long = System.currentTimeMillis()): Boolean = inviteCode != null && (inviteExpiresAt ?: 0) > now
}

data class Member(val uid: String, val name: String, val role: Role, val joinedAt: Long)

enum class IdeaType { DESTINATION, PLACE }
enum class IdeaStatus { OPEN, SELECTED, DISMISSED }

data class Idea(
    val id: String,
    val type: IdeaType,
    val title: String,
    val subtitle: String?,
    val country: String?,
    val lat: Double?,
    val lng: Double?,
    val category: Category?,
    val tags: Set<String>,
    val createdBy: String,
    val createdByName: String,
    val createdAt: Long,
    /** Each member's own vote, kept individually (uid → vote). */
    val votes: Map<String, VoteKind>,
    val status: IdeaStatus,
    val commentCount: Int,
) {
    val ups: Int get() = votes.values.count { it != VoteKind.DOWN }
    val downs: Int get() = votes.values.count { it == VoteKind.DOWN }
    val musts: Int get() = votes.values.count { it == VoteKind.MUST }
    val score: Int get() = votes.values.sumOf { it.weight }
}

data class Comment(
    val id: String,
    val ideaId: String?,
    val uid: String,
    val name: String,
    val text: String,
    val createdAt: Long,
    val deleted: Boolean,
)

enum class EventType {
    TRIP_CREATED, MEMBER_JOINED, MEMBER_LEFT, MEMBER_REMOVED, INVITE_CREATED, INVITE_REVOKED,
    IDEA_CREATED, VOTE_CAST, COMMENT_CREATED, DESTINATION_SELECTED, VOTING_STARTED,
    PREFERENCES_UPDATED, ITINERARY_UPDATED, ITINERARY_CONFIRMED, PROPOSAL_CREATED,
    PROPOSAL_APPROVED, PROPOSAL_REJECTED, TRIP_UPDATED,
}

data class TripEvent(
    val id: String,
    val tripId: String,
    val type: EventType,
    val actorId: String,
    val actorName: String,
    val text: String,
    val createdAt: Long,
)

enum class DecisionType { DESTINATION_SELECTED, ITINERARY_CONFIRMED }

data class Decision(
    val id: String,
    val type: DecisionType,
    val refId: String?,
    val title: String,
    val decidedById: String,
    val decidedByName: String,
    val at: Long,
    /** Human summary of the evidence, e.g. "Goa 4 · Kerala 2 · Hampi 1". */
    val tally: String,
)

enum class Budget { LOW, MID, HIGH }

data class MemberPrefs(
    val uid: String,
    val name: String,
    val interests: Set<Category> = setOf(Category.FOOD, Category.LANDMARK),
    val diet: Diet = Diet.ANY,
    val noAlcohol: Boolean = false,
    val start: StartPreference = StartPreference.NORMAL,
    val maxWalkKm: Int? = null,
    val accessibility: Boolean = false,
    val budget: Budget = Budget.MID,
    val updatedAt: Long = 0,
)

data class ItineraryStop(
    /** Stable key like "d1-s0-a8f3", safe to use inside Firestore field paths. */
    val key: String,
    val name: String,
    val lat: Double,
    val lng: Double,
    val category: Category,
    val arrive: Int,
    val start: Int,
    val leave: Int,
    val travelMinutes: Int,
    val opensAt: Int,
    val closesAt: Int,
    val hoursEstimated: Boolean,
    val pDisrupted: Double,
    val pDelay: Double,
    val pClosed: Double,
    val pRainGivenDisrupted: Double,
    val risk: String,
    val reason: String,
    val optional: Boolean,
    val ideaId: String?,
)

data class ItineraryDay(
    val date: LocalDate,
    val rainProbability: Double,
    /** False when the date is beyond the 16-day forecast, so rain is a neutral guess. */
    val rainKnown: Boolean,
    val verified: Boolean,
    val km: Double,
    val evolutionBest: List<Double>,
    val evolutionAverage: List<Double>,
    val stops: List<ItineraryStop>,
)

data class AltPlace(val name: String, val lat: Double, val lng: Double, val category: Category)

data class Itinerary(
    val version: Int,
    val generatedAt: Long,
    val generatedById: String,
    val generatedByName: String,
    val locked: Boolean,
    /** Minute of the day the group sets off (from the members' start preferences). */
    val dayStart: Int,
    /** Where each day starts: the group's stay, or the city centre. */
    val origin: AltPlace,
    val days: List<ItineraryDay>,
    val alternates: List<AltPlace>,
    val notes: List<String>,
    val gaImprovementPercent: Double,
) {
    val totalKm: Double get() = days.sumOf { it.km }
    val verified: Boolean get() = days.all { it.verified }
    fun stop(key: String): ItineraryStop? = days.firstNotNullOfOrNull { d -> d.stops.firstOrNull { it.key == key } }
}

enum class ProposalType { REMOVE_STOP, MOVE_STOP }
enum class ProposalStatus { PENDING, APPROVED, REJECTED }

data class Proposal(
    val id: String,
    val type: ProposalType,
    val stopKey: String,
    val stopName: String,
    val toDay: Int?,
    val reason: String,
    val createdBy: String,
    val createdByName: String,
    val status: ProposalStatus,
    val createdAt: Long,
    val resolvedByName: String?,
) {
    val summary: String get() = when (type) {
        ProposalType.REMOVE_STOP -> "Remove $stopName"
        ProposalType.MOVE_STOP -> "Move $stopName to day ${(toDay ?: 0) + 1}"
    }
}

/** A place or city returned by search (Photon / OpenStreetMap). */
data class PlaceHit(
    val name: String,
    val detail: String?,
    val country: String?,
    val lat: Double,
    val lng: Double,
    val category: Category?,
    val tags: Set<String> = emptySet(),
)
