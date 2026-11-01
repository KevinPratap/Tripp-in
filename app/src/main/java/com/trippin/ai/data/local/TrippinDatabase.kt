package com.trippin.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * On-device data only: the Q-learning table and feedback log (personal learning stays on the
 * phone). Shared trip data moved to Firestore in version 2.
 */
@Database(
    entities = [QRowEntity::class, FeedbackEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class TrippinDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao

    companion object {
        /** v1 → v2: trips, stops and GA history now live in Firestore; the learned Q-table is kept. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS generations")
                db.execSQL("DROP TABLE IF EXISTS stops")
                db.execSQL("DROP TABLE IF EXISTS trips")
            }
        }

        fun create(context: Context): TrippinDatabase =
            Room.databaseBuilder(context, TrippinDatabase::class.java, "trippin.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
