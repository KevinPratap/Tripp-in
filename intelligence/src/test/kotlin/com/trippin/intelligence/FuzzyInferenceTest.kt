package com.trippin.intelligence

import com.trippin.intelligence.fuzzy.Connective
import com.trippin.intelligence.fuzzy.FatigueController
import com.trippin.intelligence.fuzzy.FuzzyRule
import com.trippin.intelligence.fuzzy.LinguisticVariable
import com.trippin.intelligence.fuzzy.MamdaniSystem
import com.trippin.intelligence.fuzzy.MembershipFunction.Trapezoid
import com.trippin.intelligence.fuzzy.MembershipFunction.Triangular
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyInferenceTest {

    @Test
    fun triangularMembershipFunctionDegrees() {
        val tri = Triangular(0.0, 5.0, 10.0)
        assertEquals(0.0, tri.degree(-1.0), 1e-9)
        assertEquals(0.0, tri.degree(0.0), 1e-9)
        assertEquals(0.5, tri.degree(2.5), 1e-9)
        assertEquals(1.0, tri.degree(5.0), 1e-9)
        assertEquals(0.5, tri.degree(7.5), 1e-9)
        assertEquals(0.0, tri.degree(10.0), 1e-9)
        assertEquals(0.0, tri.degree(12.0), 1e-9)
    }

    @Test
    fun triangularRequiresOrderedParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            Triangular(5.0, 2.0, 10.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Triangular(0.0, 10.0, 5.0)
        }
    }

    @Test
    fun trapezoidMembershipFunctionDegreesAndShoulders() {
        val trap = Trapezoid(0.0, 2.0, 6.0, 8.0)
        assertEquals(0.0, trap.degree(-1.0), 1e-9)
        assertEquals(0.5, trap.degree(1.0), 1e-9)
        assertEquals(1.0, trap.degree(2.0), 1e-9)
        assertEquals(1.0, trap.degree(4.0), 1e-9)
        assertEquals(1.0, trap.degree(6.0), 1e-9)
        assertEquals(0.5, trap.degree(7.0), 1e-9)
        assertEquals(0.0, trap.degree(8.0), 1e-9)

        val leftShoulder = Trapezoid(0.0, 0.0, 3.0, 6.0)
        assertEquals(1.0, leftShoulder.degree(0.0), 1e-9)

        val rightShoulder = Trapezoid(4.0, 7.0, 10.0, 10.0)
        assertEquals(1.0, rightShoulder.degree(10.0), 1e-9)
    }

    @Test
    fun trapezoidRequiresOrderedParameters() {
        assertThrows(IllegalArgumentException::class.java) {
            Trapezoid(4.0, 2.0, 6.0, 8.0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            Trapezoid(0.0, 2.0, 8.0, 6.0)
        }
    }

    @Test
    fun linguisticVariableClampsInputsToRange() {
        val variable = LinguisticVariable(
            "speed", 0.0, 100.0,
            mapOf(
                "slow" to Trapezoid(0.0, 0.0, 20.0, 40.0),
                "fast" to Trapezoid(60.0, 80.0, 100.0, 100.0),
            ),
        )
        val underflow = variable.fuzzify(-50.0)
        assertEquals(1.0, underflow["slow"]!!, 1e-9)
        assertEquals(0.0, underflow["fast"]!!, 1e-9)

        val overflow = variable.fuzzify(150.0)
        assertEquals(0.0, overflow["slow"]!!, 1e-9)
        assertEquals(1.0, overflow["fast"]!!, 1e-9)
    }

    @Test
    fun fuzzyRuleFormatting() {
        val andRule = FuzzyRule(listOf("speed" to "fast", "temp" to "hot"), Connective.AND, "high")
        assertEquals("IF speed is fast AND temp is hot THEN output is high", andRule.toString())

        val orRule = FuzzyRule(listOf("speed" to "slow", "temp" to "cold"), Connective.OR, "low")
        assertEquals("IF speed is slow OR temp is cold THEN output is low", orRule.toString())
    }

    @Test
    fun mamdaniSystemValidatesRulesAgainstVariables() {
        val input = LinguisticVariable("x", 0.0, 10.0, mapOf("low" to Triangular(0.0, 2.0, 5.0)))
        val output = LinguisticVariable("y", 0.0, 10.0, mapOf("low" to Triangular(0.0, 2.0, 5.0)))

        // Unknown output term
        assertThrows(IllegalArgumentException::class.java) {
            MamdaniSystem(listOf(input), output, listOf(FuzzyRule(listOf("x" to "low"), Connective.AND, "unknown")))
        }

        // Unknown input variable
        assertThrows(IllegalArgumentException::class.java) {
            MamdaniSystem(listOf(input), output, listOf(FuzzyRule(listOf("unknown" to "low"), Connective.AND, "low")))
        }

        // Unknown input term
        assertThrows(IllegalArgumentException::class.java) {
            MamdaniSystem(listOf(input), output, listOf(FuzzyRule(listOf("x" to "unknown"), Connective.AND, "low")))
        }
    }

    @Test
    fun mamdaniSystemFiresAndConnectiveWithMinimumStrength() {
        val in1 = LinguisticVariable("x", 0.0, 10.0, mapOf("a" to Triangular(0.0, 5.0, 10.0)))
        val in2 = LinguisticVariable("y", 0.0, 10.0, mapOf("b" to Triangular(0.0, 5.0, 10.0)))
        val out = LinguisticVariable("z", 0.0, 100.0, mapOf("high" to Triangular(60.0, 80.0, 100.0)))

        val system = MamdaniSystem(
            listOf(in1, in2), out,
            listOf(FuzzyRule(listOf("x" to "a", "y" to "b"), Connective.AND, "high")),
        )
        // x = 2.5 -> degree 0.5; y = 5.0 -> degree 1.0; min = 0.5
        val result = system.evaluate(mapOf("x" to 2.5, "y" to 5.0))
        assertEquals(0.5, result.ruleStrengths[0], 1e-6)
    }

    @Test
    fun mamdaniSystemFiresOrConnectiveWithMaximumStrength() {
        val in1 = LinguisticVariable("x", 0.0, 10.0, mapOf("a" to Triangular(0.0, 5.0, 10.0)))
        val in2 = LinguisticVariable("y", 0.0, 10.0, mapOf("b" to Triangular(0.0, 5.0, 10.0)))
        val out = LinguisticVariable("z", 0.0, 100.0, mapOf("high" to Triangular(60.0, 80.0, 100.0)))

        val system = MamdaniSystem(
            listOf(in1, in2), out,
            listOf(FuzzyRule(listOf("x" to "a", "y" to "b"), Connective.OR, "high")),
        )
        // x = 2.5 -> degree 0.5; y = 5.0 -> degree 1.0; max = 1.0
        val result = system.evaluate(mapOf("x" to 2.5, "y" to 5.0))
        assertEquals(1.0, result.ruleStrengths[0], 1e-6)
    }

    @Test
    fun mamdaniSystemDefaultsToMidpointWhenNoRulesFire() {
        val in1 = LinguisticVariable("x", 0.0, 10.0, mapOf("a" to Triangular(3.0, 5.0, 7.0)))
        val out = LinguisticVariable("z", 0.0, 100.0, mapOf("high" to Triangular(60.0, 80.0, 100.0)))

        val system = MamdaniSystem(
            listOf(in1), out,
            listOf(FuzzyRule(listOf("x" to "a"), Connective.AND, "high")),
        )
        // x = 0.0 -> degree 0.0, rule strength 0.0
        val result = system.evaluate(mapOf("x" to 0.0))
        assertEquals(0.0, result.ruleStrengths[0], 1e-6)
        assertEquals(50.0, result.crisp, 1e-6)
    }

    @Test
    fun fatigueControllerAdviceLabelsAndSuggestions() {
        val fresh = FatigueController.advise(0.5, 1.0, 22.0)
        assertTrue(fresh.score < 40.0)
        assertEquals("Fresh", fresh.label)
        assertTrue(fresh.suggestion.contains("Keep going"))

        val tired = FatigueController.advise(7.0, 6.0, 28.0)
        assertTrue(tired.score in 40.0..64.99)
        assertEquals("Tired", tired.label)
        assertTrue(tired.suggestion.contains("café break"))

        val exhausted = FatigueController.advise(14.0, 10.0, 38.0)
        assertTrue(exhausted.score >= 65.0)
        assertEquals("Exhausted", exhausted.label)
        assertTrue(exhausted.suggestion.contains("Swap the next stop"))
    }
}
