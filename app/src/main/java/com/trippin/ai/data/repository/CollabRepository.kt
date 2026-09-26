package com.trippin.ai.data.repository

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import com.trippin.ai.auth.Session
import com.trippin.ai.data.firebase.Backend
import com.trippin.ai.data.firebase.asFlow
import com.trippin.ai.data.firebase.now
import com.trippin.ai.data.firebase.toComment
import com.trippin.ai.data.firebase.toDecision
import com.trippin.ai.data.firebase.toIdea
import com.trippin.ai.data.firebase.toMap
import com.trippin.ai.data.firebase.toPrefs
import com.trippin.ai.data.model.Comment
import com.trippin.ai.data.model.Decision
import com.trippin.ai.data.model.DecisionType
import com.trippin.ai.data.model.Destination
import com.trippin.ai.data.model.EventType
import com.trippin.ai.data.model.Idea
import com.trippin.ai.data.model.IdeaStatus
import com.trippin.ai.data.model.IdeaType
import com.trippin.ai.data.model.MemberPrefs
import com.trippin.ai.data.model.PlaceHit
import com.trippin.ai.data.model.Trip
import com.trippin.ai.data.model.TripStatus
import com.trippin.intelligence.group.VoteKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.Date

/**
 * Suggestions, votes, comments, preferences and group decisions.
 *
 * Votes live inside each idea as a map `votes.{uid}` → MUST/UP/DOWN. One key per member makes a
 * duplicate vote impossible (the spec's UNIQUE(trip, idea, user)) without a transaction, keeps
 * every individual preference for the planner, and the security rules only let a member write
 * their own key.
 */
class CollabRepository(private val backend: Backend, private val trips: TripRepository) {

    fun ideas(tripId: String): Flow<List<Idea>> =
        backend.ideas(tripId).asFlow().map { d -> d.map { it.toIdea() }.sortedByDescending { it.createdAt } }

    fun comments(tripId: String): Flow<List<Comment>> =
        backend.comments(tripId).asFlow().map { d -> d.map { it.toComment() }.filterNot { it.deleted }.sortedBy { it.createdAt } }

    fun decisions(tripId: String): Flow<List<Decision>> =
        backend.decisions(tripId).asFlow().map { d -> d.mapNotNull { it.toDecision() }.sortedByDescending { it.at } }

    fun prefs(tripId: String): Flow<List<MemberPrefs>> = backend.prefs(tripId).asFlow().map { d -> d.map { it.toPrefs() } }

    /** Adds a suggestion, or — if the same place is already suggested — just up-votes that one. */
    fun suggest(trip: Trip, me: Session, type: IdeaType, hit: PlaceHit, existing: List<Idea>): String {
        val dup = existing.firstOrNull { it.type == type && it.status != IdeaStatus.DISMISSED && it.title.equals(hit.name, ignoreCase = true) }
        if (dup != null) {
            vote(trip, me, dup, VoteKind.UP)
            return dup.id
        }
        val ref = backend.ideas(trip.id).document()
        val batch = backend.db.batch()
        batch.set(
            ref,
            mapOf(
                "type" to type.name, "title" to hit.name,
                "subtitle" to listOfNotNull(hit.detail, hit.country).joinToString(" · ").ifBlank { null },
                "country" to hit.country,
                "lat" to hit.lat, "lng" to hit.lng, "category" to hit.category?.name, "tags" to hit.tags.toList(),
                "createdBy" to me.uid, "createdByName" to me.name, "createdAt" to now(),
                "votes" to mapOf(me.uid to VoteKind.UP.name), "status" to IdeaStatus.OPEN.name, "commentCount" to 0,
            ),
        )
        trips.addEvent(batch, trip.id, me, EventType.IDEA_CREATED, "${me.name} suggested ${hit.name}")
        backend.fire(batch.commit(), "add the suggestion")
        return ref.id
    }

    /** Sets (or clears, with null) the current user's vote. Tapping your current vote again clears it. */
    fun vote(trip: Trip, me: Session, idea: Idea, kind: VoteKind?) {
        val value: Any = kind?.name ?: FieldValue.delete()
        val batch = backend.db.batch()
        batch.update(backend.ideas(trip.id).document(idea.id), "votes.${me.uid}", value)
        if (kind != null) {
            val verb = when (kind) { VoteKind.MUST -> "marked ${idea.title} a must"; VoteKind.UP -> "voted for ${idea.title}"; VoteKind.DOWN -> "voted against ${idea.title}" }
            trips.addEvent(batch, trip.id, me, EventType.VOTE_CAST, "${me.name} $verb")
        }
        backend.fire(batch.commit(), "save your vote")
    }

    /** Demo helper: a simulated member's vote. */
    fun voteAs(tripId: String, uid: String, ideaId: String, kind: VoteKind) {
        backend.fire(backend.ideas(tripId).document(ideaId).update("votes.$uid", kind.name), "save a simulated vote")
    }

    fun dismiss(trip: Trip, idea: Idea) {
        backend.fire(backend.ideas(trip.id).document(idea.id).update("status", IdeaStatus.DISMISSED.name), "remove the suggestion")
    }

    fun restore(trip: Trip, idea: Idea) {
        backend.fire(backend.ideas(trip.id).document(idea.id).update("status", IdeaStatus.OPEN.name), "restore the suggestion")
    }

