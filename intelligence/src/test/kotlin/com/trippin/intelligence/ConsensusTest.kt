package com.trippin.intelligence

import com.trippin.intelligence.group.AgreementLevel
import com.trippin.intelligence.group.GroupConsensus
import com.trippin.intelligence.group.VoteKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConsensusTest {

    private fun c(id: String, votes: Map<String, VoteKind>, members: Int = 3) = GroupConsensus.of(id, votes, members)

    @Test fun `no votes is unvoted with zero agreement`() {
        val r = c("a", emptyMap())
        assertEquals(AgreementLevel.UNVOTED, r.level)
        assertEquals(0.0, r.agreement, 0.0)
        assertEquals(0, r.score)
    }

    @Test fun `score uses the planner weights`() {
        val r = c("a", mapOf("1" to VoteKind.MUST, "2" to VoteKind.UP, "3" to VoteKind.DOWN))
        assertEquals(3 + 1 - 2, r.score)
        assertEquals(1, r.musts); assertEquals(1, r.ups); assertEquals(1, r.downs)
    }

    @Test fun `everyone in with half musts is loved`() {
        val r = c("a", mapOf("1" to VoteKind.MUST, "2" to VoteKind.MUST, "3" to VoteKind.UP))
        assertEquals(AgreementLevel.LOVED, r.level)
        assertEquals(1.0, r.agreement, 0.0)
    }

    @Test fun `loved needs the whole group to have voted`() {
        val r = c("a", mapOf("1" to VoteKind.MUST, "2" to VoteKind.MUST), members = 3)
        assertEquals(AgreementLevel.LIKED, r.level)
    }

    @Test fun `all ups without enough musts is only liked`() {
        val r = c("a", mapOf("1" to VoteKind.UP, "2" to VoteKind.UP, "3" to VoteKind.UP))
        assertEquals(AgreementLevel.LIKED, r.level)
    }

    @Test fun `equal sides is a split`() {
        val r = c("a", mapOf("1" to VoteKind.UP, "2" to VoteKind.DOWN), members = 2)
        assertEquals(AgreementLevel.SPLIT, r.level)
    }

    @Test fun `two against one in a group of three is liked not split`() {
        val r = c("a", mapOf("1" to VoteKind.UP, "2" to VoteKind.UP, "3" to VoteKind.DOWN))
        assertEquals(AgreementLevel.LIKED, r.level)
    }

    @Test fun `three against two with five voters is still a split`() {
        val v = mapOf("1" to VoteKind.UP, "2" to VoteKind.UP, "3" to VoteKind.UP, "4" to VoteKind.DOWN, "5" to VoteKind.DOWN)
        assertEquals(AgreementLevel.SPLIT, c("a", v, members = 5).level)
    }

    @Test fun `more downs than ups is disliked`() {
        val r = c("a", mapOf("1" to VoteKind.DOWN, "2" to VoteKind.DOWN, "3" to VoteKind.UP))
        assertEquals(AgreementLevel.DISLIKED, r.level)
    }

    @Test fun `all downs is disliked`() {
        assertEquals(AgreementLevel.DISLIKED, c("a", mapOf("1" to VoteKind.DOWN)).level)
    }

    @Test fun `rank orders by score then musts then id`() {
        val a = c("b", mapOf("1" to VoteKind.UP, "2" to VoteKind.UP, "3" to VoteKind.UP))          // 3
        val b = c("a", mapOf("1" to VoteKind.MUST))                                                  // 3, one must
        val d = c("c", mapOf("1" to VoteKind.UP))                                                    // 1
        assertEquals(listOf("a", "b", "c"), GroupConsensus.rank(listOf(d, a, b)).map { it.ideaId })
    }

    @Test fun `rank is stable for identical results`() {
        val x = c("z", mapOf("1" to VoteKind.UP))
        val y = c("y", mapOf("1" to VoteKind.UP))
        assertEquals(listOf("y", "z"), GroupConsensus.rank(listOf(x, y)).map { it.ideaId })
    }

    @Test fun `split round returns only split ideas`() {
        val split = c("s", mapOf("1" to VoteKind.UP, "2" to VoteKind.DOWN), members = 2)
        val liked = c("l", mapOf("1" to VoteKind.UP, "2" to VoteKind.UP), members = 2)
        assertEquals(listOf("s"), GroupConsensus.splitRound(listOf(split, liked)).map { it.ideaId })
    }

    @Test fun `winners excludes split and disliked and respects the limit`() {
        val loved = c("a", mapOf("1" to VoteKind.MUST, "2" to VoteKind.MUST, "3" to VoteKind.MUST))
        val liked = c("b", mapOf("1" to VoteKind.UP, "2" to VoteKind.UP, "3" to VoteKind.UP))
        val split = c("c", mapOf("1" to VoteKind.UP, "2" to VoteKind.DOWN), members = 2)
        val bad = c("d", mapOf("1" to VoteKind.DOWN))
        val all = listOf(bad, split, liked, loved)
        assertEquals(listOf("a", "b"), GroupConsensus.winners(all).map { it.ideaId })
        assertEquals(listOf("a"), GroupConsensus.winners(all, limit = 1).map { it.ideaId })
    }

    @Test fun `deck skips what I voted on and puts group favourites first`() {
        val seenLow = c("b", mapOf("2" to VoteKind.UP))
        val seenHigh = c("a", mapOf("2" to VoteKind.MUST, "3" to VoteKind.UP))
        val fresh2 = c("z", emptyMap())
        val fresh1 = c("y", emptyMap())
        val mine = c("m", mapOf("1" to VoteKind.UP))
        val order = GroupConsensus.deckOrder(listOf(fresh2, seenLow, mine, fresh1, seenHigh), alreadyVotedByMe = setOf("m"))
        assertEquals(listOf("a", "b", "y", "z"), order)
    }

    @Test fun `deck order is empty when I voted on everything`() {
        val a = c("a", mapOf("1" to VoteKind.UP))
        assertTrue(GroupConsensus.deckOrder(listOf(a), setOf("a")).isEmpty())
    }

    @Test fun `member progress counts each member's own votes`() {
        val votes = mapOf(
            "i1" to mapOf("u1" to VoteKind.UP, "u2" to VoteKind.DOWN),
            "i2" to mapOf("u1" to VoteKind.MUST),
        )
        val p = GroupConsensus.memberProgress(listOf("u1", "u2", "u3"), listOf("i1", "i2"), votes)
        assertEquals(1.0, p["u1"]!!, 0.0)
        assertEquals(0.5, p["u2"]!!, 0.0)
        assertEquals(0.0, p["u3"]!!, 0.0)
    }

    @Test fun `member progress with no ideas counts everyone as done`() {
        assertEquals(1.0, GroupConsensus.memberProgress(listOf("u1"), emptyList(), emptyMap())["u1"]!!, 0.0)
    }

    @Test fun `overall agreement averages only voted ideas`() {
        val a = c("a", mapOf("1" to VoteKind.UP, "2" to VoteKind.UP))                       // 1.0
        val b = c("b", mapOf("1" to VoteKind.UP, "2" to VoteKind.DOWN), members = 2)        // 0.5
        val none = c("c", emptyMap())
        assertEquals(75, GroupConsensus.overallAgreementPercent(listOf(a, b, none)))
        assertEquals(0, GroupConsensus.overallAgreementPercent(listOf(none)))
    }
}
