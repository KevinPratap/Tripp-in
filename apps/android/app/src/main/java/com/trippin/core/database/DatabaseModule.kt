package com.trippin.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrippinDatabase =
        Room.databaseBuilder(context, TrippinDatabase::class.java, "trippin.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCachedJsonDao(db: TrippinDatabase): CachedJsonDao = db.cachedJsonDao()

    @Provides
    fun provideVisitedStopDao(db: TrippinDatabase): VisitedStopDao = db.visitedStopDao()

    @Provides
    fun provideSavedSpotDao(db: TrippinDatabase): SavedSpotDao = db.savedSpotDao()
}
