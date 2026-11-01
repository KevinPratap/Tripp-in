package com.trippin.intelligence

import com.trippin.intelligence.group.DayClustering
import com.trippin.intelligence.group.Diet
import com.trippin.intelligence.group.GroupPlanner
import com.trippin.intelligence.group.GroupPreferenceAggregator
import com.trippin.intelligence.group.IdeaSignal
import com.trippin.intelligence.group.MemberConstraints
import com.trippin.intelligence.group.PlanningContext
import com.trippin.intelligence.group.StartPreference
import com.trippin.intelligence.group.VoteKind
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.OsmMapper
import com.trippin.intelligence.model.Place
import com.trippin.intelligence.model.SampleData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GroupPlannerTest {

    // 2026-10-12 is a Monday.
    private val monday = LocalDate.of(2026, 10, 12)
    private val members = listOf(
        MemberConstraints("a", "Aarav", setOf(Category.MUSEUM, Category.FOOD)),
        MemberConstraints("b", "Sara", setOf(Category.FOOD, Category.LANDMARK)),
        MemberConstraints("c", "Rahul", setOf(Category.LANDMARK)),
        MemberConstraints("d", "Mei", setOf(Category.PARK)),
    )

    private fun idea(id: String, place: Place, vararg votes: Pair<String, VoteKind>) =
        IdeaSignal(id, place.copy(id = "idea:$id"), votes.toMap())

    private fun ctx(ideas: List<IdeaSignal> = emptyList(), ms: List<MemberConstraints> = members, days: Int = 2) = PlanningContext(
        start = SampleData.hotel, startDate = monday, days = days, pace = TripBrain.Pace.STANDARD,
        members = ms, ideas = ideas, candidates = SampleData.mumbai,
    )

    @Test fun mustHaveIsAlwaysScheduled() {
        val gateway = idea("g", SampleData.byId("hajiali"), "a" to VoteKind.MUST)
        val plan = GroupPlanner.plan(ctx(listOf(gateway)))
        val scheduled = plan.days.flatMap { d -> d.stops.mapNotNull { it.ideaId } }
        assertTrue(scheduled.contains("g"))
    }

    @Test fun halfTheGroupVotingDownOverridesAMustHave() {
        val i = idea("x", SampleData.byId("hajiali"), "a" to VoteKind.MUST, "b" to VoteKind.DOWN, "c" to VoteKind.DOWN)
        val profile = GroupPreferenceAggregator.profile(ctx(listOf(i)))
        assertTrue(profile.mustHave.isEmpty())
        assertTrue(profile.avoided.contains(i))
        val plan = GroupPlanner.plan(ctx(listOf(i)))
        assertFalse(plan.days.flatMap { it.stops }.any { it.stop.place.name == "Haji Ali Dargah" })
    }

    @Test fun downVotedPlaceIsNotUsedAsFiller() {
        val i = idea("m", SampleData.byId("marine"), "d" to VoteKind.DOWN)
        val plan = GroupPlanner.plan(ctx(listOf(i)))
        assertFalse(plan.days.flatMap { it.stops }.any { it.stop.place.name == "Marine Drive" })
    }

    @Test fun lateStartWinsAndIsExplained() {
        val ms = members.mapIndexed { i, m -> if (i == 1) m.copy(start = StartPreference.LATE) else m.copy(start = StartPreference.EARLY) }
        val profile = GroupPreferenceAggregator.profile(ctx(ms = ms))
        assertEquals(10 * 60 + 30, profile.dayStart)
        assertTrue(profile.notes.any { "Sara" in it && "late start" in it })
        val plan = GroupPlanner.plan(ctx(ms = ms))
        plan.days.flatMap { it.stops }.forEach { assertTrue(it.stop.arrive >= 10 * 60 + 30) }
    }

    @Test fun shortWalkLimitSlowsThePace() {
        val ms = members.mapIndexed { i, m -> if (i == 3) m.copy(maxWalkKm = 4) else m }
        val profile = GroupPreferenceAggregator.profile(ctx(ms = ms))
        assertEquals(TripBrain.Pace.RELAXED, profile.pace)
    }

    @Test fun vegetarianMemberSetsGroupDiet() {
        val ms = members.mapIndexed { i, m -> if (i == 0) m.copy(diet = Diet.VEGETARIAN) else m }
        assertEquals(Diet.VEGETARIAN, GroupPreferenceAggregator.profile(ctx(ms = ms)).diet)
    }

    @Test fun nightlifeIsOptionalWhenSomeoneDoesNotDrink() {
        val ms = members.map { it.copy(interests = it.interests + Category.NIGHTLIFE) }.mapIndexed { i, m -> if (i == 2) m.copy(noAlcohol = true) else m }
        val bar = Place("bar", "Rooftop Bar", 18.93, 72.83, Category.NIGHTLIFE, 90, 19 * 60, 24 * 60)
        val i = idea("bar", bar, "a" to VoteKind.MUST)
        val plan = GroupPlanner.plan(ctx(listOf(i), ms))
        val stop = plan.days.flatMap { it.stops }.first { it.ideaId == "bar" }
        assertTrue(stop.optional)
    }

    @Test fun placeClosedOnADayIsMovedToAnOpenDay() {
        // CSMVS closed on Mondays: whatever day the clustering picks, it must not land on Monday.
        val museum = SampleData.byId("csmvs").copy(closedDays = setOf(1))
        val i = idea("mus", museum, "a" to VoteKind.MUST)
        val plan = GroupPlanner.plan(ctx(listOf(i)))
        val monday = plan.days.first { it.date == this.monday }
        assertFalse(monday.stops.any { it.ideaId == "mus" })
        assertTrue(plan.days.flatMap { it.stops }.any { it.ideaId == "mus" })
    }

    @Test fun lunchIsScheduledAtLunchtime() {
        val plan = GroupPlanner.plan(ctx(days = 1))
        val food = plan.days[0].stops.firstOrNull { it.stop.place.category == Category.FOOD } ?: return
        assertTrue("lunch at ${food.stop.start}", food.stop.start in 12 * 60..15 * 60)
    }

    @Test fun everyDayGetsStopsAndTimesNeverOverlap() {
        val plan = GroupPlanner.plan(ctx(days = 3))
        assertEquals(3, plan.days.size)
        plan.days.forEach { d ->
            assertTrue(d.stops.isNotEmpty())
            d.stops.zipWithNext().forEach { (a, b) -> assertTrue(b.stop.arrive >= a.stop.leave) }
            d.stops.forEach { assertTrue(it.risk.pDisrupted in 0.0..1.0) }
        }
        val names = plan.days.flatMap { d -> d.stops.map { it.stop.place.name } }
        assertEquals("no place twice", names.size, names.toSet().size)
    }

    @Test fun clusteringRespectsCapacityAndKeepsEveryPlace() {
        val groups = DayClustering.cluster(SampleData.mumbai, 3, 5)
        assertEquals(3, groups.size)
        assertEquals(SampleData.mumbai.toSet(), groups.flatten().toSet())
        groups.forEach { assertTrue(it.size <= 5) }
    }
}

