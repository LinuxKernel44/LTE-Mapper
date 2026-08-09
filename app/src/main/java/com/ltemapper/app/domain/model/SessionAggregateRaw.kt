package com.ltemapper.app.domain.model

/** Raw aggregate values pulled straight out of SQL (no full row scan), one row per session. */
data class SessionAggregateRaw(
    val totalCount: Int,
    val minRsrpDbm: Int?,
    val maxRsrpDbm: Int?,
    val avgRsrpDbm: Double?,
    val medianRsrpDbm: Int?,
    val avgRsrqDb: Double?,
    val avgSnrDb: Double?,
    val countExcellent: Int,
    val countGood: Int,
    val countFair: Int,
    val countWeak: Int,
    val countVeryWeak: Int,
    val countNoSignal: Int,
    val countUnknown: Int,
    val minLat: Double?,
    val maxLat: Double?,
    val minLon: Double?,
    val maxLon: Double?,
)
