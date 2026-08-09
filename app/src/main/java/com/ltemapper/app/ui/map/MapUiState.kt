package com.ltemapper.app.ui.map

import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.LocationSample
import com.ltemapper.app.domain.model.MapDisplayMode
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.model.SignalThresholds
import com.ltemapper.app.mapping.MapPoint

data class MapUiState(
    val isMapping: Boolean = false,
    val currentSignal: CellSignalInfo? = null,
    val currentSignalQuality: SignalQuality = SignalQuality.UNKNOWN,
    val currentLocation: LocationSample? = null,
    val measurementCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val mappingDurationMillis: Long = 0,
    val points: List<MapPoint> = emptyList(),
    val displayMode: MapDisplayMode = MapDisplayMode.POINTS,
    val networkFilter: NetworkFilter = NetworkFilter.ALL,
    val tileSource: MapTileSource = MapTileSource.MAPNIK,
    val customTileSource: CustomTileSourceConfig = CustomTileSourceConfig(),
    val followUser: Boolean = true,
    val showDashboard: Boolean = false,
    val missingPermissions: Boolean = false,
    val signalThresholds: SignalThresholds = SignalThresholds.DEFAULT,
)
