package com.trippin.ai.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.WriteBatch
import com.trippin.ai.auth.Session
import com.trippin.ai.data.firebase.Backend
import com.trippin.ai.data.firebase.asFlow
import com.trippin.ai.data.firebase.now
import com.trippin.ai.data.firebase.toEvent
import com.trippin.ai.data.firebase.toMap
import com.trippin.ai.data.firebase.toMember
import com.trippin.ai.data.firebase.toTrip
import com.trippin.ai.data.model.DecisionType
import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.EventType
import com.trippin.ai.data.model.Member
import com.trippin.ai.data.model.Role
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripEvent
import com.trippin.ai.data.model.TripStatus
import com.trippin.intelligence.TripBrain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.security.SecureRandom
import java.time.LocalDate
import java.util.Date
import java.util.concurrent.TimeUnit

data class NewTrip(
    val title: String,
    val isGroup: Boolean,
    val startDate: LocalDate,
    val days: Int,
    val pace: TripBrain.Pace,
    /** Null for a group that will vote on where to go. */
    val destination: Destination?,
)

/** Trips, membership, invitations and the activity feed. */
class TripRepository(private val backend: Backend, private val prefs: PreferencesRepository) {

    fun myTrips(uid: String): Flow<List<Trip>> =
        backend.trips().whereArrayContains("memberIds", uid).asFlow { backend.report("Couldn't load your trips.") }
            .map { docs -> docs.mapNotNull { it.toTrip() }.sortedWith(compareBy<Trip> { it.isPast() }.thenBy { if (it.isPast()) -it.startDate.toEpochDay() else it.startDate.toEpochDay() }) }

    fun trip(id: String): Flow<Trip?> = backend.trip(id).asFlow(skipCacheMisses = !backend.isDemo).map { it?.toTrip() }

    fun members(tripId: String): Flow<List<Member>> =
        backend.members(tripId).asFlow().map { d -> d.map { it.toMember() }.sortedWith(compareBy<Member> { it.role.ordinal }.thenBy { it.joinedAt }) }

    fun events(tripId: String, limit: Long = 60): Flow<List<TripEvent>> =
        backend.events(tripId).orderBy("createdAt", Query.Direction.DESCENDING).limit(limit).asFlow()
            .map { d -> d.mapNotNull { it.toEvent(tripId) } }

    /** Creates the trip, the owner's membership, their preferences and the first activity entry in one batch. */
    suspend fun create(input: NewTrip, me: Session): String {
        val ref = backend.trips().document()
        val batch = backend.db.batch()
        val status = if (input.destination == null) TripStatus.DECIDING_DESTINATION else TripStatus.COLLECTING_IDEAS
        batch.set(
            ref,
            mapOf(
                "title" to input.title.trim(),
                "ownerId" to me.uid,
                "memberIds" to listOf(me.uid),
                "status" to status.name,
                "isGroup" to input.isGroup,
                "startDate" to input.startDate.toString(),
                "days" to input.days,
                "pace" to input.pace.name,
                "destination" to input.destination?.toMap(),
                "stay" to null,
                "createdAt" to now(),
                "updatedAt" to now(),
            ),
        )
        batch.set(backend.members(ref.id).document(me.uid), memberDoc(me, Role.OWNER, null))
        batch.set(backend.prefs(ref.id).document(me.uid), prefs.defaultPrefs(me.uid, me.name).toMap())
        addEvent(batch, ref.id, me, EventType.TRIP_CREATED, "${me.name} created ${input.title.trim()}")
        if (input.destination != null) {
            batch.set(
                backend.decisions(ref.id).document(),
                mapOf(
                    "type" to DecisionType.DESTINATION_SELECTED.name, "refId" to null, "title" to input.destination.label,
                    "decidedById" to me.uid, "decidedByName" to me.name, "at" to now(), "tally" to "Chosen when the trip was created",
                ),
            )
        }
        backend.fire(batch.commit(), "create the trip")
        return ref.id
    }

    fun updateDetails(trip: Trip, me: Session, title: String, start: LocalDate, days: Int, pace: TripBrain.Pace) {
        val batch = backend.db.batch()
        batch.update(backend.trip(trip.id), mapOf("title" to title.trim(), "startDate" to start.toString(), "days" to days, "pace" to pace.name, "updatedAt" to now()))
        addEvent(batch, trip.id, me, EventType.TRIP_UPDATED, "${me.name} changed the trip details")
        backend.fire(batch.commit(), "save the trip details")
    }