    fun comment(trip: Trip, me: Session, idea: Idea?, text: String) {
        val clean = text.trim().take(MAX_COMMENT)
        if (clean.isEmpty()) return
        val batch = backend.db.batch()
        batch.set(
            backend.comments(trip.id).document(),
            mapOf("ideaId" to idea?.id, "uid" to me.uid, "name" to me.name, "text" to clean, "createdAt" to now(), "deleted" to false),
        )
        if (idea != null) batch.update(backend.ideas(trip.id).document(idea.id), "commentCount", FieldValue.increment(1))
        trips.addEvent(batch, trip.id, me, EventType.COMMENT_CREATED, "${me.name} commented${idea?.let { " on ${it.title}" } ?: ""}: “${clean.take(60)}”")
        backend.fire(batch.commit(), "post the comment")
    }

    /** Authors delete their own comments; owners/admins can remove any (moderation). */
    fun deleteComment(trip: Trip, comment: Comment) {
        val batch = backend.db.batch()
        batch.update(backend.comments(trip.id).document(comment.id), mapOf("deleted" to true, "text" to ""))
        comment.ideaId?.let { batch.update(backend.ideas(trip.id).document(it), "commentCount", FieldValue.increment(-1)) }
        backend.fire(batch.commit(), "delete the comment")
    }

    fun savePrefs(trip: Trip, me: Session, p: MemberPrefs) {
        val batch = backend.db.batch()
        batch.set(backend.prefs(trip.id).document(me.uid), p.copy(uid = me.uid, name = me.name).toMap(), SetOptions.merge())
        trips.addEvent(batch, trip.id, me, EventType.PREFERENCES_UPDATED, "${me.name} updated their preferences")
        backend.fire(batch.commit(), "save your preferences")
    }

    fun savePrefsAs(tripId: String, p: MemberPrefs) {
        backend.fire(backend.prefs(tripId).document(p.uid).set(p.toMap()), "save simulated preferences")
    }

    /** Opens the destination vote with an optional deadline shown to everyone. */
    fun openDestinationVote(trip: Trip, me: Session, closesAt: Long?) {
        val batch = backend.db.batch()
        batch.update(backend.trip(trip.id), mapOf("destinationVoteClosesAt" to closesAt?.let { Timestamp(Date(it)) }, "status" to TripStatus.DECIDING_DESTINATION.name))
        trips.addEvent(batch, trip.id, me, EventType.VOTING_STARTED, "${me.name} opened the destination vote")
        backend.fire(batch.commit(), "open the vote")
    }

    /**
     * Records the group's destination as a decision (with the tally as evidence), moves the trip
     * on to collecting ideas, and keeps every vote for the record.
     */
    fun decideDestination(trip: Trip, me: Session, winner: Idea, all: List<Idea>) {
        val lat = winner.lat ?: return
        val lng = winner.lng ?: return
        val destination = Destination(winner.title, winner.country, lat, lng)
        val tally = all.filter { it.type == IdeaType.DESTINATION && it.status != IdeaStatus.DISMISSED }
            .sortedByDescending { it.score }.joinToString(" · ") { "${it.title} ${it.ups}👍${if (it.downs > 0) " ${it.downs}👎" else ""}" }
        val batch = backend.db.batch()
        batch.set(
            backend.decisions(trip.id).document(),
            mapOf(
                "type" to DecisionType.DESTINATION_SELECTED.name, "refId" to winner.id, "title" to destination.label,
                "decidedById" to me.uid, "decidedByName" to me.name, "at" to now(), "tally" to tally,
            ),
        )
        batch.update(
            backend.trip(trip.id),
            mapOf("destination" to destination.toMap(), "status" to TripStatus.COLLECTING_IDEAS.name, "destinationVoteClosesAt" to null, "updatedAt" to now()),
        )
        batch.update(backend.ideas(trip.id).document(winner.id), "status", IdeaStatus.SELECTED.name)
        trips.addEvent(batch, trip.id, me, EventType.DESTINATION_SELECTED, "The group is going to ${destination.name}")
        backend.fire(batch.commit(), "record the decision")
    }

    /** Owner changes the destination directly (e.g. a solo trip, or re-opening the choice). */
    fun setDestination(trip: Trip, me: Session, d: Destination?) {
        val batch = backend.db.batch()
        if (d == null) {
            batch.update(backend.trip(trip.id), mapOf("destination" to null, "status" to TripStatus.DECIDING_DESTINATION.name, "updatedAt" to now()))
            trips.addEvent(batch, trip.id, me, EventType.VOTING_STARTED, "${me.name} re-opened where to go")
        } else {
            batch.update(backend.trip(trip.id), mapOf("destination" to d.toMap(), "status" to TripStatus.COLLECTING_IDEAS.name, "updatedAt" to now()))
            batch.set(
                backend.decisions(trip.id).document(),
                mapOf("type" to DecisionType.DESTINATION_SELECTED.name, "refId" to null, "title" to d.label, "decidedById" to me.uid, "decidedByName" to me.name, "at" to now(), "tally" to "Set by ${me.name}"),
            )
            trips.addEvent(batch, trip.id, me, EventType.DESTINATION_SELECTED, "${me.name} set the destination to ${d.name}")
        }
        backend.fire(batch.commit(), "change the destination")
    }

    companion object {
        const val MAX_COMMENT = 500
    }
}
