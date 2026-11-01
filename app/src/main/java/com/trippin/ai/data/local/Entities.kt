package com.trippin.ai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

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
