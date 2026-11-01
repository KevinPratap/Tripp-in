package com.trippin.intelligence

import com.trippin.intelligence.group.Expense
import com.trippin.intelligence.group.ExpenseSplit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseSplitTest {

    private fun e(id: String, payer: String, amount: Long, people: List<String>, cur: String = "INR") = Expense(id, payer, amount, cur, people)

    @Test fun `equal split among three leaves payer owed two thirds`() {
        val b = ExpenseSplit.balances(listOf(e("1", "a", 3000, listOf("a", "b", "c"))))["INR"]!!
        assertEquals(2000L, b["a"]); assertEquals(-1000L, b["b"]); assertEquals(-1000L, b["c"])
    }

    @Test fun `leftover minor units are shared out and the total still balances`() {
        val b = ExpenseSplit.balances(listOf(e("1", "a", 1000, listOf("a", "b", "c"))))["INR"]!!
        assertEquals(0L, b.values.sum())
        assertEquals(-334L, b["a"]!! - 1000L)   // a pays 334: first in id order takes the extra unit
        assertEquals(-333L, b["b"]); assertEquals(-333L, b["c"])
    }

    @Test fun `payer who did not take part is owed the whole amount`() {
        val b = ExpenseSplit.balances(listOf(e("1", "a", 2000, listOf("b", "c"))))["INR"]!!
        assertEquals(2000L, b["a"]); assertEquals(-1000L, b["b"]); assertEquals(-1000L, b["c"])
    }

    @Test fun `currencies are never mixed`() {
        val b = ExpenseSplit.balances(listOf(e("1", "a", 1000, listOf("a", "b")), e("2", "b", 500, listOf("a", "b"), "EUR")))
        assertEquals(setOf("INR", "EUR"), b.keys)
        assertEquals(500L, b["INR"]!!["a"]); assertEquals(250L, b["EUR"]!!["b"])
    }

    @Test fun `empty splits and non positive amounts are ignored`() {
        assertTrue(ExpenseSplit.balances(listOf(e("1", "a", 100, emptyList()), e("2", "a", 0, listOf("a")), e("3", "a", -5, listOf("a")))).isEmpty())
    }

    @Test fun `settling brings everyone to zero`() {
        val ex = listOf(
            e("1", "a", 9000, listOf("a", "b", "c")),
            e("2", "b", 3000, listOf("a", "b", "c")),
            e("3", "c", 600, listOf("a", "c")),
        )
        val bal = ExpenseSplit.balances(ex)
        val net = HashMap(bal["INR"]!!)
        ExpenseSplit.settle(bal).forEach { t ->
            net[t.from] = net.getValue(t.from) + t.amountMinor
            net[t.to] = net.getValue(t.to) - t.amountMinor
        }
        assertTrue(net.values.all { it == 0L })
    }

    @Test fun `simple debt is one transfer`() {
        val t = ExpenseSplit.settle(ExpenseSplit.balances(listOf(e("1", "a", 2000, listOf("a", "b")))))
        assertEquals(1, t.size)
        assertEquals("b", t[0].from); assertEquals("a", t[0].to); assertEquals(1000L, t[0].amountMinor)
    }

    @Test fun `nobody owes anything when everyone paid their share`() {
        val ex = listOf(e("1", "a", 1000, listOf("a", "b")), e("2", "b", 1000, listOf("a", "b")))
        assertTrue(ExpenseSplit.settle(ExpenseSplit.balances(ex)).isEmpty())
    }

    @Test fun `totals are per currency`() {
        val t = ExpenseSplit.totals(listOf(e("1", "a", 100, listOf("a")), e("2", "a", 50, listOf("a")), e("3", "a", 7, listOf("a"), "USD")))
        assertEquals(150L, t["INR"]); assertEquals(7L, t["USD"])
    }

    @Test fun `parsing amounts`() {
        assertEquals(1250L, ExpenseSplit.toMinor("12.50", "INR"))
        assertEquals(125000L, ExpenseSplit.toMinor("1,250", "INR"))
        assertEquals(1250L, ExpenseSplit.toMinor("1250", "JPY"))
        assertEquals(1L, ExpenseSplit.toMinor("0.005", "INR"))
        assertNull(ExpenseSplit.toMinor("", "INR")); assertNull(ExpenseSplit.toMinor("abc", "INR"))
        assertNull(ExpenseSplit.toMinor("0", "INR")); assertNull(ExpenseSplit.toMinor("-4", "INR"))
    }

    @Test fun `formatting amounts`() {
        assertEquals("INR 1,250.00", ExpenseSplit.format(125000, "INR"))
        assertEquals("EUR 0.05", ExpenseSplit.format(5, "EUR"))
        assertEquals("JPY 12,500", ExpenseSplit.format(12500, "JPY"))
        assertEquals("INR 1,234,567.89", ExpenseSplit.format(123456789, "INR"))
    }
}
