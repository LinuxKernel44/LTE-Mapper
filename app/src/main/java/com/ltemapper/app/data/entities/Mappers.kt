package com.ltemapper.app.data.entities

import com.ltemapper.app.domain.model.BoundingBox
import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.OfflineRegion
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality

fun MappingSessionEntity.toDomain(): MappingSession = MappingSession(
    id = id,
    startTime = startTime,
    endTime = endTime,
    distanceMeters = distanceMeters,
    measurementCount = measurementCount,
    label = label,
)

fun MappingSession.toEntity(): MappingSessionEntity = MappingSessionEntity(
    id = id,
    startTime = startTime,
    endTime = endTime,
    distanceMeters = distanceMeters,
    measurementCount = measurementCount,
    label = label,
)

fun SignalMeasurementEntity.toDomain(): Measurement = Measurement(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = accuracy,
    isLowAccuracy = isLowAccuracy,
    networkType = runCatching { RanType.valueOf(networkType) }.getOrDefault(RanType.UNKNOWN),
    rssiDbm = rssi,
    rsrpDbm = rsrp,
    rsrqDb = rsrq,
    rssnrDb = rssnr,
    cqi = cqi,
    pci = pci,
    earfcn = earfcn,
    cellId = cellId,
    tac = tac,
    mcc = mcc,
    mnc = mnc,
    operatorName = operator,
    connectionState = connectionState,
    signalQuality = runCatching { SignalQuality.valueOf(signalQuality) }.getOrDefault(SignalQuality.UNKNOWN),
)

fun Measurement.toEntity(): SignalMeasurementEntity = SignalMeasurementEntity(
    id = id,
    sessionId = sessionId,
    timestamp = timestamp,
    latitude = latitude,
    longitude = longitude,
    accuracy = accuracyMeters,
    isLowAccuracy = isLowAccuracy,
    networkType = networkType.name,
    rssi = rssiDbm,
    rsrp = rsrpDbm,
    rsrq = rsrqDb,
    rssnr = rssnrDb,
    cqi = cqi,
    pci = pci,
    earfcn = earfcn,
    cellId = cellId,
    tac = tac,
    mcc = mcc,
    mnc = mnc,
    operator = operatorName,
    connectionState = connectionState,
    signalQuality = signalQuality.name,
)

fun OfflineRegionEntity.toDomain(): OfflineRegion = OfflineRegion(
    id = id,
    name = name,
    boundingBox = BoundingBox(minLat = minLat, maxLat = maxLat, minLon = minLon, maxLon = maxLon),
    minZoom = minZoom,
    maxZoom = maxZoom,
    tileCount = tileCount,
    sizeBytes = sizeBytes,
    downloadedAt = downloadedAt,
)

fun OfflineRegion.toEntity(): OfflineRegionEntity = OfflineRegionEntity(
    id = id,
    name = name,
    minLat = boundingBox.minLat,
    maxLat = boundingBox.maxLat,
    minLon = boundingBox.minLon,
    maxLon = boundingBox.maxLon,
    minZoom = minZoom,
    maxZoom = maxZoom,
    tileCount = tileCount,
    sizeBytes = sizeBytes,
    downloadedAt = downloadedAt,
)
