package com.trippin.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val destination: String,
    val startDate: String,
    val endDate: String,
    val travelersCount: Int,
    val status: String,
    val heroImageUrl: String?,
    val currentVersion: Int,
    val updatedAt: String
)
