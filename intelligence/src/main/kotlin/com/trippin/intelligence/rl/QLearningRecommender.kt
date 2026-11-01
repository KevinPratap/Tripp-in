package com.trippin.intelligence.rl

import com.trippin.intelligence.model.Category
import kotlin.random.Random

/**
 * IS-II Unit 4 — Reinforcement learning.
 *
 * Tabular Q-learning that learns which kind of stop a traveller wants next, from their own
 * reactions. The environment is the trip; the agent picks a category; the traveller's response
 * is the reward.
 *
 *   State  = (time of day, energy level)            → 3 × 3 = 9 states
 *   Action = the category of the next stop          → 6 actions
 *   Reward = +1 kept & liked, +0.3 kept, −1 skipped
 *   Update = Q(s,a) ← Q(s,a) + α [ r + γ · max_a' Q(s',a') − Q(s,a) ]
 *   Policy = ε-greedy, with ε decaying as the agent learns
 */
class QLearningRecommender(
    val alpha: Double = 0.3,
    val gamma: Double = 0.6,
    epsilon: Double = 0.3,
    private val minEpsilon: Double = 0.05,
    private val epsilonDecay: Double = 0.995,
    private val rng: Random = Random(7),
) {
    enum class TimeOfDay { MORNING, AFTERNOON, EVENING }
    enum class Energy { LOW, MEDIUM, HIGH }

    data class State(val time: TimeOfDay, val energy: Energy) {
        val index: Int get() = time.ordinal * Energy.entries.size + energy.ordinal
    }

    enum class Feedback(val reward: Double) { LIKED(1.0), KEPT(0.3), SKIPPED(-1.0) }

    var epsilon: Double = epsilon
        private set
    var updates: Int = 0
        private set

    private val actions = Category.entries
    private val q = Array(TimeOfDay.entries.size * Energy.entries.size) { DoubleArray(actions.size) }

    fun qValue(s: State, a: Category): Double = q[s.index][a.ordinal]

    /** Copy of the whole table, rows = states, columns = categories. Drawn as a heatmap in the app. */
    fun table(): List<List<Double>> = q.map { it.toList() }

    fun bestAction(s: State): Category = actions.maxBy { q[s.index][it.ordinal] }

    /** ε-greedy: explore a random category with probability ε, otherwise exploit the best one. */
    fun choose(s: State): Category =
        if (rng.nextDouble() < epsilon) actions[rng.nextInt(actions.size)] else bestAction(s)

    /** Ranked suggestions for the UI, best first. */
    fun ranked(s: State): List<Pair<Category, Double>> =
        actions.map { it to q[s.index][it.ordinal] }.sortedByDescending { it.second }

    fun learn(s: State, a: Category, feedback: Feedback, next: State?) {
        val row = q[s.index]
        val future = next?.let { q[it.index].max() } ?: 0.0
        row[a.ordinal] += alpha * (feedback.reward + gamma * future - row[a.ordinal])
        updates++
        epsilon = maxOf(minEpsilon, epsilon * epsilonDecay)
    }

    /** Restores a table saved in Room, so learning carries across app launches. */
    fun load(values: List<List<Double>>) {
        require(values.size == q.size && values.all { it.size == actions.size })
        values.forEachIndexed { i, row -> row.forEachIndexed { j, v -> q[i][j] = v } }
    }

    companion object {
        fun stateFor(minuteOfDay: Int, fatigueScore: Double): State {
            val time = when {
                minuteOfDay < 12 * 60 -> TimeOfDay.MORNING
                minuteOfDay < 17 * 60 -> TimeOfDay.AFTERNOON
                else -> TimeOfDay.EVENING
            }
            val energy = when {
                fatigueScore >= 65 -> Energy.LOW
                fatigueScore >= 40 -> Energy.MEDIUM
                else -> Energy.HIGH
            }
            return State(time, energy)
        }
    }
}
