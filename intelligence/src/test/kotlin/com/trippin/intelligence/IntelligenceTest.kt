package com.trippin.intelligence

import com.trippin.intelligence.bayes.BayesianNetwork
import com.trippin.intelligence.bayes.StopRiskModel
import com.trippin.intelligence.fuzzy.FatigueController
import com.trippin.intelligence.fuzzy.MembershipFunction
import com.trippin.intelligence.genetic.RouteOptimizer
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.SampleData
import com.trippin.intelligence.rl.QLearningRecommender
import com.trippin.intelligence.rl.QLearningRecommender.Energy
import com.trippin.intelligence.rl.QLearningRecommender.Feedback
import com.trippin.intelligence.rl.QLearningRecommender.State
import com.trippin.intelligence.rl.QLearningRecommender.TimeOfDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BayesianNetworkTest {

    /** The textbook burglary network (Russell & Norvig, fig. 14.2). Known answer: P(B | j, m) ≈ 0.284. */
    @Test fun burglaryNetworkMatchesTextbook() {
        val net = BayesianNetwork()
            .addRoot("B", 0.001)
            .addRoot("E", 0.002)
            .add(
                BayesianNetwork.Node(
                    "A", listOf("B", "E"),
                    mapOf(listOf(true, true) to 0.95, listOf(true, false) to 0.94, listOf(false, true) to 0.29, listOf(false, false) to 0.001),
                ),
            )
            .add(BayesianNetwork.Node("J", listOf("A"), mapOf(listOf(true) to 0.90, listOf(false) to 0.05)))
            .add(BayesianNetwork.Node("M", listOf("A"), mapOf(listOf(true) to 0.70, listOf(false) to 0.01)))
        assertEquals(0.284, net.probability("B", mapOf("J" to true, "M" to true)), 0.001)
    }

    @Test fun rainRaisesDisruptionRisk() {
        fun risk(rain: Double) = StopRiskModel.assess(StopRiskModel.StopEvidence(rain, peakHour = false, travelMinutes = 10, hoursEstimated = false))
        val dry = risk(0.05)
        val wet = risk(0.9)
        assertTrue(wet.pDisrupted > dry.pDisrupted)
        assertTrue(wet.pDelay > dry.pDelay)
        assertTrue(wet.pRainGivenDisrupted in 0.0..1.0)
    }

    @Test fun estimatedHoursRaiseClosedRisk() {
        fun closed(est: Boolean) = StopRiskModel.assess(StopRiskModel.StopEvidence(0.2, false, 10, est)).pClosed
        assertEquals(0.15, closed(true), 1e-9)
        assertEquals(0.02, closed(false), 1e-9)
    }
}

class FuzzyTest {

    @Test fun membershipShapes() {
        val tri = MembershipFunction.Triangular(0.0, 5.0, 10.0)
        assertEquals(0.0, tri.degree(0.0), 1e-9)
        assertEquals(0.5, tri.degree(2.5), 1e-9)
        assertEquals(1.0, tri.degree(5.0), 1e-9)
        assertEquals(0.2, tri.degree(9.0), 1e-9)
        val leftShoulder = MembershipFunction.Trapezoid(0.0, 0.0, 3.0, 6.0)
        assertEquals(1.0, leftShoulder.degree(0.0), 1e-9)
        assertEquals(0.5, leftShoulder.degree(4.5), 1e-9)
    }

    @Test fun freshAndExhaustedEnds() {
        assertTrue(FatigueController.advise(1.0, 1.0, 22.0).score < 40)
        assertTrue(FatigueController.advise(13.0, 10.0, 36.0).score > 65)
    }

    @Test fun fatigueNeverDropsAsYouWalkMore() {
        var last = -1.0
        var km = 0.0
        while (km <= 15.0) {
            val s = FatigueController.advise(km, 6.0, 25.0).score
            assertTrue("fatigue fell at $km km", s >= last - 1e-6)
            last = s
            km += 0.5
        }
    }
}

class GeneticAlgorithmTest {

    private fun isPermutation(xs: List<Int>, n: Int) = xs.size == n && xs.toSet() == (0 until n).toSet()

    @Test fun operatorsKeepValidPermutations() {
        val rng = Random(1)
        repeat(2000) {
            val a = (0 until 9).shuffled(rng)
            val b = (0 until 9).shuffled(rng)
            assertTrue(isPermutation(RouteOptimizer.orderCrossover(a, b, rng), 9))
            assertTrue(isPermutation(RouteOptimizer.mutate(a, rng), 9))
        }
    }

    private fun permutations(xs: List<Int>): List<List<Int>> =
        if (xs.size <= 1) listOf(xs) else xs.flatMap { x -> permutations(xs - x).map { listOf(x) + it } }

