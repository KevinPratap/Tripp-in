package com.trippin.ai.data.repository

import com.trippin.ai.data.local.FeedbackEntity
import com.trippin.ai.data.local.LearningDao
import com.trippin.ai.data.local.QRowEntity
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.rl.QLearningRecommender
import com.trippin.intelligence.rl.QLearningRecommender.Feedback
import com.trippin.intelligence.rl.QLearningRecommender.State
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** Owns the Q-learning agent and keeps its table in Room so learning survives restarts. */
class LearningRepository(private val dao: LearningDao) {
    private val mutex = Mutex()
    private var agent: QLearningRecommender? = null

    private val _table = MutableStateFlow<List<List<Double>>>(emptyList())
    /** Current Q-table, rows = 9 states, columns = 6 categories. Drives the heatmap. */
    val table: StateFlow<List<List<Double>>> = _table

    fun feedbackCount(): Flow<Int> = dao.observeFeedbackCount()

    private suspend fun agent(): QLearningRecommender = mutex.withLock {
        agent ?: QLearningRecommender().also { a ->
            val rows = dao.qTable()
            if (rows.size == 9) a.load(rows.map { r -> r.qValues.split(',').map(String::toDouble) })
            agent = a
            _table.value = a.table()
        }
    }

    private suspend fun persist(a: QLearningRecommender) {
        val t = a.table()
        dao.saveRows(t.mapIndexed { i, row -> QRowEntity(i, row.joinToString(",")) })
        _table.value = t
    }

    suspend fun epsilon(): Double = agent().epsilon

    suspend fun suggest(state: State): List<Pair<Category, Double>> = agent().ranked(state)

    suspend fun choose(state: State): Category = agent().choose(state)

    suspend fun feedback(state: State, category: Category, feedback: Feedback, next: State?) {
        val a = agent()
        a.learn(state, category, feedback, next)
        dao.logFeedback(FeedbackEntity(stateIndex = state.index, category = category.name, feedback = feedback.name))
        persist(a)
    }

    /**
     * The traveller's chosen interests first, then the categories the agent has learned they like.
     * This is how RL feedback from past trips shapes the next plan.
     */
    suspend fun preferenceOrder(interests: List<Category>): List<Category> {
        if (interests.isEmpty()) return Category.entries
        val a = agent()
        val learned = Category.entries.associateWith { c -> a.table().sumOf { row -> row[c.ordinal] } }
        return interests.sortedByDescending { learned[it] ?: 0.0 }
    }

    /** Lab demo: trains on a simulated traveller so the heatmap visibly converges. */
    suspend fun simulate(episodes: Int, seed: Int = Random.nextInt()) {
        val a = agent()
        val rng = Random(seed)
        fun react(s: State, c: Category): Feedback = when {
            s.energy == QLearningRecommender.Energy.LOW -> if (c == Category.PARK) Feedback.LIKED else Feedback.SKIPPED
            s.time == QLearningRecommender.TimeOfDay.AFTERNOON -> if (c == Category.FOOD) Feedback.LIKED else Feedback.SKIPPED
            s.time == QLearningRecommender.TimeOfDay.MORNING -> if (c == Category.MUSEUM) Feedback.LIKED else Feedback.KEPT
            else -> if (c == Category.NIGHTLIFE) Feedback.LIKED else Feedback.KEPT
        }
        repeat(episodes) {
            val s = State(QLearningRecommender.TimeOfDay.entries.random(rng), QLearningRecommender.Energy.entries.random(rng))
            val c = a.choose(s)
            val next = State(QLearningRecommender.TimeOfDay.entries.random(rng), QLearningRecommender.Energy.entries.random(rng))
            a.learn(s, c, react(s, c), next)
        }
        persist(a)
    }

    suspend fun reset() = mutex.withLock {
        dao.clearQ()
        dao.clearFeedback()
        agent = QLearningRecommender()
        _table.value = agent!!.table()
    }
}
