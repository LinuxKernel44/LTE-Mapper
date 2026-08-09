package com.ltemapper.app.domain.model

/** A location fix joined with the cell signal snapshot taken at (approximately) the same time. */
data class Measurement(
    val id: Long = 0,
    val sessionId: Long,
    val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val isLowAccuracy: Boolean,
    val networkType: RanType,
    val rssiDbm: Int?,
    val rsrpDbm: Int?,
    val rsrqDb: Int?,
    val rssnrDb: Int?,
    val cqi: Int?,
    val pci: Int?,
    val earfcn: Int?,
    val cellId: Long?,
    val tac: Int?,
    val mcc: String?,
    val mnc: String?,
    val operatorName: String?,
    val connectionState: String?,
    val signalQuality: SignalQuality,
)
