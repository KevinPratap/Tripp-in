package com.trippin.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedJsonEntity::class,
        VisitedStopEntity::class,
        SavedSpotEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class TrippinDatabase : RoomDatabase() {
    abstract fun cachedJsonDao(): CachedJsonDao
    abstract fun visitedStopDao(): VisitedStopDao
    abstract fun savedSpotDao(): SavedSpotDao
}
