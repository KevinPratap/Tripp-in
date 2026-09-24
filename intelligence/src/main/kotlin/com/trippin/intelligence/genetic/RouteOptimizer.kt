package com.trippin.intelligence.genetic

import com.trippin.intelligence.model.Geo
import com.trippin.intelligence.model.Place
import kotlin.random.Random

/**
 * IS-II Unit 3 — Evolutionary intelligence.
 *
 * Orders a day's stops with a genetic algorithm. This is the travelling-salesman problem with
 * time windows: the shortest route is not enough, because each place also has opening hours.
 *
 * Encoding: a chromosome is a permutation of stop indices.
 * Fitness: 1 / (1 + cost), where cost = distance + penalties for closed-door arrivals and waiting.
 * Operators: tournament selection, order crossover (OX1), swap + inversion mutation, elitism.
 */
class RouteOptimizer(private val params: Params = Params()) {

    data class Params(
        val populationSize: Int = 60,
        val generations: Int = 150,
        val tournamentSize: Int = 3,
        val crossoverRate: Double = 0.9,
        val mutationRate: Double = 0.2,
        val elitism: Int = 2,
        val seed: Int = 42,
        /** Cost units per km travelled. */
        val kmWeight: Double = 1.0,
        /** Cost units per minute spent outside a place's opening hours. */
        val closedPenalty: Double = 0.5,
        /** Cost units per minute waiting for a door to open. */
        val waitPenalty: Double = 0.02,
    )

    data class ScheduledStop(val place: Place, val travelMinutes: Int, val arrive: Int, val start: Int, val leave: Int, val violationMinutes: Int)

    data class Evaluation(val order: List<Int>, val cost: Double, val km: Double, val violationMinutes: Int, val schedule: List<ScheduledStop>) {
        val fitness: Double get() = 1.0 / (1.0 + cost)
    }

    data class Generation(val index: Int, val bestCost: Double, val averageCost: Double)

    data class Result(val best: Evaluation, val initial: Evaluation, val history: List<Generation>) {
        /** How much cheaper the evolved route is than the order the stops came in. */
        val improvementPercent: Double
            get() = if (initial.cost == 0.0) 0.0 else (initial.cost - best.cost) / initial.cost * 100
    }

    fun evaluate(start: Place, stops: List<Place>, order: List<Int>, dayStart: Int): Evaluation {
        var time = dayStart
        var here = start
        var km = 0.0
        var violation = 0
        var waiting = 0
        val schedule = ArrayList<ScheduledStop>(order.size)
        for (i in order) {
            val p = stops[i]
            val d = Geo.distanceKm(here, p)
            val travel = Geo.travelMinutes(d)
            val arrive = time + travel
            val begin = maxOf(arrive, p.opensAt)
            waiting += begin - arrive
            val leave = begin + p.visitMinutes
            val over = maxOf(0, leave - p.closesAt)
            violation += over
            km += d
            schedule += ScheduledStop(p, travel, arrive, begin, leave, over)
            time = leave
            here = p
        }
        val cost = km * params.kmWeight + violation * params.closedPenalty + waiting * params.waitPenalty
        return Evaluation(order, cost, km, violation, schedule)
    }

    fun optimise(start: Place, stops: List<Place>, dayStart: Int = 9 * 60): Result {
        val n = stops.size
        val identity = (0 until n).toList()
        val initial = evaluate(start, stops, identity, dayStart)
        if (n < 3) {
            val best = listOf(identity, identity.reversed()).map { evaluate(start, stops, it, dayStart) }.minBy { it.cost }
            return Result(best, initial, listOf(Generation(0, best.cost, best.cost)))
        }

        val rng = Random(params.seed)
        var population = List(params.populationSize) { i ->
            val genes = if (i == 0) identity else identity.shuffled(rng)
            evaluate(start, stops, genes, dayStart)
        }
        val history = ArrayList<Generation>(params.generations + 1)
        history += snapshot(0, population)

        repeat(params.generations) { g ->
            val sorted = population.sortedBy { it.cost }
            val next = ArrayList<Evaluation>(params.populationSize)
            next += sorted.take(params.elitism)
            while (next.size < params.populationSize) {
                val mum = tournament(sorted, rng)
                val dad = tournament(sorted, rng)
                var child = if (rng.nextDouble() < params.crossoverRate) orderCrossover(mum.order, dad.order, rng) else mum.order
                if (rng.nextDouble() < params.mutationRate) child = mutate(child, rng)
                next += evaluate(start, stops, child, dayStart)
            }
            population = next
            history += snapshot(g + 1, population)
        }
        return Result(population.minBy { it.cost }, initial, history)
    }

    private fun snapshot(index: Int, pop: List<Evaluation>) =
        Generation(index, pop.minOf { it.cost }, pop.sumOf { it.cost } / pop.size)

    private fun tournament(pop: List<Evaluation>, rng: Random): Evaluation =
        List(params.tournamentSize) { pop[rng.nextInt(pop.size)] }.minBy { it.cost }

    companion object {
        /**
         * Order crossover (OX1): copy a random slice from parent A, then fill the remaining
         * positions with parent B's genes in B's order, skipping ones already used. Always
         * produces a valid permutation.
         */
        fun orderCrossover(a: List<Int>, b: List<Int>, rng: Random): List<Int> {
            val n = a.size
            var i = rng.nextInt(n)
            var j = rng.nextInt(n)
            if (i > j) i = j.also { j = i }
            val child = arrayOfNulls<Int>(n)
            val used = HashSet<Int>()
            for (k in i..j) { child[k] = a[k]; used += a[k] }
            var pos = (j + 1) % n
            for (k in 0 until n) {
                val gene = b[(j + 1 + k) % n]
                if (gene in used) continue
                child[pos] = gene
                used += gene
                pos = (pos + 1) % n
            }
            return child.map { it!! }
        }

        /** Swap two stops, or reverse a stretch of the route (a 2-opt style move). */
        fun mutate(order: List<Int>, rng: Random): List<Int> {
            val m = order.toMutableList()
            var i = rng.nextInt(m.size)
            var j = rng.nextInt(m.size)
            if (rng.nextBoolean()) {
                m[i] = m[j].also { m[j] = m[i] }
            } else {
                if (i > j) i = j.also { j = i }
                m.subList(i, j + 1).reverse()
            }
            return m
        }
    }
}
