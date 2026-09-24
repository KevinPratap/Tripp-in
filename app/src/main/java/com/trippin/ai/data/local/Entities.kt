package com.trippin.ai.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val destination: String,
    val lat: Double,
    val lng: Double,
    /** ISO date of day 1, e.g. 2026-10-13. */
    val startDate: String,
    val days: Int,
    val travellers: Int,
    val pace: String,
    val interests: String,
    val rainProbability: Double,
    val temperatureC: Double,
    val verified: Boolean,
    val totalKm: Double,
    val gaImprovementPercent: Double,
    /** True when the network failed and the offline demo city was used instead. */
    val usedDemoData: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "stops",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class StopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val dayIndex: Int,
    val orderInDay: Int,
    val name: String,
    val lat: Double,
    val lng: Double,
    val category: String,
    val arrive: Int,
    val start: Int,
    val leave: Int,
    val travelMinutes: Int,
    val hoursEstimated: Boolean,
    val pDisrupted: Double,
    val pDelay: Double,
    val pClosed: Double,
    val pRainGivenDisrupted: Double,
    val riskLevel: String,
)

/** Best and average route cost per GA generation, so the evolution chart survives restarts. */
@Entity(
    tableName = "generations",
    primaryKeys = ["tripId", "dayIndex", "generation"],
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
)
data class GenerationEntity(
    val tripId: Long,
    val dayIndex: Int,
    val generation: Int,
    val bestCost: Double,
    val averageCost: Double,
)

/** One row of the Q-table: a state index and its six action values as CSV. */
@Entity(tableName = "q_table")
data class QRowEntity(
    @PrimaryKey val stateIndex: Int,
    val qValues: String,
)

@Entity(tableName = "feedback")
data class FeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stateIndex: Int,
    val category: String,
    val feedback: String,
    val at: Long = System.currentTimeMillis(),
)

data class TripWithStops(
    @Embedded val trip: TripEntity,
    @Relation(parentColumn = "id", entityColumn = "tripId") val stops: List<StopEntity>,
)
