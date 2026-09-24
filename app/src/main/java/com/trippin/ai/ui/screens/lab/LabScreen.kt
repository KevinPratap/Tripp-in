package com.trippin.ai.ui.screens.lab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trippin.ai.AppContainer
import com.trippin.ai.ui.components.EvolutionChart
import com.trippin.ai.ui.components.FuzzyChart
import com.trippin.ai.ui.components.GhostButton
import com.trippin.ai.ui.components.Headline
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.ProbabilityBar
import com.trippin.ai.ui.components.QHeatmap
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.theme.Mono
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.bayes.StopRiskModel
import com.trippin.intelligence.fuzzy.FatigueController
import com.trippin.intelligence.genetic.RouteOptimizer
import com.trippin.intelligence.model.Category
import com.trippin.intelligence.model.SampleData
import com.trippin.intelligence.model.asClock
import com.trippin.intelligence.rl.QLearningRecommender
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LabViewModel(private val container: AppContainer) : ViewModel() {
    val gaResult = MutableStateFlow<RouteOptimizer.Result?>(null)
    val evolving = MutableStateFlow(false)
    val qTable = container.learningRepository.table
    val feedbackCount = container.learningRepository.feedbackCount()
    val epsilon = MutableStateFlow(0.3)

    init { refreshEpsilon() }

    private fun refreshEpsilon() = viewModelScope.launch { epsilon.value = container.learningRepository.epsilon() }

    fun evolve(population: Int, generations: Int, mutation: Double) = viewModelScope.launch {
        evolving.value = true
        gaResult.value = withContext(Dispatchers.Default) {
            RouteOptimizer(RouteOptimizer.Params(populationSize = population, generations = generations, mutationRate = mutation, seed = (0..9999).random()))
                .optimise(SampleData.hotel, SampleData.mumbai, dayStart = 9 * 60)
        }
        evolving.value = false
    }

    fun train() = viewModelScope.launch { container.learningRepository.simulate(200); refreshEpsilon() }
    fun reset() = viewModelScope.launch { container.learningRepository.reset(); refreshEpsilon() }
}

private enum class LabTab(val title: String, val unit: String) {
    BAYES("Bayes", "Unit 1 · Uncertain knowledge & reasoning"),
    FUZZY("Fuzzy", "Unit 2 · Fuzzy inference systems"),
    GA("Genetic", "Unit 3 · Evolutionary intelligence"),
    RL("Q-learn", "Unit 4 · Reinforcement learning"),
}

@Composable
fun LabScreen(container: AppContainer) {
    val vm: LabViewModel = viewModel { LabViewModel(container) }
    var tab by rememberSaveable { mutableStateOf(LabTab.BAYES) }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Kicker("Intelligent Systems II · live")
        Headline("AI Lab")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LabTab.entries.forEach { t ->
                val on = t == tab
                Text(
                    t.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (on) Trip.Paper else Trip.Ink,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if (on) Trip.Ink else Trip.Card)
                        .border(2.dp, Trip.Ink, RoundedCornerShape(14.dp))
                        .selectable(selected = on, role = Role.Tab) { tab = t }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        Kicker(tab.unit, color = Trip.Signal)
        when (tab) {
            LabTab.BAYES -> BayesLab()
            LabTab.FUZZY -> FuzzyLab()
            LabTab.GA -> GeneticLab(vm)
            LabTab.RL -> RlLab(vm)
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Trip.Card).border(2.dp, Trip.Ink, RoundedCornerShape(24.dp)).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

@Composable
private fun LabSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = Trip.Ink, activeTrackColor = Trip.Signal, inactiveTrackColor = Trip.PaperDeep),
        )
    }
}

@Composable
private fun LabSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Trip.Signal, checkedThumbColor = Trip.Ink))
    }
}

