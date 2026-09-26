package com.trippin.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {
    @Query("SELECT * FROM q_table ORDER BY stateIndex")
    suspend fun qTable(): List<QRowEntity>

    @Upsert
    suspend fun saveRows(rows: List<QRowEntity>)

    @Insert
    suspend fun logFeedback(f: FeedbackEntity)

    @Query("SELECT COUNT(*) FROM feedback")
    fun observeFeedbackCount(): Flow<Int>

    @Query("DELETE FROM q_table")
    suspend fun clearQ()

    @Query("DELETE FROM feedback")
    suspend fun clearFeedback()
}
