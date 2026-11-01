package com.trippin.feature.budget

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trippin.core.design.CardListSkeleton
import com.trippin.core.design.MessageState
import com.trippin.core.design.PillTone
import com.trippin.core.design.SectionLabel
import com.trippin.core.design.StatusPill
import com.trippin.core.design.SurfaceTier
import com.trippin.core.design.TrippinButton
import com.trippin.core.design.TrippinCard
import com.trippin.core.design.TrippinChoiceChip
import com.trippin.core.design.TrippinIconButton
import com.trippin.core.design.TrippinScaffold
import com.trippin.core.design.TrippinTextField
import com.trippin.core.design.TrippinTheme
import com.trippin.core.design.TrippinTopBar
import com.trippin.core.design.TrippinType
import com.trippin.core.design.formatStatedAmount
import com.trippin.core.network.ExpenseDto
import com.trippin.core.network.ExpenseOverviewDto
import com.trippin.feature.home.cityName
import com.trippin.feature.home.parseIsoDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rowDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(
    onBack: () -> Unit,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = TrippinTheme.colors
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf<ExpenseDto?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val place = state.details?.trip?.destination?.let(::cityName)
    val currency = state.currency
    val subtitle = listOfNotNull(place, currency?.let { "amounts in $it" }).joinToString(" · ").ifBlank { null }

    TrippinScaffold(
        topBar = { TrippinTopBar(title = "Budget", subtitle = subtitle, onBack = onBack) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.weight(1f)
            ) {
                val overview = state.overview
                when {
                    overview == null && state.loading -> CardListSkeleton(label = "Loading the budget")
                    overview == null -> MessageState(
                        icon = Icons.Default.Payments,
                        title = "Could not load the budget",
                        body = state.loadError ?: "Connect and try again.",
                        actionLabel = "Try again",
                        onAction = { viewModel.refresh() }
                    )
                    else -> Ledger(overview, state, onDelete = { confirmDelete = it })
                }
            }
            Box(
                Modifier.fillMaxWidth().background(colors.paper).padding(horizontal = 16.dp, vertical = 12.dp).navigationBarsPadding()
            ) {
                TrippinButton(text = "Add expense", onClick = viewModel::openForm, leadingIcon = Icons.Default.Add)
            }
        }
    }

    if (state.formOpen) {
        AddExpenseDialog(state, viewModel)
    }

    confirmDelete?.let { expense ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Remove ${expense.title}?", style = TrippinType.Heading, color = colors.ink) },
            text = { Text("It comes off the total and out of who owes whom, for everyone on the trip.", style = TrippinType.Body, color = colors.inkMuted) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(expense.id); confirmDelete = null }) {
                    Text("Remove", style = TrippinType.Label, color = colors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Keep", style = TrippinType.Label, color = colors.ink) }
            },
            containerColor = colors.panel
        )
    }
}

