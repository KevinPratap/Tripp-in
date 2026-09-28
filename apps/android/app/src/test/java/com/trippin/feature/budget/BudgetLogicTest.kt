package com.trippin.feature.budget

import com.trippin.core.network.ActivityDto
import com.trippin.core.network.ItineraryDayDto
import com.trippin.core.network.ItineraryDto
import com.trippin.core.network.TravellerDto
import com.trippin.core.network.TripDetailsDto
import com.trippin.core.network.TripSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetLogicTest {

    private fun stop(cost: Double?, currency: String?) = ActivityDto(
        id = "a${cost}${currency}", placeId = "p", title = "t", type = "SIGHT",
        startTime = "10:00", endTime = "11:00", durationMinutes = 60,
        estimatedCost = cost, currency = currency
    )

    private fun details(stops: List<ActivityDto>, travellers: List<String> = emptyList()) = TripDetailsDto(
        trip = TripSummaryDto(
            id = "trip", destination = "Lisbon, Portugal", startDate = "2026-09-27", endDate = "2026-09-30",
            travelersCount = travellers.size, status = "READY", currency = "EUR",
            travellers = travellers.map { TravellerDto(id = it, name = it, joinedAt = "2026-09-01") }
        ),
        itinerary = ItineraryDto(
            id = "it", tripId = "trip", version = 1, status = "VERIFIED",
            days = listOf(ItineraryDayDto(id = "d1", date = "2026-09-27", dayIndex = 1, activities = stops))
        )
    )

    @Test
    fun `a complete draft becomes a request in the trip currency with the split sorted`() {
        val result = checkDraft(
            ExpenseDraft(title = " Dinner ", amount = "1,084.5", paidBy = "Sam", splitBetween = setOf("Sam", "Kevin")),
            currency = "eur"
        )
        assertTrue(result is DraftCheck.Ok)
        val request = (result as DraftCheck.Ok).request
        assertEquals("Dinner", request.title)
        assertEquals(1084.5, request.amount, 0.0001)
        assertEquals("EUR", request.currency)
        assertEquals(listOf("Kevin", "Sam"), request.splitBetween)
    }

    @Test
    fun `each missing piece is named`() {
        assertEquals(DraftCheck.Problem("Say what it was for."), checkDraft(ExpenseDraft(title = "x"), "EUR"))
        assertEquals(DraftCheck.Problem("Enter an amount above zero."), checkDraft(ExpenseDraft(title = "Taxi", amount = "0"), "EUR"))
        assertEquals(DraftCheck.Problem("Pick who paid."), checkDraft(ExpenseDraft(title = "Taxi", amount = "12"), "EUR"))
        assertEquals(
            DraftCheck.Problem("Pick at least one person to split it with."),
            checkDraft(ExpenseDraft(title = "Taxi", amount = "12", paidBy = "Sam"), "EUR")
        )
    }

    @Test
    fun `the plan estimate adds only stops priced in the trip currency`() {
        val d = details(listOf(stop(30.0, "EUR"), stop(12.5, "eur"), stop(2000.0, "JPY"), stop(null, "EUR")))
        assertEquals(42.5, planEstimate(d, "EUR")!!, 0.0001)
    }

    @Test
    fun `no priced stops means no estimate rather than zero`() {
        assertNull(planEstimate(details(listOf(stop(null, null))), "EUR"))
        assertNull(planEstimate(details(listOf(stop(10.0, "EUR"))), null))
    }

    @Test
    fun `payers are the travellers by name, or Me when there are none`() {
        assertEquals(listOf("Kevin", "Sam"), payersFor(details(emptyList(), listOf("Kevin", "Sam", "Sam"))))
        assertEquals(listOf("Me"), payersFor(details(emptyList())))
    }
}