    @Test fun findsTheBruteForceOptimumOnSevenStops() {
        val stops = SampleData.mumbai.take(7)
        val opt = RouteOptimizer()
        val result = opt.optimise(SampleData.hotel, stops, 9 * 60)
        val bestPossible = permutations((0 until 7).toList()).minOf { opt.evaluate(SampleData.hotel, stops, it, 9 * 60).cost }
        assertTrue("GA ${result.best.cost} vs optimum $bestPossible", result.best.cost <= bestPossible * 1.01 + 1e-9)
    }

    @Test fun elitismMeansBestNeverGetsWorse() {
        val result = RouteOptimizer().optimise(SampleData.hotel, SampleData.mumbai, 9 * 60)
        result.history.zipWithNext().forEach { (a, b) -> assertTrue(b.bestCost <= a.bestCost + 1e-9) }
        assertTrue(result.best.cost <= result.initial.cost)
    }

    @Test fun respectsOpeningHoursWhenAFeasibleOrderExists() {
        val stops = listOf("britannia", "csmvs", "gateway", "marine").map(SampleData::byId)
        val result = RouteOptimizer().optimise(SampleData.hotel, stops, 9 * 60)
        assertEquals(0, result.best.violationMinutes)
    }
}

class QLearningTest {

    @Test fun oneUpdateFollowsTheBellmanFormula() {
        val agent = QLearningRecommender(alpha = 0.3, gamma = 0.6)
        val s = State(TimeOfDay.MORNING, Energy.HIGH)
        agent.learn(s, Category.MUSEUM, Feedback.LIKED, next = null)
        assertEquals(0.3, agent.qValue(s, Category.MUSEUM), 1e-9)
    }

    /** A simulated traveller: loves food in the afternoon, wants parks when tired, museums in the morning. */
    @Test fun learnsASimulatedTravellersTaste() {
        val agent = QLearningRecommender(rng = Random(3))
        val rng = Random(11)
        fun react(s: State, a: Category): Feedback = when {
            s.energy == Energy.LOW -> if (a == Category.PARK) Feedback.LIKED else Feedback.SKIPPED
            s.time == TimeOfDay.AFTERNOON -> if (a == Category.FOOD) Feedback.LIKED else Feedback.SKIPPED
            s.time == TimeOfDay.MORNING -> if (a == Category.MUSEUM) Feedback.LIKED else Feedback.KEPT
            else -> Feedback.KEPT
        }
        repeat(1500) {
            val s = State(TimeOfDay.entries.random(rng), Energy.entries.random(rng))
            val a = agent.choose(s)
            val next = State(TimeOfDay.entries.random(rng), Energy.entries.random(rng))
            agent.learn(s, a, react(s, a), next)
        }
        assertEquals(Category.FOOD, agent.bestAction(State(TimeOfDay.AFTERNOON, Energy.HIGH)))
        assertEquals(Category.PARK, agent.bestAction(State(TimeOfDay.EVENING, Energy.LOW)))
        assertEquals(Category.MUSEUM, agent.bestAction(State(TimeOfDay.MORNING, Energy.MEDIUM)))
    }
}

class TripBrainTest {

    @Test fun plansAVerifiedDayWithRiskScores() {
        val stops = TripBrain.pickStops(SampleData.mumbai, listOf(Category.MUSEUM, Category.FOOD, Category.LANDMARK), TripBrain.Pace.STANDARD)
        assertTrue(stops.size in 1..TripBrain.Pace.STANDARD.maxStops)
        val plan = TripBrain.planDay(SampleData.hotel, stops, 9 * 60 + 30, rainProbability = 0.4)
        assertTrue(plan.verified)
        plan.stops.forEach { assertTrue(it.risk.pDisrupted in 0.0..1.0) }
        plan.stops.zipWithNext().forEach { (a, b) -> assertTrue(b.stop.arrive >= a.stop.leave) }
    }
}

class OsmMapperTest {
    @Test fun parsesSimpleAndOvernightHours() {
        assertEquals(600 to 1080, com.trippin.intelligence.model.OsmMapper.parseHours("Tu-Su 10:00-18:00"))
        assertEquals(0 to 1440, com.trippin.intelligence.model.OsmMapper.parseHours("24/7"))
        assertEquals(1080 to 1440, com.trippin.intelligence.model.OsmMapper.parseHours("Mo-Sa 18:00-02:00"))
        assertEquals(null, com.trippin.intelligence.model.OsmMapper.parseHours("sunrise-sunset"))
    }

    @Test fun estimatedWhenNoHoursPublished() {
        val p = com.trippin.intelligence.model.OsmMapper.toPlace("1", 0.0, 0.0, mapOf("name" to "Some Park", "leisure" to "park"))!!
        assertEquals(Category.PARK, p.category)
        assertTrue(p.hoursEstimated)
        assertEquals(null, com.trippin.intelligence.model.OsmMapper.toPlace("2", 0.0, 0.0, mapOf("amenity" to "cafe")))
    }
}