    fun setStay(trip: Trip, me: Session, stay: Destination?) {
        val batch = backend.db.batch()
        batch.update(backend.trip(trip.id), mapOf("stay" to stay?.toMap(), "updatedAt" to now()))
        addEvent(batch, trip.id, me, EventType.TRIP_UPDATED, if (stay == null) "${me.name} cleared where you're staying" else "${me.name} set your stay to ${stay.name}")
        backend.fire(batch.commit(), "save where you're staying")
    }

    /** Owner only. Firestore has no cascading delete, so children are removed explicitly. */
    suspend fun delete(tripId: String) {
        val source = if (backend.isDemo) Source.CACHE else Source.DEFAULT
        val refs = backend.tripChildren(tripId).flatMap { c -> runCatching { c.get(source).await().documents.map { it.reference } }.getOrDefault(emptyList()) }
        refs.chunked(400).forEach { chunk ->
            val b = backend.db.batch()
            chunk.forEach { b.delete(it) }
            backend.fire(b.commit(), "delete the trip")
        }
        val trip = runCatching { backend.trip(tripId).get(source).await().toTrip() }.getOrNull()
        val b = backend.db.batch()
        trip?.inviteCode?.let { b.delete(backend.invite(it)) }
        b.delete(backend.trip(tripId))
        backend.fire(b.commit(), "delete the trip")
    }

    fun leave(trip: Trip, me: Session) {
        val batch = backend.db.batch()
        addEvent(batch, trip.id, me, EventType.MEMBER_LEFT, "${me.name} left the trip")
        batch.delete(backend.prefs(trip.id).document(me.uid))
        batch.delete(backend.members(trip.id).document(me.uid))
        batch.update(backend.trip(trip.id), "memberIds", FieldValue.arrayRemove(me.uid))
        backend.fire(batch.commit(), "leave the trip")
    }

    fun removeMember(trip: Trip, member: Member, me: Session) {
        val batch = backend.db.batch()
        batch.delete(backend.members(trip.id).document(member.uid))
        batch.delete(backend.prefs(trip.id).document(member.uid))
        batch.update(backend.trip(trip.id), "memberIds", FieldValue.arrayRemove(member.uid))
        addEvent(batch, trip.id, me, EventType.MEMBER_REMOVED, "${me.name} removed ${member.name}")
        backend.fire(batch.commit(), "remove ${member.name}")
    }

    fun setRole(trip: Trip, member: Member, role: Role, me: Session) {
        require(role != Role.OWNER) { "Ownership can't be reassigned here" }
        val batch = backend.db.batch()
        batch.update(backend.members(trip.id).document(member.uid), "role", role.name)
        addEvent(batch, trip.id, me, EventType.TRIP_UPDATED, "${me.name} made ${member.name} ${role.label.lowercase()}")
        backend.fire(batch.commit(), "change ${member.name}'s role")
    }

