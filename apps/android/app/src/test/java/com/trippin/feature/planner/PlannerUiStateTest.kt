package com.trippin.feature.planner

import com.trippin.core.network.DestinationSuggestionDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The planner form's rules.
 *
 * These are the app's first unit tests. The state transitions are pure functions precisely so the rule
 * that decides a trip's currency can be checked here, without an emulator: that rule was the bug, since
 * the planner opened every trip in INR whatever the destination.
 */
class PlannerUiStateTest {

    private val lisbon = DestinationSuggestionDto(
        id = "osm:R:1",
        name = "Lisbon",
        region = "Lisbon",
        country = "Portugal",
        countryCode = "PT",
        currency = "EUR",
        latitude = 38.7,
        longitude = -9.1,
        label = "Lisbon, Portugal"
    )

    private val bangkok = lisbon.copy(
        id = "osm:R:2",
        name = "Bangkok",
        region = null,
        country = "Thailand",
        countryCode = "TH",
        currency = "THB",
        label = "Bangkok, Thailand"
    )

    /** Somewhere the server could not price, so it says so instead of guessing. */
    private val unpriced = lisbon.copy(
        id = "osm:N:3",
        name = "Queen Maud Land",
        country = null,
        countryCode = "AQ",
        currency = null,
        label = "Queen Maud Land"
    )

    private fun readyForm() = PlannerUiState(
        destination = "Lisbon, Portugal",
        startDate = LocalDate.now().plusDays(7),
        endDate = LocalDate.now().plusDays(10),
        currency = "EUR"
    )

    // ---- Currency ----

    @Test
    fun `starts with no currency rather than defaulting every trip to one country's money`() {
        assertNull(PlannerUiState().currency)
    }

    @Test
    fun `picking a destination sets the currency to that country's money`() {
        assertEquals("EUR", PlannerUiState().withSuggestionPicked(lisbon).currency)
        assertEquals("THB", PlannerUiState().withSuggestionPicked(bangkok).currency)
    }

    @Test
    fun `a currency the traveller chose is not overwritten by a later destination`() {
        val state = PlannerUiState().withCurrencyChosen("USD").withSuggestionPicked(lisbon)
        assertEquals("USD", state.currency)
    }

    @Test
    fun `a destination we cannot price leaves the currency alone instead of clearing it`() {
        val state = PlannerUiState().withSuggestionPicked(lisbon).withSuggestionPicked(unpriced)
        assertEquals("EUR", state.currency)
    }

    @Test
    fun `changing destination before choosing a currency follows the new destination`() {
        val state = PlannerUiState().withSuggestionPicked(lisbon).withSuggestionPicked(bangkok)
        assertEquals("THB", state.currency)
        assertFalse(state.currencyTouched)
    }

    @Test
    fun `the form cannot be built until a currency is settled`() {
        val noCurrency = readyForm().copy(currency = null)
        assertFalse(noCurrency.canBuild)
        assertEquals("Pick the currency this trip is budgeted in.", noCurrency.guidance)
    }

    @Test
    fun `a currency outside the base five is still offered so it can be seen and kept`() {
        // The chip list holds five currencies; a destination can resolve to any of about a hundred and
        // fifty. Bangkok must not resolve to THB and then offer no way to see it.
        val offered = PlannerUiState().withSuggestionPicked(bangkok).offeredCurrencies
        assertTrue(offered.any { it.first == "THB" })
    }

    @Test
    fun `a currency already in the base list is not offered twice`() {
        val offered = PlannerUiState().withSuggestionPicked(lisbon).offeredCurrencies
        assertEquals(1, offered.count { it.first == "EUR" })
    }

    // ---- Destination ----

    @Test
    fun `typing clears the place that was picked, because the field no longer names it`() {
        val state = PlannerUiState().withSuggestionPicked(lisbon).withDestinationTyped("Lisb")
        assertNull(state.resolved)
        assertTrue(state.destinationIsUnresolved)
    }

    @Test
    fun `picking a place puts its full label in the field so the engine geocodes something unambiguous`() {
        assertEquals("Lisbon, Portugal", PlannerUiState().withSuggestionPicked(lisbon).destination)
    }

    @Test
    fun `a picked place falls back to its name when the server sent no label`() {
        val noLabel = lisbon.copy(label = "")
        assertEquals("Lisbon", PlannerUiState().withSuggestionPicked(noLabel).destination)
    }

    @Test
    fun `a resolved destination is not flagged as unresolved`() {
        assertFalse(PlannerUiState().withSuggestionPicked(lisbon).destinationIsUnresolved)
    }

    @Test
    fun `a short entry is not yet flagged as unresolved, because nothing has been looked up`() {
        assertFalse(PlannerUiState(destination = "L").destinationIsUnresolved)
    }

    @Test
    fun `an empty destination is the first thing the form asks for`() {
        assertEquals("Type where you are going.", PlannerUiState().guidance)
    }

    @Test
    fun `picking a place closes the list`() {
        val state = PlannerUiState(suggestions = listOf(lisbon), suggestionsVisible = true, suggestionsLoading = true)
            .withSuggestionPicked(lisbon)
        assertTrue(state.suggestions.isEmpty())
        assertFalse(state.suggestionsVisible)
        assertFalse(state.suggestionsLoading)
    }

    // ---- Dates, budget, and the order things are asked in ----

    @Test
    fun `a trip cannot end before it starts`() {
        val state = readyForm().copy(
            startDate = LocalDate.now().plusDays(10),
            endDate = LocalDate.now().plusDays(7)
        )
        assertEquals("The last day cannot be before the first day.", state.dateProblem)
    }

    @Test
    fun `a trip cannot start in the past`() {
        val state = readyForm().copy(startDate = LocalDate.now().minusDays(1))
        assertEquals("Pick a first day from today onwards.", state.dateProblem)
    }

    @Test
    fun `a blank budget is allowed and means the plan is not checked against one`() {
        val state = readyForm().copy(budget = "")
        assertNull(state.budgetProblem)
        assertNull(state.budgetValue)
        assertTrue(state.canBuild)
    }

    @Test
    fun `a budget with thousands separators is read as a number`() {
        assertEquals(2500.0, readyForm().copy(budget = "2,500").budgetValue!!, 0.001)
    }

    @Test
    fun `a budget of zero is refused`() {
        assertEquals("A budget has to be more than zero.", readyForm().copy(budget = "0").budgetProblem)
    }

    @Test
    fun `guidance reports the destination before the dates`() {
        val state = PlannerUiState(destination = "", startDate = null, endDate = null)
        assertEquals("Type where you are going.", state.guidance)
    }

    @Test
    fun `guidance reports the dates before the currency`() {
        val state = PlannerUiState(destination = "Lisbon, Portugal")
        assertEquals("Pick the first and last day of the trip.", state.guidance)
    }

    @Test
    fun `a complete form can be built`() {
        val state = readyForm()
        assertNull(state.guidance)
        assertTrue(state.canBuild)
    }

    @Test
    fun `a form being submitted cannot be submitted again`() {
        assertFalse(readyForm().copy(submitting = true).canBuild)
    }
}
