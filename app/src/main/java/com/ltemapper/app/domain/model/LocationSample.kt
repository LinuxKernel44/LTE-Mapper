package com.ltemapper.app.domain.model

/** A single GPS fix, already filtered by the configured min-distance/min-time rules. */
data class LocationSample(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double? = null,
    val speedMps: Float? = null,
    val bearingDeg: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val isLowAccuracy: Boolean = false,
)

/** Above this horizontal accuracy (meters), a fix is flagged low-accuracy rather than trusted. */
const val LOW_ACCURACY_THRESHOLD_METERS = 30f
