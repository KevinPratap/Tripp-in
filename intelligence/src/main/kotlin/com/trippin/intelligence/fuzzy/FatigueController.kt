package com.trippin.intelligence.fuzzy

import com.trippin.intelligence.fuzzy.MembershipFunction.Trapezoid
import com.trippin.intelligence.fuzzy.MembershipFunction.Triangular

/**
 * The fuzzy controller behind "Today" mode. "Tired" has no sharp edge — 7 km is not suddenly
 * exhausting while 6.9 km is fine — which is exactly the kind of vague knowledge fuzzy logic models.
 *
 * Inputs: distance walked so far (km), hours on the go, temperature (°C). Output: fatigue 0–100.
 */
object FatigueController {
    const val WALKED = "walkedKm"
    const val HOURS = "activeHours"
    const val TEMP = "temperatureC"

    val walked = LinguisticVariable(
        WALKED, 0.0, 15.0,
        mapOf(
            "low" to Trapezoid(0.0, 0.0, 3.0, 6.0),
            "medium" to Triangular(3.0, 7.0, 11.0),
            "high" to Trapezoid(8.0, 11.0, 15.0, 15.0),
        ),
    )
    val hours = LinguisticVariable(
        HOURS, 0.0, 12.0,
        mapOf(
            "short" to Trapezoid(0.0, 0.0, 3.0, 5.0),
            "moderate" to Triangular(3.0, 6.0, 9.0),
            "long" to Trapezoid(7.0, 9.0, 12.0, 12.0),
        ),
    )
    val temperature = LinguisticVariable(
        TEMP, 0.0, 45.0,
        mapOf(
            "comfortable" to Trapezoid(0.0, 0.0, 26.0, 32.0),
            "hot" to Trapezoid(28.0, 34.0, 45.0, 45.0),
        ),
    )
    val fatigue = LinguisticVariable(
        "fatigue", 0.0, 100.0,
        mapOf(
            "fresh" to Trapezoid(0.0, 0.0, 20.0, 40.0),
            "tired" to Triangular(25.0, 50.0, 75.0),
            "exhausted" to Trapezoid(60.0, 80.0, 100.0, 100.0),
        ),
    )

    val rules = listOf(
        FuzzyRule(listOf(WALKED to "low", HOURS to "short"), Connective.AND, "fresh"),
        FuzzyRule(listOf(WALKED to "low", HOURS to "moderate"), Connective.AND, "fresh"),
        FuzzyRule(listOf(TEMP to "comfortable", WALKED to "low"), Connective.AND, "fresh"),
        FuzzyRule(listOf(WALKED to "medium", HOURS to "short"), Connective.AND, "tired"),
        FuzzyRule(listOf(WALKED to "medium", HOURS to "moderate"), Connective.AND, "tired"),
        FuzzyRule(listOf(HOURS to "moderate", TEMP to "hot"), Connective.AND, "tired"),
        FuzzyRule(listOf(WALKED to "high", HOURS to "long"), Connective.OR, "exhausted"),
        FuzzyRule(listOf(TEMP to "hot", WALKED to "medium"), Connective.AND, "tired"),
        FuzzyRule(listOf(TEMP to "hot", WALKED to "high"), Connective.AND, "exhausted"),
        FuzzyRule(listOf(WALKED to "medium", HOURS to "long"), Connective.AND, "exhausted"),
    )

    val system = MamdaniSystem(listOf(walked, hours, temperature), fatigue, rules)

    data class Advice(val score: Double, val label: String, val suggestion: String, val detail: MamdaniSystem.Result)

    fun advise(walkedKm: Double, activeHours: Double, temperatureC: Double): Advice {
        val r = system.evaluate(mapOf(WALKED to walkedKm, HOURS to activeHours, TEMP to temperatureC))
        val (label, suggestion) = when {
            r.crisp < 40 -> "Fresh" to "Keep going — your next stop is on plan."
            r.crisp < 65 -> "Tired" to "Add a 20-minute café break before the next stop."
            else -> "Exhausted" to "Swap the next stop for something closer, or call it a day."
        }
        return Advice(r.crisp, label, suggestion, r)
    }
}
