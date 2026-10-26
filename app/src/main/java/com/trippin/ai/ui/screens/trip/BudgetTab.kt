package com.trippin.ai.ui.screens.trip

import androidx.compose.foundation.layout.Arrangement
import com.trippin.ai.ui.brand.Art
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trippin.ai.data.model.TripExpense
import com.trippin.ai.ui.components.Avatar
import com.trippin.ai.ui.components.EmptyState
import com.trippin.ai.ui.components.Kicker
import com.trippin.ai.ui.components.Panel
import com.trippin.ai.ui.components.SectionTitle
import com.trippin.ai.ui.components.SignalButton
import com.trippin.ai.ui.components.SmallButton
import com.trippin.ai.ui.components.TextInput
import com.trippin.ai.ui.navigation.LocalSnack
import com.trippin.ai.ui.theme.Trip
import com.trippin.intelligence.group.Expense
import com.trippin.intelligence.group.ExpenseSplit

/** Currency that fits the destination. Left blank when we are not sure, never guessed across countries. */
private fun currencyFor(country: String?): String {
    val c = country?.trim()?.lowercase().orEmpty()
    return when {
        c in setOf("united states", "united states of america", "usa", "us") -> "USD"
        c in setOf("united kingdom", "uk", "england", "scotland", "wales", "great britain") -> "GBP"
        c in setOf("japan") -> "JPY"
        c in setOf("india") -> "INR"
        c in setOf("thailand") -> "THB"
        c in setOf("australia") -> "AUD"
        c in setOf("canada") -> "CAD"
        c in setOf("switzerland") -> "CHF"
        c in setOf("singapore") -> "SGD"
        c in setOf("united arab emirates", "uae") -> "AED"
        c in setOf("indonesia") -> "IDR"
        c in setOf(
            "france", "germany", "italy", "spain", "portugal", "netherlands", "belgium", "austria",
            "ireland", "greece", "finland", "croatia", "slovenia", "slovakia", "estonia", "latvia", "lithuania",
        ) -> "EUR"
        else -> ""
    }
}

/** Shared costs for the trip: add an expense, see who owes whom, in each currency separately. */
@Composable
fun BudgetTab(vm: TripViewModel, ui: TripUi) {
    val trip = ui.trip ?: return
    val snack = LocalSnack.current
    val nameOf = { uid: String -> ui.members.firstOrNull { it.uid == uid }?.name ?: "Someone" }
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember(trip.id) { mutableStateOf(currencyFor(trip.destination?.country)) }
    var splitWith by remember(ui.members.size) { mutableStateOf(ui.members.map { it.uid }.toSet()) }

    val expenses = ui.expenses.map { Expense(it.id, it.paidBy, it.amountMinor, it.currency, it.splitAmong) }
    val balances = ExpenseSplit.balances(expenses)
    val transfers = ExpenseSplit.settle(balances)
    val totals = ExpenseSplit.totals(expenses)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Panel {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Kicker("Add an expense")
                    TextInput(title, { title = it }, "What was it for")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextInput(
                            amount, { amount = it }, "Amount", Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                        TextInput(currency, { currency = it.uppercase().take(3) }, "Currency", Modifier.width(110.dp))
                    }
                    Text("Split equally between", style = MaterialTheme.typography.labelLarge, color = Trip.Muted)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ui.members.forEach { m ->
                            val on = m.uid in splitWith
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Avatar(m.name, m.uid, 28.dp)
                                Text(m.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                SmallButton(if (on) "In" else "Out", filled = on, onClick = {
                                    splitWith = if (on) splitWith - m.uid else splitWith + m.uid
                                })
                            }
                        }
                    }
                    SignalButton("Add expense", onClick = {
                        val cur = currency.trim().uppercase()
                        val minor = ExpenseSplit.toMinor(amount, cur)
                        when {
                            title.isBlank() -> snack("Say what the expense was for")
                            cur.length != 3 -> snack("Enter a 3 letter currency code, like EUR")
                            minor == null || minor <= 0L -> snack("Enter an amount above zero")
                            splitWith.isEmpty() -> snack("Pick at least one person to split with")
                            else -> {
                                vm.addExpense(title, minor, cur, splitWith.toList())
                                title = ""; amount = ""
                            }
                        }
                    }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (expenses.isEmpty()) {
            item { EmptyState("No expenses yet", "Add the hotel, the train or a dinner and Trippin works out who owes whom.", art = Art.SPLIT) }
        } else {
            item {
                SectionTitle("Settle up")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (transfers.isEmpty()) Text("Everyone is square.", style = MaterialTheme.typography.bodyLarge, color = Trip.Muted)
                    transfers.forEach { t ->
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${nameOf(t.from)} pays ${nameOf(t.to)}", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Text(ExpenseSplit.format(t.amountMinor, t.currency), style = MaterialTheme.typography.titleMedium, color = Trip.Signal)
                            }
                        }
                    }
                    totals.forEach { (cur, total) ->
                        Text("Total spent in $cur: ${ExpenseSplit.format(total, cur)}", style = MaterialTheme.typography.labelLarge, color = Trip.Muted)
                    }
                }
            }
            item { SectionTitle("Expenses") }
            items(ui.expenses, key = { it.id }) { e -> ExpenseRow(e, nameOf, canDelete = e.paidBy == vm.me.uid || vm.canManage, onDelete = { vm.deleteExpense(e) }) }
        }
    }
}

@Composable
private fun ExpenseRow(e: TripExpense, nameOf: (String) -> String, canDelete: Boolean, onDelete: () -> Unit) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(e.paidByName, e.paidBy, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${nameOf(e.paidBy)} paid, split ${e.splitAmong.size} ${if (e.splitAmong.size == 1) "way" else "ways"}",
                    style = MaterialTheme.typography.bodyMedium, color = Trip.Muted,
                )
            }
            Text(ExpenseSplit.format(e.amountMinor, e.currency), style = MaterialTheme.typography.titleMedium)
            if (canDelete) SmallButton("Remove", onClick = onDelete)
        }
    }
}