@Composable
private fun BayesLab() {
    var rain by remember { mutableStateOf(0.4f) }
    var peak by remember { mutableStateOf(false) }
    var long by remember { mutableStateOf(false) }
    var estimated by remember { mutableStateOf(false) }
    val a = StopRiskModel.assess(StopRiskModel.StopEvidence(rain.toDouble(), peak, if (long) 30 else 10, estimated))

    Card {
        Text(
            "Rain ─┐\nPeak ─┼─► Delay ─┐\nLong ─┘          ├─► Disrupted\nEstimated ─► Closed ┘",
            fontFamily = Mono, style = MaterialTheme.typography.bodyMedium,
        )
        Text("Exact inference by enumeration over all hidden variables.", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
    }
    Card {
        LabSlider("Forecast rain · ${(rain * 100).toInt()}% (prior)", rain, 0f..1f) { rain = it }
        LabSwitch("Arrive at peak hour", peak) { peak = it }
        LabSwitch("Long leg (> 20 min)", long) { long = it }
        LabSwitch("Opening hours estimated", estimated) { estimated = it }
    }
    Card {
        ProbabilityBar("P(Disrupted | evidence)", a.pDisrupted, Trip.Signal)
        ProbabilityBar("P(Delay | evidence)", a.pDelay)
        ProbabilityBar("P(Closed | evidence)", a.pClosed)
        ProbabilityBar("P(Rain | Disrupted) — diagnostic", a.pRainGivenDisrupted, Trip.Cobalt)
        Text("Risk level: ${a.level}", style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
private fun FuzzyLab() {
    var walked by remember { mutableStateOf(6f) }
    var hours by remember { mutableStateOf(5f) }
    var temp by remember { mutableStateOf(30f) }
    val advice = FatigueController.advise(walked.toDouble(), hours.toDouble(), temp.toDouble())

    Card {
        LabSlider("Walked · ${"%.1f".format(walked)} km", walked, 0f..15f) { walked = it }
        LabSlider("On the go · ${"%.1f".format(hours)} h", hours, 0f..12f) { hours = it }
        LabSlider("Temperature · ${temp.toInt()}°C", temp, 0f..45f) { temp = it }
    }
    Card {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${advice.score.toInt()}", style = MaterialTheme.typography.displayLarge)
            Text(advice.label.uppercase(), style = MaterialTheme.typography.headlineMedium, color = Trip.Signal, modifier = Modifier.padding(bottom = 12.dp))
        }
        FuzzyChart(
            terms = FatigueController.fatigue.terms.values.map { mf -> (0..100 step 2).map { x -> x.toDouble() to mf.degree(x.toDouble()) } },
            aggregated = advice.detail.aggregated, centroid = advice.score, range = 0.0..100.0,
        )
        Text("Mamdani: min for AND, max for OR, clip by min, aggregate by max, centroid defuzzification (blue line).", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
    }
    Card {
        Kicker("Rules fired", color = Trip.Ink)
        FatigueController.rules.forEachIndexed { i, r ->
            ProbabilityBar(r.toString(), advice.detail.ruleStrengths[i], if (advice.detail.ruleStrengths[i] > 0) Trip.Ink else Trip.PaperDeep)
        }
    }
}

@Composable
private fun GeneticLab(vm: LabViewModel) {
    val result by vm.gaResult.collectAsStateWithLifecycle()
    val busy by vm.evolving.collectAsStateWithLifecycle()
    var population by remember { mutableIntStateOf(60) }
    var generations by remember { mutableIntStateOf(150) }
    var mutation by remember { mutableStateOf(0.2f) }

    Card {
        Text("Order all ${SampleData.mumbai.size} Mumbai stops from Churchgate, respecting opening hours (TSP with time windows).", style = MaterialTheme.typography.bodyMedium)
        LabSlider("Population · $population", population.toFloat(), 10f..200f) { population = it.toInt() }
        LabSlider("Generations · $generations", generations.toFloat(), 10f..400f) { generations = it.toInt() }
        LabSlider("Mutation rate · ${"%.2f".format(mutation)}", mutation, 0f..1f) { mutation = it }
        SignalButton("Evolve", onClick = { vm.evolve(population, generations, mutation.toDouble()) }, loading = busy)
    }
    result?.let { r ->
        Card {
            EvolutionChart(r.history.map { it.bestCost }, r.history.map { it.averageCost })
            Text("${r.improvementPercent.toInt()}% cheaper than the input order", style = MaterialTheme.typography.titleLarge)
            Text("${"%.1f".format(r.best.km)} km · ${r.best.violationMinutes} min outside opening hours", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
        }
        Card {
            Kicker("Evolved route", color = Trip.Ink)
            r.best.schedule.forEachIndexed { i, s ->
                Text("${i + 1}. ${s.start.asClock()}  ${s.place.name}", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun RlLab(vm: LabViewModel) {
    val table by vm.qTable.collectAsStateWithLifecycle()
    val count by vm.feedbackCount.collectAsStateWithLifecycle(initialValue = 0)
    val eps by vm.epsilon.collectAsStateWithLifecycle()
    val rows = QLearningRecommender.TimeOfDay.entries.flatMap { t -> QLearningRecommender.Energy.entries.map { e -> "${t.name.take(3)}·${e.name.take(3)}" } }
    val cols = Category.entries.map { it.name.take(4) }

    Card {
        Text("State = time of day × energy. Action = kind of stop. Reward = your reaction in Today mode.", style = MaterialTheme.typography.bodyMedium)
        Text("Q(s,a) ← Q(s,a) + α[r + γ·max Q(s′,a′) − Q(s,a)]", fontFamily = Mono, style = MaterialTheme.typography.bodyMedium)
        Text("ε = ${"%.2f".format(eps)} · $count real feedback events", style = MaterialTheme.typography.labelMedium)
    }
    Card {
        if (table.isNotEmpty()) QHeatmap(table, rows, cols) else Text("Loading the Q-table…")
        Text("Orange = learned to like, blue = learned to avoid.", style = MaterialTheme.typography.bodyMedium, color = Trip.Muted)
    }
    SignalButton("Train 200 simulated episodes", onClick = { vm.train() })
    GhostButton("Reset what it learned", onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth())
}
