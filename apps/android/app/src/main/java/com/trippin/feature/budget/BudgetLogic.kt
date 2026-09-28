package com.trippin.feature.budget

import com.trippin.core.design.parseStatedAmount
import com.trippin.core.network.AddExpenseRequestDto
import com.trippin.core.network.TripDetailsDto

/** The add-expense form as typed. */
data class ExpenseDraft(
    val title: String = "",
    val amount: String = "",
    val paidBy: String = "",
    val splitBetween: Set<String> = emptySet()
)

sealed interface DraftCheck {
    data class Ok(val request: AddExpenseRequestDto) : DraftCheck
    data class Problem(val message: String) : DraftCheck
}

/**
 * Turns the form into a request, or says what is missing. The server holds the same rules; checking
 * here too means the traveller hears about a typo before a round trip, and offline.
 */
fun checkDraft(draft: ExpenseDraft, currency: String?): DraftCheck {
    val title = draft.title.trim()
    if (title.length < 2) return DraftCheck.Problem("Say what it was for.")
    val amount = parseStatedAmount(draft.amount)
    if (amount == null || amount <= 0.0) return DraftCheck.Problem("Enter an amount above zero.")
    val payer = draft.paidBy.trim()
    if (payer.isEmpty()) return DraftCheck.Problem("Pick who paid.")
    val split = draft.splitBetween.map { it.trim() }.filter { it.isNotEmpty() }.sorted()
    if (split.isEmpty()) return DraftCheck.Problem("Pick at least one person to split it with.")
    return DraftCheck.Ok(
        AddExpenseRequestDto(
            title = title,
            amount = Math.round(amount * 100) / 100.0,
            currency = currency?.trim()?.uppercase()?.takeIf { it.isNotEmpty() },
            paidBy = payer,
            splitBetween = split
        )
    )
}

/**
 * What the plan's own stops are estimated to cost, in the trip's currency. Stops priced in any other
 * currency are left out rather than added in, and a plan with no priced stops has no estimate at all.
 */
fun planEstimate(details: TripDetailsDto?, currency: String?): Double? {
    val code = currency?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
    val priced = details?.itinerary?.days.orEmpty()
        .flatMap { it.activities }
        .filter { (it.estimatedCost ?: 0.0) > 0.0 && it.currency?.trim()?.uppercase() == code }
    if (priced.isEmpty()) return null
    return priced.sumOf { it.estimatedCost ?: 0.0 }
}

/** Who can pay or share: the trip's travellers by name, or a single "Me" when none are listed yet. */
fun payersFor(details: TripDetailsDto?): List<String> =
    details?.trip?.travellers.orEmpty().map { it.name.trim() }.filter { it.isNotEmpty() }.distinct()
        .ifEmpty { listOf("Me") }
