package com.ltemapper.app.mapping

import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.SignalQuality

data class MapPoint(
    val latitude: Double,
    val longitude: Double,
    val quality: SignalQuality,
    val rsrpDbm: Int?,
    val isLowAccuracy: Boolean,
    val timestampMillis: Long,
)

fun Measurement.toMapPoint(): MapPoint = MapPoint(
    latitude = latitude,
    longitude = longitude,
    quality = signalQuality,
    rsrpDbm = rsrpDbm,
    isLowAccuracy = isLowAccuracy,
    timestampMillis = timestamp,
)
