package com.trippin.core.repository

import com.trippin.core.common.DataResult
import com.trippin.core.common.runNetwork
import com.trippin.core.database.CachedJsonDao
import com.trippin.core.database.CachedJsonEntity
import com.trippin.core.network.AddExpenseRequestDto
import com.trippin.core.network.ApiService
import com.trippin.core.network.ExpenseOverviewDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A trip's spending log. The last answer from the server is kept on the phone so the ledger still
 * reads offline; adding or removing an entry needs the network, because the server is what every
 * traveller on the trip reads from.
 */
@Singleton
class ExpenseRepository @Inject constructor(
    private val api: ApiService,
    private val cache: CachedJsonDao,
    private val json: Json
) {
    private fun key(tripId: String) = "expenses:$tripId"

    fun observe(tripId: String): Flow<ExpenseOverviewDto?> =
        cache.observe(key(tripId)).map { row ->
            row?.json?.let { runCatching { json.decodeFromString(ExpenseOverviewDto.serializer(), it) }.getOrNull() }
        }

    suspend fun refresh(tripId: String): DataResult<ExpenseOverviewDto> =
        store(tripId, runNetwork { api.getExpenses(tripId) })

    suspend fun add(tripId: String, request: AddExpenseRequestDto): DataResult<ExpenseOverviewDto> =
        store(tripId, runNetwork { api.addExpense(tripId, request) })

    suspend fun delete(tripId: String, expenseId: String): DataResult<ExpenseOverviewDto> =
        store(tripId, runNetwork { api.deleteExpense(tripId, expenseId) })

    private suspend fun store(tripId: String, result: DataResult<ExpenseOverviewDto>): DataResult<ExpenseOverviewDto> {
        if (result is DataResult.Ok) {
            cache.put(
                CachedJsonEntity(
                    key = key(tripId),
                    json = json.encodeToString(ExpenseOverviewDto.serializer(), result.value),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        return result
    }
}