@Composable
private fun Ledger(overview: ExpenseOverviewDto, state: BudgetUiState, onDelete: (ExpenseDto) -> Unit) {
    val colors = TrippinTheme.colors
    val headline = overview.currency.takeIf { it.isNotBlank() && it != "UNSPECIFIED" } ?: state.currency
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            TrippinCard(tier = SurfaceTier.RAISED) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("LOGGED SO FAR", style = TrippinType.Eyebrow, color = colors.inkMuted)
                    if (overview.expenses.isEmpty()) {
                        Text("Nothing yet", style = TrippinType.Display, color = colors.ink)
                        Text("Log what the group spends and Tripp'in works out who owes whom.", style = TrippinType.Body, color = colors.inkMuted)
                    } else {
                        Text(
                            formatStatedAmount(overview.totalSpent, headline),
                            style = TrippinType.Numeric.copy(fontSize = 46.sp, lineHeight = 48.sp),
                            color = colors.ink
                        )
                        val count = overview.expenses.size
                        Text("$count ${if (count == 1) "expense" else "expenses"}", style = TrippinType.Label, color = colors.inkMuted)
                    }
                    state.estimate?.let { estimate ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "The plan's stops come to about ${formatStatedAmount(estimate, state.currency)}",
                                style = TrippinType.Caption,
                                color = colors.inkMuted,
                                modifier = Modifier.weight(1f)
                            )
                            StatusPill("Estimated", PillTone.WARN)
                        }
                    }
                    if (overview.mixedCurrencies) {
                        val others = overview.totalsByCurrency.filter { it.currency != overview.currency }
                            .joinToString(", ") { formatStatedAmount(it.amount, it.currency) }
                        Text(
                            "Also logged: $others. Different currencies are never added together.",
                            style = TrippinType.Caption,
                            color = colors.warn
                        )
                    }
                }
            }
        }

        if (overview.expenses.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel("Settle up", Modifier.padding(horizontal = 4.dp))
                    TrippinCard {
                        Column {
                            if (overview.settlements.isEmpty()) {
                                Text("Everyone is square.", style = TrippinType.Body, color = colors.ink, modifier = Modifier.padding(16.dp))
                            }
                            overview.settlements.forEachIndexed { i, s ->
                                if (i > 0) Divider()
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${s.from} pays ${s.to}", style = TrippinType.Heading, color = colors.ink, modifier = Modifier.weight(1f))
                                    Text(formatStatedAmount(s.amount, s.currency), style = TrippinType.Heading, color = colors.ink)
                                }
                            }
                        }
                    }
                }
            }
            item { SectionLabel("Every expense", Modifier.padding(horizontal = 4.dp)) }
            item {
                TrippinCard {
                    Column {
                        overview.expenses.forEachIndexed { i, e ->
                            if (i > 0) Divider()
                            ExpenseRow(e, onDelete = { onDelete(e) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseDto, onDelete: () -> Unit) {
    val colors = TrippinTheme.colors
    val ways = expense.splitBetween.size
    val date = parseIsoDate(expense.createdAt)?.format(rowDate)
    val detail = listOfNotNull(
        "${expense.paidBy} paid",
        if (ways > 1) "split $ways ways" else null,
        date
    ).joinToString(" · ")
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(expense.title, style = TrippinType.Heading, color = colors.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = TrippinType.Caption, color = colors.inkMuted)
        }
        Text(formatStatedAmount(expense.amount, expense.currency), style = TrippinType.Heading, color = colors.ink)
        TrippinIconButton(Icons.Default.DeleteOutline, "Remove ${expense.title}", onClick = onDelete, tint = colors.inkMuted)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(1.dp).background(TrippinTheme.colors.hairline))
}

@Composable
private fun AddExpenseDialog(state: BudgetUiState, viewModel: BudgetViewModel) {
    val colors = TrippinTheme.colors
    val draft = state.draft
    AlertDialog(
        onDismissRequest = { if (!state.saving) viewModel.closeForm() },
        title = { Text("Add expense", style = TrippinType.Title, color = colors.ink) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TrippinTextField(
                    value = draft.title,
                    onValueChange = { v -> viewModel.editDraft { it.copy(title = v) } },
                    label = "What for",
                    placeholder = "Dinner, tickets, taxi"
                )
                TrippinTextField(
                    value = draft.amount,
                    onValueChange = { v -> viewModel.editDraft { it.copy(amount = v) } },
                    label = state.currency?.let { "Amount in $it" } ?: "Amount",
                    keyboardType = KeyboardType.Decimal
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Paid by", style = TrippinType.Label, color = colors.ink)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.payers.forEach { name ->
                            TrippinChoiceChip(name, selected = draft.paidBy == name) {
                                viewModel.editDraft { it.copy(paidBy = name) }
                            }
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Split between", style = TrippinType.Label, color = colors.ink)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.payers.forEach { name ->
                            val on = name in draft.splitBetween
                            TrippinChoiceChip(name, selected = on) {
                                viewModel.editDraft { d -> d.copy(splitBetween = if (on) d.splitBetween - name else d.splitBetween + name) }
                            }
                        }
                    }
                }
                state.formError?.let { Text(it, style = TrippinType.Caption, color = colors.danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = viewModel::save, enabled = !state.saving) {
                Text(if (state.saving) "Saving" else "Log it", style = TrippinType.Label, color = colors.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = viewModel::closeForm, enabled = !state.saving) {
                Text("Cancel", style = TrippinType.Label, color = colors.ink)
            }
        },
        containerColor = colors.panel
    )
}
