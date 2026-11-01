package com.trippin.intelligence

import com.trippin.intelligence.bayes.BayesianNetwork
import com.trippin.intelligence.bayes.StopRiskModel
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.Place
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BayesianInferenceTest {

    @Test
    fun variablesReturnsNodesInTopologicalOrder() {
        val net = BayesianNetwork().addRoot("A", 0.5)
        val nodeB = BayesianNetwork.Node("B", listOf("A"), mapOf(listOf(true) to 0.8, listOf(false) to 0.2))
        net.add(nodeB)
        assertEquals(listOf("A", "B"), net.variables)
    }

    @Test
    fun jointCalculatesExactChainRuleDistribution() {
        val net = BayesianNetwork().addRoot("A", 0.6)
        val nodeB = BayesianNetwork.Node("B", listOf("A"), mapOf(listOf(true) to 0.8, listOf(false) to 0.1))
        net.add(nodeB)

        val pTT = net.joint(mapOf("A" to true, "B" to true))
        val pTF = net.joint(mapOf("A" to true, "B" to false))
        val pFT = net.joint(mapOf("A" to false, "B" to true))
        val pFF = net.joint(mapOf("A" to false, "B" to false))

        assertEquals(0.48, pTT, 1e-9)
        assertEquals(0.12, pTF, 1e-9)
        assertEquals(0.04, pFT, 1e-9)
        assertEquals(0.36, pFF, 1e-9)
        assertEquals(1.0, pTT + pTF + pFT + pFF, 1e-9)
    }

    @Test
    fun addRejectsDuplicateNodes() {
        val net = BayesianNetwork().addRoot("A", 0.5)
        assertThrows(IllegalArgumentException::class.java) {
            net.addRoot("A", 0.3)
        }
    }

    @Test
    fun addRejectsChildrenAddedBeforeParents() {
        val net = BayesianNetwork()
        val nodeB = BayesianNetwork.Node("B", listOf("A"), mapOf(listOf(true) to 0.5, listOf(false) to 0.5))
        assertThrows(IllegalArgumentException::class.java) {
            net.add(nodeB)
        }
    }

    @Test
    fun addRejectsMismatchedCptSize() {
        val net = BayesianNetwork().addRoot("A", 0.5)
        // Variable B has 1 parent so requires 2^1 = 2 rows
        val nodeB = BayesianNetwork.Node("B", listOf("A"), mapOf(listOf(true) to 0.5))
        assertThrows(IllegalArgumentException::class.java) {
            net.add(nodeB)
        }
    }

    @Test
    fun addRejectsProbabilitiesOutsideUnitInterval() {
        assertThrows(IllegalArgumentException::class.java) {
            BayesianNetwork().addRoot("A", 1.5)
        }
        assertThrows(IllegalArgumentException::class.java) {
            BayesianNetwork().addRoot("A", -0.1)
        }
    }

    @Test
    fun probabilityQueryValidationAndEvidenceShortCircuit() {
        val net = BayesianNetwork().addRoot("A", 0.4)
        assertThrows(IllegalArgumentException::class.java) {
            net.probability("UNKNOWN")
        }
        assertEquals(1.0, net.probability("A", mapOf("A" to true)), 1e-9)
        assertEquals(0.0, net.probability("A", mapOf("A" to false)), 1e-9)
    }

    @Test
    fun stopRiskAssessmentLevelMapping() {
        val low = StopRiskModel.Assessment(pDisrupted = 0.15, pDelay = 0.10, pClosed = 0.02, pRainGivenDisrupted = 0.05)
        assertEquals("Low", low.level)

        val medium = StopRiskModel.Assessment(pDisrupted = 0.35, pDelay = 0.25, pClosed = 0.05, pRainGivenDisrupted = 0.20)
        assertEquals("Medium", medium.level)

        val high = StopRiskModel.Assessment(pDisrupted = 0.60, pDelay = 0.50, pClosed = 0.15, pRainGivenDisrupted = 0.40)
        assertEquals("High", high.level)
    }

    @Test
    fun stopRiskModelEvaluatesLongLegEvidence() {
        val shortLeg = StopRiskModel.assess(
            StopRiskModel.StopEvidence(rainProbability = 0.2, peakHour = false, travelMinutes = 15, hoursEstimated = false),
        )
        val longLeg = StopRiskModel.assess(
            StopRiskModel.StopEvidence(rainProbability = 0.2, peakHour = false, travelMinutes = 25, hoursEstimated = false),
        )
        assertTrue(longLeg.pDelay > shortLeg.pDelay)
        assertTrue(longLeg.pDisrupted > shortLeg.pDisrupted)
    }

    @Test
    fun tripBrainIsPeakWindows() {
        assertFalse(TripBrain.isPeak(509)) // 08:29
        assertTrue(TripBrain.isPeak(510))  // 08:30
        assertTrue(TripBrain.isPeak(570))  // 09:30
        assertTrue(TripBrain.isPeak(630))  // 10:30
        assertFalse(TripBrain.isPeak(631)) // 10:31

        assertFalse(TripBrain.isPeak(1049)) // 17:29
        assertTrue(TripBrain.isPeak(1050))  // 17:30
        assertTrue(TripBrain.isPeak(1140))  // 19:00
        assertTrue(TripBrain.isPeak(1230))  // 20:30
        assertFalse(TripBrain.isPeak(1231)) // 20:31
    }

    @Test
    fun tripBrainPickStopsRespectsPaceLimitsAndAlternatesCategories() {
        val candidates = listOf(
            Place("1", "Museum 1", 0.0, 0.0, Category.MUSEUM, 90),
            Place("2", "Museum 2", 0.0, 0.0, Category.MUSEUM, 90),
            Place("3", "Food 1", 0.0, 0.0, Category.FOOD, 60),
            Place("4", "Food 2", 0.0, 0.0, Category.FOOD, 60),
            Place("5", "Landmark 1", 0.0, 0.0, Category.LANDMARK, 45),
        )
        val pickedRelaxed = TripBrain.pickStops(candidates, listOf(Category.MUSEUM, Category.FOOD), TripBrain.Pace.RELAXED)
        assertTrue(pickedRelaxed.size <= TripBrain.Pace.RELAXED.maxStops)
        assertEquals(Category.MUSEUM, pickedRelaxed[0].category)
        assertEquals(Category.FOOD, pickedRelaxed[1].category)

        val pickedEmptyPref = TripBrain.pickStops(candidates, emptyList(), TripBrain.Pace.STANDARD)
        assertTrue(pickedEmptyPref.isNotEmpty())
        assertTrue(pickedEmptyPref.size <= TripBrain.Pace.STANDARD.maxStops)
    }
}
