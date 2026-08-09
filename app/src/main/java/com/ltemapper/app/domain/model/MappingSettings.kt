package com.ltemapper.app.domain.model

/** User-configurable recording and battery-usage knobs, persisted via DataStore. */
data class MappingSettings(
    val minDistanceMeters: Float = 8f,
    val minIntervalMillis: Long = 3_000L,
    val locationUpdateIntervalMillis: Long = 2_000L,
    val backgroundMappingEnabled: Boolean = false,
    val signalThresholds: SignalThresholds = SignalThresholds.DEFAULT,
    val tileSource: MapTileSource = MapTileSource.MAPNIK,
    val customTileSource: CustomTileSourceConfig = CustomTileSourceConfig(),
    /** Offline map downloads can be hundreds of MB, so default to Wi-Fi only. */
    val downloadOnWifiOnly: Boolean = true,
) {
    companion object {
        val DEFAULT = MappingSettings()
        const val MIN_DISTANCE_RANGE_LOW = 5f
        const val MIN_DISTANCE_RANGE_HIGH = 10f
        const val MIN_INTERVAL_RANGE_LOW_MS = 1_000L
        const val MIN_INTERVAL_RANGE_HIGH_MS = 5_000L
    }
}
