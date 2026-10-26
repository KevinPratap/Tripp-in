package com.trippin.intelligence.group

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How much a group agrees on one idea, worked out from each member's own vote.
 * Pure Kotlin so the Swipe Deck's rules are unit-tested without Android or Firebase.
 *
 * Scoring reuses [VoteKind.weight] (MUST 3, UP 1, DOWN -2), the same numbers the planner uses,
 * so what the deck shows and what the plan is built from can never disagree.
 */
enum class AgreementLevel {
    /** Nobody has voted yet. */
    UNVOTED,
    /** Everyone who is in agrees, and at least half of the voters called it a must. */
    LOVED,
    /** More people are for it than against it. */
    LIKED,
    /** The group is genuinely divided: a candidate for a tiebreak round. */
    SPLIT,
    /** More people are against it than for it. */
    DISLIKED,
}

data class Consensus(
    val ideaId: String,
    val score: Int,
    val voters: Int,
    val musts: Int,
    val ups: Int,
    val downs: Int,
    /** Share of voters who did not vote DOWN, 0.0 to 1.0. Zero when nobody has voted. */
    val agreement: Double,
    val level: AgreementLevel,
) {
    /** Members who are for it, counting MUST as for. */
    val positives: Int get() = musts + ups
}

object GroupConsensus {

    /**
     * @param votes each member's own vote on this idea
     * @param memberCount everyone who can vote on the trip, including people who haven't yet
     */
    fun of(ideaId: String, votes: Map<String, VoteKind>, memberCount: Int): Consensus {
        val musts = votes.values.count { it == VoteKind.MUST }
        val ups = votes.values.count { it == VoteKind.UP }
        val downs = votes.values.count { it == VoteKind.DOWN }
        val voters = votes.size
        val positives = musts + ups
        val agreement = if (voters == 0) 0.0 else (voters - downs).toDouble() / voters

        // On small groups any real disagreement with equal sides is a split. From five voters up,
        // a one-vote gap (3 against 2) is still too close to call.
        val splitMargin = if (voters >= 5) 1 else 0
        val level = when {
            voters == 0 -> AgreementLevel.UNVOTED
            downs > 0 && positives > 0 && abs(positives - downs) <= splitMargin -> AgreementLevel.SPLIT
            downs > positives -> AgreementLevel.DISLIKED
            downs == 0 && voters >= memberCount && musts * 2 >= voters -> AgreementLevel.LOVED
            else -> AgreementLevel.LIKED
        }
        return Consensus(ideaId, votes.values.sumOf { it.weight }, voters, musts, ups, downs, agreement, level)
    }

    /** Best first: score, then musts, then agreement, then turnout. The id breaks ties so order is stable. */
    fun rank(items: List<Consensus>): List<Consensus> =
        items.sortedWith(
            compareByDescending<Consensus> { it.score }
                .thenByDescending { it.musts }
                .thenByDescending { it.agreement }
                .thenByDescending { it.voters }
                .thenBy { it.ideaId },
        )

    /** The ideas worth a second look: the group is divided on them. */
    fun splitRound(items: List<Consensus>): List<Consensus> = rank(items.filter { it.level == AgreementLevel.SPLIT })

    /** Ideas the group is for, best first, capped at [limit]. */
    fun winners(items: List<Consensus>, limit: Int = Int.MAX_VALUE): List<Consensus> =
        rank(items.filter { it.level == AgreementLevel.LOVED || it.level == AgreementLevel.LIKED }).take(limit)

    /**
     * Order for one member's deck: only what they haven't voted on, with what the group already
     * likes first. Cards nobody has voted on come after, in id order, so the deck is stable.
     */
    fun deckOrder(items: List<Consensus>, alreadyVotedByMe: Set<String>): List<String> {
        val open = items.filter { it.ideaId !in alreadyVotedByMe }
        val (seen, fresh) = open.partition { it.voters > 0 }
        return rank(seen).map { it.ideaId } + fresh.sortedBy { it.ideaId }.map { it.ideaId }
    }

    /**
     * Fraction of [ideaIds] each member has voted on, 0.0 to 1.0, so the deck can show who is done.
     * [votesByIdea] maps an idea id to that idea's votes (member id to vote).
     */
    fun memberProgress(memberIds: List<String>, ideaIds: List<String>, votesByIdea: Map<String, Map<String, VoteKind>>): Map<String, Double> {
        if (ideaIds.isEmpty()) return memberIds.associateWith { 1.0 }
        return memberIds.associateWith { m ->
            ideaIds.count { id -> votesByIdea[id]?.containsKey(m) == true }.toDouble() / ideaIds.size
        }
    }

    /** One headline number for the match screen: average agreement over ideas that have votes, as a percent. */
    fun overallAgreementPercent(items: List<Consensus>): Int {
        val voted = items.filter { it.voters > 0 }
        if (voted.isEmpty()) return 0
        return (voted.map { it.agreement }.average() * 100).roundToInt()
    }
}
