package com.trippin.ai.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TripDao {
    @Query("SELECT * FROM trips ORDER BY createdAt DESC")
    abstract fun observeTrips(): Flow<List<TripEntity>>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract fun observeTrip(id: Long): Flow<TripWithStops?>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :id")
    abstract suspend fun getTrip(id: Long): TripWithStops?

    @Query("SELECT * FROM trips WHERE startDate >= :today ORDER BY startDate ASC LIMIT 1")
    abstract suspend fun nextTrip(today: String): TripEntity?

    @Query("SELECT * FROM generations WHERE tripId = :tripId AND dayIndex = :day ORDER BY generation")
    abstract fun observeGenerations(tripId: Long, day: Int): Flow<List<GenerationEntity>>

    @Insert
    abstract suspend fun insertTrip(trip: TripEntity): Long

    @Insert
    abstract suspend fun insertStops(stops: List<StopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertGenerations(rows: List<GenerationEntity>)

    @Query("UPDATE trips SET rainProbability = :rain WHERE id = :id")
    abstract suspend fun updateRain(id: Long, rain: Double)

    @Query("DELETE FROM trips WHERE id = :id")
    abstract suspend fun delete(id: Long)

    /** Saves a whole plan atomically: either every row lands or none do. */
    @Transaction
    open suspend fun insertPlan(trip: TripEntity, stops: (Long) -> List<StopEntity>, generations: (Long) -> List<GenerationEntity>): Long {
        val id = insertTrip(trip)
        insertStops(stops(id))
        insertGenerations(generations(id))
        return id
    }
}

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
