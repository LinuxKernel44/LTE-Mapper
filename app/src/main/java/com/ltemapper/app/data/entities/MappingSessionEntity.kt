package com.ltemapper.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mapping_sessions")
data class MappingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val distanceMeters: Double,
    val measurementCount: Int,
    val label: String? = null,
)
