package com.trippin.intelligence.bayes

/**
 * IS-II Unit 1 — Uncertain knowledge and reasoning.
 *
 * A small, general Bayesian network over boolean variables, with exact inference by
 * enumeration (Russell & Norvig, ENUMERATION-ASK). Nodes must be added in topological
 * order: every parent before its children.
 */
class BayesianNetwork {

    /**
     * @param cpt P(node = true | parents), keyed by the parents' values in [parents] order.
     *            A root node has a single entry keyed by the empty list (its prior).
     */
    data class Node(val name: String, val parents: List<String>, val cpt: Map<List<Boolean>, Double>)

    private val nodes = LinkedHashMap<String, Node>()

    val variables: List<String> get() = nodes.keys.toList()

    fun addRoot(name: String, pTrue: Double): BayesianNetwork =
        add(Node(name, emptyList(), mapOf(emptyList<Boolean>() to pTrue)))

    fun add(node: Node): BayesianNetwork {
        require(node.name !in nodes) { "Duplicate node ${node.name}" }
        node.parents.forEach { require(it in nodes) { "Parent $it of ${node.name} must be added first" } }
        require(node.cpt.size == 1 shl node.parents.size) {
            "${node.name} needs ${1 shl node.parents.size} CPT rows, got ${node.cpt.size}"
        }
        node.cpt.values.forEach { require(it in 0.0..1.0) { "Probabilities must be in [0,1]" } }
        nodes[node.name] = node
        return this
    }

    /** P(node = value | its parents' values in [assignment]). */
    private fun conditional(node: Node, value: Boolean, assignment: Map<String, Boolean>): Double {
        val key = node.parents.map { assignment.getValue(it) }
        val pTrue = node.cpt.getValue(key)
        return if (value) pTrue else 1.0 - pTrue
    }

    /** Joint probability of a complete assignment (chain rule over the network). */
    fun joint(assignment: Map<String, Boolean>): Double =
        nodes.values.fold(1.0) { acc, n -> acc * conditional(n, assignment.getValue(n.name), assignment) }

    private fun enumerateAll(vars: List<String>, evidence: Map<String, Boolean>): Double {
        if (vars.isEmpty()) return 1.0
        val y = nodes.getValue(vars.first())
        val rest = vars.drop(1)
        val known = evidence[y.name]
        return if (known != null) {
            conditional(y, known, evidence) * enumerateAll(rest, evidence)
        } else {
            listOf(true, false).sumOf { v ->
                conditional(y, v, evidence) * enumerateAll(rest, evidence + (y.name to v))
            }
        }
    }

    /** P(query = true | evidence), exact. */
    fun probability(query: String, evidence: Map<String, Boolean> = emptyMap()): Double {
        require(query in nodes) { "Unknown variable $query" }
        evidence[query]?.let { return if (it) 1.0 else 0.0 }
        val pTrue = enumerateAll(variables, evidence + (query to true))
        val pFalse = enumerateAll(variables, evidence + (query to false))
        return pTrue / (pTrue + pFalse)
    }
}
