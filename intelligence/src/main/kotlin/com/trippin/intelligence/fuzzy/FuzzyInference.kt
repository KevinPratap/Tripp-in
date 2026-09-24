package com.trippin.intelligence.fuzzy

import kotlin.math.max
import kotlin.math.min

/**
 * IS-II Unit 2 — Fuzzy inference systems.
 *
 * A Mamdani fuzzy inference system: fuzzify crisp inputs, fire rules with min (AND) / max (OR),
 * clip each consequent with min (implication), aggregate with max, and defuzzify by centroid.
 */
sealed interface MembershipFunction {
    fun degree(x: Double): Double

    data class Triangular(val a: Double, val b: Double, val c: Double) : MembershipFunction {
        init { require(a <= b && b <= c) }
        override fun degree(x: Double): Double = when {
            x <= a || x >= c -> if (x == b) 1.0 else 0.0
            x <= b -> (x - a) / (b - a)
            else -> (c - x) / (c - b)
        }
    }

    /** Flat top between b and c. a == b (or c == d) gives a shoulder at the edge of the range. */
    data class Trapezoid(val a: Double, val b: Double, val c: Double, val d: Double) : MembershipFunction {
        init { require(a <= b && b <= c && c <= d) }
        override fun degree(x: Double): Double = when {
            x in b..c -> 1.0
            x < a || x > d -> 0.0
            x < b -> (x - a) / (b - a)
            else -> (d - x) / (d - c)
        }
    }
}

data class LinguisticVariable(
    val name: String,
    val min: Double,
    val max: Double,
    val terms: Map<String, MembershipFunction>,
) {
    fun fuzzify(x: Double): Map<String, Double> {
        val clamped = x.coerceIn(min, max)
        return terms.mapValues { (_, mf) -> mf.degree(clamped) }
    }
}

enum class Connective { AND, OR }

data class FuzzyRule(
    val antecedents: List<Pair<String, String>>,
    val connective: Connective,
    val consequent: String,
) {
    override fun toString(): String =
        "IF " + antecedents.joinToString(" ${connective.name} ") { "${it.first} is ${it.second}" } +
            " THEN output is $consequent"
}

class MamdaniSystem(
    private val inputs: List<LinguisticVariable>,
    val output: LinguisticVariable,
    val rules: List<FuzzyRule>,
    private val resolution: Int = 200,
) {
    init {
        val byName = inputs.associateBy { it.name }
        rules.forEach { r ->
            require(r.consequent in output.terms) { "Unknown output term ${r.consequent}" }
            r.antecedents.forEach { (v, t) ->
                require(byName[v]?.terms?.containsKey(t) == true) { "Unknown input term $v.$t" }
            }
        }
    }

    data class Result(
        val crisp: Double,
        val fuzzified: Map<String, Map<String, Double>>,
        val ruleStrengths: List<Double>,
        /** (x, aggregated membership) samples — drawn as the output curve in the app. */
        val aggregated: List<Pair<Double, Double>>,
    )

    fun evaluate(values: Map<String, Double>): Result {
        val fuzzified = inputs.associate { v -> v.name to v.fuzzify(values.getValue(v.name)) }

        val strengths = rules.map { rule ->
            val degrees = rule.antecedents.map { (v, t) -> fuzzified.getValue(v).getValue(t) }
            when (rule.connective) {
                Connective.AND -> degrees.min()
                Connective.OR -> degrees.max()
            }
        }

        val step = (output.max - output.min) / resolution
        val curve = (0..resolution).map { i ->
            val x = output.min + i * step
            var mu = 0.0
            rules.forEachIndexed { idx, rule ->
                val clipped = min(strengths[idx], output.terms.getValue(rule.consequent).degree(x))
                mu = max(mu, clipped)
            }
            x to mu
        }

        val area = curve.sumOf { it.second }
        val crisp = if (area == 0.0) (output.min + output.max) / 2 else curve.sumOf { it.first * it.second } / area
        return Result(crisp, fuzzified, strengths, curve)
    }
}
