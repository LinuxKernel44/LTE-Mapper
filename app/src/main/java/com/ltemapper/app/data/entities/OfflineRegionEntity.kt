package com.ltemapper.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_regions")
data class OfflineRegionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val sizeBytes: Long,
    val downloadedAt: Long,
)
