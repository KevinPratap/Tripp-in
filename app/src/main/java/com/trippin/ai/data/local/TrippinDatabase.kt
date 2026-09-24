package com.trippin.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TripEntity::class, StopEntity::class, GenerationEntity::class, QRowEntity::class, FeedbackEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TrippinDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun learningDao(): LearningDao

    companion object {
        fun create(context: Context): TrippinDatabase =
            Room.databaseBuilder(context, TrippinDatabase::class.java, "trippin.db").build()
    }
}
