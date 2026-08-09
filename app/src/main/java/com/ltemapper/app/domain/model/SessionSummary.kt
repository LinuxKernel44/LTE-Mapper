package com.ltemapper.app.domain.model

data class BoundingBox(
    val minLat: Double,
    val maxLat: Double,
    val minLon: Double,
    val maxLon: Double,
)

data class MappingSession(
    val id: Long = 0,
    val startTime: Long,
    val endTime: Long?,
    val distanceMeters: Double,
    val measurementCount: Int,
    val label: String? = null,
)

/** Aggregated statistics for one mapping session, computed without loading every row into memory. */
data class SessionSummary(
    val session: MappingSession,
    val minRsrpDbm: Int?,
    val maxRsrpDbm: Int?,
    val avgRsrpDbm: Double?,
    val medianRsrpDbm: Int?,
    val avgRsrqDb: Double?,
    val avgSnrDb: Double?,
    val qualityDistributionPercent: Map<SignalQuality, Double>,
    val boundingBox: BoundingBox?,
)
