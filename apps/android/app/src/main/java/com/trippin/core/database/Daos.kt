package com.trippin.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedJsonDao {
    @Query("SELECT * FROM cached_json WHERE `key` = :key")
    fun observe(key: String): Flow<CachedJsonEntity?>

    @Query("SELECT * FROM cached_json WHERE `key` = :key")
    suspend fun get(key: String): CachedJsonEntity?

    @Upsert
    suspend fun put(entity: CachedJsonEntity)

    @Query("DELETE FROM cached_json WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface VisitedStopDao {
    @Query("SELECT activityId FROM visited_stops")
    fun observeIds(): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM visited_stops WHERE activityId = :id)")
    suspend fun isVisited(id: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VisitedStopEntity)

    @Query("DELETE FROM visited_stops WHERE activityId = :id")
    suspend fun delete(id: String)
}

@Dao
interface SavedSpotDao {
    @Query("SELECT * FROM saved_spots ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedSpotEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM saved_spots WHERE placeId = :id)")
    suspend fun isSaved(id: String): Boolean

    @Upsert
    suspend fun insert(entity: SavedSpotEntity)

    @Delete
    suspend fun delete(entity: SavedSpotEntity)

    @Query("DELETE FROM saved_spots WHERE placeId = :id")
    suspend fun deleteById(id: String)
}