class WeeklyHoursTest {
    @Test fun readsClosedDaysAndPerDayHours() {
        val w = OsmMapper.parseWeekly("Mo off; Tu-Fr 10:00-18:00; Sa,Su 09:00-20:00")!!
        assertEquals(setOf(1), w.closedDays)
        assertEquals(600 to 1080, w.byDay[2])
        assertEquals(540 to 1200, w.byDay[7])
    }

    @Test fun missingDaysAreClosed() {
        val w = OsmMapper.parseWeekly("Tu-Su 10:00-18:00")!!
        assertEquals(setOf(1), w.closedDays)
    }

    @Test fun wrapsAroundTheWeek() {
        val w = OsmMapper.parseWeekly("Fr-Mo 18:00-02:00")!!
        assertEquals(setOf(2, 3, 4), w.closedDays)
        assertEquals(1080 to 1440, w.byDay[5])
    }

    @Test fun splitShiftBecomesOneWindow() {
        val w = OsmMapper.parseWeekly("12:00-15:00,19:00-23:00")!!
        assertEquals(720 to 1380, w.byDay[3])
    }

    @Test fun refusesWhatItCannotRead() {
        assertNull(OsmMapper.parseWeekly("sunrise-sunset"))
        assertNull(OsmMapper.parseWeekly("Mo-Fr 09:00-17:00 \"by appointment\""))
    }

    @Test fun placeKnowsWhenItIsShut() {
        val p = OsmMapper.toPlace("1", 0.0, 0.0, mapOf("name" to "Museum", "tourism" to "museum", "opening_hours" to "Mo off; Tu-Su 10:00-17:00", "wheelchair" to "yes"))!!
        assertFalse(p.hoursEstimated)
        assertNull(p.forDay(1))
        assertNotNull(p.forDay(2))
        assertEquals(600, p.opensAt)
        assertTrue(Place.TAG_WHEELCHAIR in p.tags)
    }

    @Test fun dietTagsAreRead() {
        val p = OsmMapper.toPlace("1", 0.0, 0.0, mapOf("name" to "Veg Cafe", "amenity" to "cafe", "diet:vegan" to "only"))!!
        assertTrue(Place.TAG_VEGAN in p.tags && Place.TAG_VEGETARIAN in p.tags)
    }
}