    /**
     * One active invite link per trip: random, expires in 7 days, revocable. The invite document
     * lets someone who isn't a member yet find the trip — the trip itself stays private.
     */
    fun createInvite(trip: Trip, me: Session): String {
        val code = newCode()
        val expires = Timestamp(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(INVITE_DAYS)))
        val batch = backend.db.batch()
        trip.inviteCode?.let { batch.delete(backend.invite(it)) }
        batch.set(
            backend.invite(code),
            mapOf("tripId" to trip.id, "tripTitle" to trip.title, "role" to Role.MEMBER.name, "createdBy" to me.uid, "createdAt" to now(), "expiresAt" to expires, "revoked" to false),
        )
        batch.update(backend.trip(trip.id), mapOf("inviteCode" to code, "inviteExpiresAt" to expires, "isGroup" to true))
        addEvent(batch, trip.id, me, EventType.INVITE_CREATED, "${me.name} created an invite link")
        backend.fire(batch.commit(), "create the invite")
        return code
    }

    fun revokeInvite(trip: Trip, me: Session) {
        val code = trip.inviteCode ?: return
        val batch = backend.db.batch()
        batch.update(backend.invite(code), "revoked", true)
        batch.update(backend.trip(trip.id), mapOf("inviteCode" to null, "inviteExpiresAt" to null))
        addEvent(batch, trip.id, me, EventType.INVITE_REVOKED, "${me.name} turned off the invite link")
        backend.fire(batch.commit(), "turn off the invite link")
    }

    sealed interface JoinResult {
        data class Joined(val tripId: String, val title: String) : JoinResult
        data class AlreadyMember(val tripId: String) : JoinResult
        data class Failed(val message: String) : JoinResult
    }

    /** Needs the server: an invite has to be checked against the real, shared data. */
    suspend fun join(rawCode: String, me: Session): JoinResult {
        val code = normaliseCode(rawCode) ?: return JoinResult.Failed("That code doesn't look right. It's 8 letters and numbers.")
        if (backend.isDemo) return JoinResult.Failed("Joining a friend's trip needs Firebase connected. This build runs on this phone only.")
        val invite = try {
            withTimeout(15_000) { backend.invite(code).get(Source.SERVER).await() }
        } catch (e: Exception) {
            return JoinResult.Failed("Couldn't check the invite. Are you online?")
        }
        if (!invite.exists()) return JoinResult.Failed("That invite doesn't exist. Ask for a new link.")
        val tripId = invite.getString("tripId") ?: return JoinResult.Failed("That invite is broken. Ask for a new link.")
        if (invite.getBoolean("revoked") == true) return JoinResult.Failed("That invite was turned off. Ask for a new link.")
        val expires = invite.getTimestamp("expiresAt")?.toDate()?.time ?: 0
        if (expires < System.currentTimeMillis()) return JoinResult.Failed("That invite has expired. Ask for a new link.")
        val existing = runCatching { withTimeout(10_000) { backend.members(tripId).document(me.uid).get(Source.SERVER).await() } }.getOrNull()
        if (existing?.exists() == true) return JoinResult.AlreadyMember(tripId)

        val role = runCatching { Role.valueOf(invite.getString("role") ?: "MEMBER") }.getOrDefault(Role.MEMBER).takeIf { it != Role.OWNER } ?: Role.MEMBER
        val batch = backend.db.batch()
        batch.set(backend.members(tripId).document(me.uid), memberDoc(me, role, code))
        batch.update(backend.trip(tripId), "memberIds", FieldValue.arrayUnion(me.uid))
        batch.set(backend.prefs(tripId).document(me.uid), prefs.defaultPrefs(me.uid, me.name).toMap())
        addEvent(batch, tripId, me, EventType.MEMBER_JOINED, "${me.name} joined the trip")
        return try {
            withTimeout(15_000) { batch.commit().await() }
            JoinResult.Joined(tripId, invite.getString("tripTitle") ?: "the trip")
        } catch (e: Exception) {
            JoinResult.Failed("Couldn't join: ${e.message?.take(80) ?: "unknown error"}")
        }
    }

    /** Demo mode only: adds simulated friends with their own votes and preferences, for testing a group on one phone. */
    fun addSimulatedMember(tripId: String, name: String): String {
        val uid = "sim-" + newCode().lowercase()
        val fake = Session(uid, name, null, true, true)
        val batch = backend.db.batch()
        batch.set(backend.members(tripId).document(uid), memberDoc(fake, Role.MEMBER, "SIMULATED"))
        batch.update(backend.trip(tripId), "memberIds", FieldValue.arrayUnion(uid))
        addEvent(batch, tripId, fake, EventType.MEMBER_JOINED, "$name joined the trip (simulated)")
        backend.fire(batch.commit(), "add $name")
        return uid
    }

    fun addEvent(batch: WriteBatch, tripId: String, actor: Session, type: EventType, text: String) {
        batch.set(
            backend.events(tripId).document(),
            mapOf("type" to type.name, "actorId" to actor.uid, "actorName" to actor.name, "text" to text, "createdAt" to now()),
        )
    }

    fun updateMemberName(tripIds: List<String>, me: Session) {
        if (tripIds.isEmpty()) return
        val batch = backend.db.batch()
        tripIds.forEach { id ->
            batch.set(backend.members(id).document(me.uid), mapOf("name" to me.name), SetOptions.merge())
            batch.set(backend.prefs(id).document(me.uid), mapOf("name" to me.name), SetOptions.merge())
        }
        backend.fire(batch.commit(), "update your name")
    }

    private fun memberDoc(me: Session, role: Role, inviteCode: String?) = mapOf(
        "name" to me.name, "role" to role.name, "status" to "ACTIVE", "joinedAt" to now(), "inviteCode" to inviteCode,
    )

    companion object {
        const val INVITE_DAYS = 7L
        private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O or 1/I
        private val random = SecureRandom()

        fun newCode(): String = (1..8).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")

        /** Accepts "abcd2345", "ABCD-2345", or a full link like trippin://join/ABCD2345. */
        fun normaliseCode(raw: String): String? {
            val c = raw.trim().substringAfterLast('/').uppercase().filter { it.isLetterOrDigit() }
            return c.takeIf { it.length == 8 && it.all { ch -> ch in ALPHABET } }
        }

        fun inviteLink(code: String) = "trippin://join/$code"
    }
}
