package com.trippin.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CachedJsonEntity::class,
        VisitedStopEntity::class,
        SavedSpotEntity::class,
        RecentDestinationEntity::class
    ],
    // v2 adds recent_destinations. No migration is written for it: everything this database holds is
    // either a cache of what the server already has or a convenience the traveller can rebuild by
    // using the app again, so DatabaseModule's fallbackToDestructiveMigration is the deliberate choice
    // rather than a gap.
    version = 2,
    exportSchema = false
)
abstract class TrippinDatabase : RoomDatabase() {
    abstract fun cachedJsonDao(): CachedJsonDao
    abstract fun visitedStopDao(): VisitedStopDao
    abstract fun savedSpotDao(): SavedSpotDao
    abstract fun recentDestinationDao(): RecentDestinationDao
}
