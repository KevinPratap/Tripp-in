package com.trippin.intelligence.group

import java.math.BigDecimal
import java.math.RoundingMode

/** One shared cost. Amounts are whole minor units (paise, cents) so there is no rounding drift. */
data class Expense(
    val id: String,
    val paidBy: String,
    val amountMinor: Long,
    val currency: String,
    /** Everyone who shares the cost equally, including the payer if they took part. */
    val splitAmong: List<String>,
)

data class Transfer(val from: String, val to: String, val amountMinor: Long, val currency: String)

/**
 * Splitting costs inside a group, and working out who pays whom. Currencies are never mixed:
 * every figure is kept per currency, because a trip can have a EUR hotel and a GBP train.
 * Pure Kotlin, unit-tested.
 */
object ExpenseSplit {

    /** Net position per currency per member. Positive means the member is owed money, negative means they owe. */
    fun balances(expenses: List<Expense>): Map<String, Map<String, Long>> {
        val out = HashMap<String, HashMap<String, Long>>()
        for (e in expenses) {
            val people = e.splitAmong.distinct().sorted()
            if (people.isEmpty() || e.amountMinor <= 0) continue
            val map = out.getOrPut(e.currency) { HashMap() }
            val base = e.amountMinor / people.size
            val extra = (e.amountMinor % people.size).toInt()
            // The leftover minor units go to the first few people in id order, so the split is deterministic.
            people.forEachIndexed { i, uid -> map[uid] = (map[uid] ?: 0L) - (base + if (i < extra) 1L else 0L) }
            map[e.paidBy] = (map[e.paidBy] ?: 0L) + e.amountMinor
        }
        return out.mapValues { it.value.toMap() }
    }

    /** Total spent per currency. */
    fun totals(expenses: List<Expense>): Map<String, Long> =
        expenses.filter { it.amountMinor > 0 }.groupBy { it.currency }.mapValues { (_, v) -> v.sumOf { it.amountMinor } }

    /**
     * Few transfers that settle everyone: the biggest debtor pays the biggest creditor, repeatedly.
     * After applying every transfer, every member is at zero.
     */
    fun settle(balances: Map<String, Map<String, Long>>): List<Transfer> {
        val result = ArrayList<Transfer>()
        for (currency in balances.keys.sorted()) {
            val net = balances.getValue(currency)
            val order = compareByDescending<Pair<String, Long>> { it.second }.thenBy { it.first }
            val creditors = net.filter { it.value > 0 }.map { it.key to it.value }.sortedWith(order).toMutableList()
            val debtors = net.filter { it.value < 0 }.map { it.key to -it.value }.sortedWith(order).toMutableList()
            var i = 0
            var j = 0
            while (i < creditors.size && j < debtors.size) {
                val pay = minOf(creditors[i].second, debtors[j].second)
                result += Transfer(debtors[j].first, creditors[i].first, pay, currency)
                creditors[i] = creditors[i].first to creditors[i].second - pay
                debtors[j] = debtors[j].first to debtors[j].second - pay
                if (creditors[i].second == 0L) i++
                if (debtors[j].second == 0L) j++
            }
        }
        return result
    }

    /** Digits after the decimal point for a currency. */
    fun decimals(currency: String): Int = if (currency.uppercase() in ZERO_DECIMAL) 0 else 2

    /** "12.50" becomes 1250 minor units. Returns null for anything that is not a positive amount. */
    fun toMinor(text: String, currency: String): Long? {
        val cleaned = text.trim().replace(",", "")
        if (cleaned.isEmpty()) return null
        val value = cleaned.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        val d = decimals(currency)
        val minor = value.setScale(d, RoundingMode.HALF_UP).movePointRight(d)
        if (minor.signum() <= 0 || minor > BigDecimal(Long.MAX_VALUE / 1000)) return null
        return minor.toLong()
    }

    /** 125000 INR becomes "INR 1,250.00". */
    fun format(minor: Long, currency: String): String {
        val d = decimals(currency)
        val plain = BigDecimal(minor).movePointLeft(d).setScale(d).toPlainString()
        val (whole, frac) = plain.split('.').let { it[0] to it.getOrNull(1) }
        val negative = whole.startsWith("-")
        val digits = whole.removePrefix("-")
        val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
        return currency.uppercase() + " " + (if (negative) "-" else "") + grouped + (frac?.let { ".$it" } ?: "")
    }

    private val ZERO_DECIMAL = setOf("JPY", "KRW", "VND")
}
