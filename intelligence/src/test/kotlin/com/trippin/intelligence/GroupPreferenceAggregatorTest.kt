package com.trippin.intelligence

import com.trippin.intelligence.group.Diet
import com.trippin.intelligence.group.GroupPreferenceAggregator
import com.trippin.intelligence.group.IdeaSignal
import com.trippin.intelligence.group.MemberConstraints
import com.trippin.intelligence.group.PlanningContext
import com.trippin.intelligence.group.StartPreference
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Place
import com.trippin.intelligence.model.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GroupPreferenceAggregatorTest {

    private val monday = LocalDate.of(2026, 10, 12)

    private fun idea(id: String, place: Place, vararg votes: Pair<String, VoteKind>) =
        IdeaSignal(id, place.copy(id = "idea:$id"), votes.toMap())

    private fun ctx(
        members: List<MemberConstraints> = emptyList(),
        ideas: List<IdeaSignal> = emptyList(),
        pace: TripBrain.Pace = TripBrain.Pace.STANDARD,
        learnedOrder: List<Category> = Category.entries,
    ) = PlanningContext(
        start = SampleData.hotel,
        startDate = monday,
        days = 2,
        pace = pace,
        members = members,
        ideas = ideas,
        candidates = SampleData.mumbai,
        learnedOrder = learnedOrder,
    )

    @Test
    fun emptyMembersDefaultsToYou() {
        val profile = GroupPreferenceAggregator.profile(ctx(members = emptyList()))
        assertEquals(9 * 60 + 30, profile.dayStart)
        assertEquals(TripBrain.Pace.STANDARD, profile.pace)
        assertEquals(Diet.ANY, profile.diet)
        assertFalse(profile.nightlifeOptional)
        assertFalse(profile.accessibility)
    }

    @Test
    fun majorityEarlyRisersSetsEarlyStartTime() {
        val ms = listOf(
            MemberConstraints("1", "Aarav", start = StartPreference.EARLY),
            MemberConstraints("2", "Sara", start = StartPreference.EARLY),
            MemberConstraints("3", "Rahul", start = StartPreference.NORMAL),
        )
        val profile = GroupPreferenceAggregator.profile(ctx(members = ms))
        assertEquals(8 * 60 + 30, profile.dayStart)
        assertTrue(profile.notes.any { it.contains("early risers") })
    }

    @Test
    fun singleLateStartOverridesMajorityEarlyRisers() {
        val ms = listOf(
            MemberConstraints("1", "Aarav", start = StartPreference.EARLY),
            MemberConstraints("2", "Sara", start = StartPreference.EARLY),
            MemberConstraints("3", "Rahul", start = StartPreference.LATE),
        )
        val profile = GroupPreferenceAggregator.profile(ctx(members = ms))
        assertEquals(10 * 60 + 30, profile.dayStart)
        assertTrue(profile.notes.any { it.contains("Rahul") && it.contains("late start") })
    }

    @Test
    fun tieInEarlyRisersDefaultsToNormalStartTime() {
        val ms = listOf(
            MemberConstraints("1", "Aarav", start = StartPreference.EARLY),
            MemberConstraints("2", "Sara", start = StartPreference.NORMAL),
        )
        val profile = GroupPreferenceAggregator.profile(ctx(members = ms))
        assertEquals(9 * 60 + 30, profile.dayStart)
    }

    @Test
    fun walkingLimitUnderFourSlowsToRelaxed() {
        val ms = listOf(
            MemberConstraints("1", "Aarav", maxWalkKm = 3),
            MemberConstraints("2", "Sara", maxWalkKm = 8),
        )
        val profile = GroupPreferenceAggregator.profile(ctx(members = ms, pace = TripBrain.Pace.PACKED))
        assertEquals(TripBrain.Pace.RELAXED, profile.pace)
        assertTrue(profile.notes.any { it.contains("Aarav") && it.contains("under 3 km") })
    }

    @Test
    fun walkingLimitFiveToSixStepsDownOneLevel() {
        val ms = listOf(MemberConstraints("1", "Aarav", maxWalkKm = 5))
        val packedProfile = GroupPreferenceAggregator.profile(ctx(members = ms, pace = TripBrain.Pace.PACKED))
        assertEquals(TripBrain.Pace.STANDARD, packedProfile.pace)

        val standardProfile = GroupPreferenceAggregator.profile(ctx(members = ms, pace = TripBrain.Pace.STANDARD))
        assertEquals(TripBrain.Pace.RELAXED, standardProfile.pace)
    }

    @Test
    fun walkingLimitAboveSixKeepsOriginalPace() {
        val ms = listOf(MemberConstraints("1", "Aarav", maxWalkKm = 7))
        val profile = GroupPreferenceAggregator.profile(ctx(members = ms, pace = TripBrain.Pace.PACKED))
        assertEquals(TripBrain.Pace.PACKED, profile.pace)
    }

    @Test
    fun veganDietPrecedesVegetarian() {
        val ms = listOf(
            MemberConstraints("1", "Aarav", diet = Diet.VEGETARIAN),
            MemberConstraints("2", "Sara", diet = Diet.VEGAN),
            MemberConstraints("3", "Rahul", diet = Diet.ANY),
        )
        val profile = GroupPreferenceAggregator.profile(ctx(members = ms))
        assertEquals(Diet.VEGAN, profile.diet)
        assertTrue(profile.notes.any { it.contains("vegan-friendly") && it.contains("Aarav and Sara") })
    }

    @Test
    fun accessibilityRequestEnablesStepFreeNote() {
        val ms = listOf(
            MemberConstraints("1", "Aarav", accessibility = true),
            MemberConstraints("2", "Sara", accessibility = false),
        )
        val profile = GroupPreferenceAggregator.profile(ctx(members = ms))
        assertTrue(profile.accessibility)
        assertTrue(profile.notes.any { it.contains("Step-free places") && it.contains("Aarav") })
    }

    @Test
    fun nightlifeOptionalWhenNoAlcoholOrEarlyStart() {
        val noAlc = listOf(
            MemberConstraints("1", "Aarav", noAlcohol = true, interests = setOf(Category.NIGHTLIFE)),
        )
        val profileAlc = GroupPreferenceAggregator.profile(ctx(members = noAlc))
        assertTrue(profileAlc.nightlifeOptional)
        assertTrue(profileAlc.notes.any { it.contains("Nightlife is marked optional") })

        val earlyOnly = listOf(MemberConstraints("1", "Sara", start = StartPreference.EARLY))
        val profileEarly = GroupPreferenceAggregator.profile(ctx(members = earlyOnly))
        assertTrue(profileEarly.nightlifeOptional)
    }

    @Test
    fun learnedTasteBreaksTieInCategoryWeights() {
        val ms = listOf(
            MemberConstraints("1", "Aarav", interests = setOf(Category.LANDMARK)),
            MemberConstraints("2", "Sara", interests = setOf(Category.LANDMARK)),
        )
        val profile = GroupPreferenceAggregator.profile(
            ctx(members = ms, learnedOrder = listOf(Category.PARK, Category.MUSEUM, Category.FOOD, Category.LANDMARK, Category.SHOPPING, Category.NIGHTLIFE)),
        )
        val wPark = profile.interestWeights.getValue(Category.PARK)
        val wMuseum = profile.interestWeights.getValue(Category.MUSEUM)
        assertTrue(wPark > wMuseum)
    }

    @Test
    fun mustHaveAcceptedWhenDownVotesLessThanHalf() {
        val hajiali = SampleData.byId("hajiali")
        val i = idea("1", hajiali, "a" to VoteKind.MUST, "b" to VoteKind.DOWN, "c" to VoteKind.UP)
        val profile = GroupPreferenceAggregator.profile(ctx(members = listOf(
            MemberConstraints("a", "Aarav"), MemberConstraints("b", "Sara"), MemberConstraints("c", "Rahul"),
        ), ideas = listOf(i)))
        assertEquals(listOf(i), profile.mustHave)
    }

    @Test
    fun mustHaveDroppedWhenDownVotesReachHalf() {
        val hajiali = SampleData.byId("hajiali")
        val i = idea("1", hajiali, "a" to VoteKind.MUST, "b" to VoteKind.DOWN, "c" to VoteKind.DOWN, "d" to VoteKind.UP)
        val profile = GroupPreferenceAggregator.profile(ctx(members = listOf(
            MemberConstraints("a", "Aarav"), MemberConstraints("b", "Sara"), MemberConstraints("c", "Rahul"), MemberConstraints("d", "Mei"),
        ), ideas = listOf(i)))
        assertTrue(profile.mustHave.isEmpty())
        assertTrue(profile.notes.any { it.contains("must-have for someone, but half the group voted it down") })
    }
}
