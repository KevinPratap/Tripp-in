package com.trippin.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "activities")
data class ActivityEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val dayIndex: Int,
    val placeId: String,
    val title: String,
    val type: String,
    val startTime: String,
    val endTime: String,
    val durationMinutes: Int,
    val travelTimeToNextMin: Int,
    val estimatedCost: Double?,
    val currency: String?,
    val reason: String?,
    val orderIndex: Int
)
