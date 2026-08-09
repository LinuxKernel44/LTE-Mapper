package com.ltemapper.app.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "signal_measurements",
    foreignKeys = [
        ForeignKey(
            entity = MappingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("sessionId"),
        Index("timestamp"),
        Index(value = ["latitude", "longitude"]),
        Index(value = ["sessionId", "rsrp"]),
        Index(value = ["sessionId", "networkType"]),
    ],
)
data class SignalMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val isLowAccuracy: Boolean,
    val networkType: String,
    val rssi: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val rssnr: Int?,
    val cqi: Int?,
    val pci: Int?,
    val earfcn: Int?,
    val cellId: Long?,
    val tac: Int?,
    val mcc: String?,
    val mnc: String?,
    val operator: String?,
    val connectionState: String?,
    val signalQuality: String,
)
