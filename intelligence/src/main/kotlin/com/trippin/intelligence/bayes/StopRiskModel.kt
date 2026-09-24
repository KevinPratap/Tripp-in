package com.trippin.intelligence.bayes

/**
 * The Bayesian network the app uses to answer "how likely is this stop to go wrong?".
 *
 *   Rain ─┐
 *   Peak ─┼─► Delay ─┐
 *   Long ─┘          ├─► Disrupted
 *   Estimated ─► Closed ┘
 *
 * Rain's prior comes from the live forecast (Open-Meteo precipitation probability).
 * The other roots are usually observed, so they enter as evidence.
 *
 * The conditional probabilities below are modelling assumptions, written down so they can be
 * defended and tuned — they are not measured data.
 */
object StopRiskModel {
    const val RAIN = "Rain"
    const val PEAK_HOUR = "PeakHour"
    const val LONG_LEG = "LongLeg"
    const val HOURS_ESTIMATED = "HoursEstimated"
    const val DELAY = "Delay"
    const val CLOSED = "Closed"
    const val DISRUPTED = "Disrupted"

    fun network(rainProbability: Double): BayesianNetwork = BayesianNetwork()
        .addRoot(RAIN, rainProbability.coerceIn(0.0, 1.0))
        .addRoot(PEAK_HOUR, 0.3)
        .addRoot(LONG_LEG, 0.35)
        .addRoot(HOURS_ESTIMATED, 0.25)
        .add(
            BayesianNetwork.Node(
                DELAY, listOf(RAIN, PEAK_HOUR, LONG_LEG),
                mapOf(
                    listOf(true, true, true) to 0.75,
                    listOf(true, true, false) to 0.55,
                    listOf(true, false, true) to 0.50,
                    listOf(true, false, false) to 0.30,
                    listOf(false, true, true) to 0.45,
                    listOf(false, true, false) to 0.30,
                    listOf(false, false, true) to 0.25,
                    listOf(false, false, false) to 0.08,
                ),
            ),
        )
        .add(
            BayesianNetwork.Node(
                CLOSED, listOf(HOURS_ESTIMATED),
                mapOf(listOf(true) to 0.15, listOf(false) to 0.02),
            ),
        )
        .add(
            BayesianNetwork.Node(
                DISRUPTED, listOf(DELAY, CLOSED),
                mapOf(
                    listOf(true, true) to 0.97,
                    listOf(true, false) to 0.60,
                    listOf(false, true) to 0.90,
                    listOf(false, false) to 0.03,
                ),
            ),
        )

    data class StopEvidence(
        val rainProbability: Double,
        val peakHour: Boolean,
        val travelMinutes: Int,
        val hoursEstimated: Boolean,
    )

    data class Assessment(
        val pDisrupted: Double,
        val pDelay: Double,
        val pClosed: Double,
        /** Diagnostic reasoning: given the stop WAS disrupted, how likely was rain the cause? */
        val pRainGivenDisrupted: Double,
    ) {
        val level: String
            get() = when {
                pDisrupted < 0.2 -> "Low"
                pDisrupted < 0.45 -> "Medium"
                else -> "High"
            }
    }

    fun assess(e: StopEvidence): Assessment {
        val net = network(e.rainProbability)
        val evidence = mapOf(
            PEAK_HOUR to e.peakHour,
            LONG_LEG to (e.travelMinutes > 20),
            HOURS_ESTIMATED to e.hoursEstimated,
        )
        return Assessment(
            pDisrupted = net.probability(DISRUPTED, evidence),
            pDelay = net.probability(DELAY, evidence),
            pClosed = net.probability(CLOSED, evidence),
            pRainGivenDisrupted = net.probability(RAIN, evidence + (DISRUPTED to true)),
        )
    }
}
