package com.ltemapper.app.ui.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ltemapper.app.domain.model.MapDisplayMode
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.ui.components.SignalDashboard
import com.ltemapper.app.ui.components.SignalLegend

/**
 * The map fills the screen, with controls laid out in two non-overlapping regions: a top row
 * (mode/filter on the left, icon buttons on the right) and a bottom column that stacks the
 * optional legend, the dashboard, and the attribution + start/stop button. Everything that can
 * grow is either collapsible or wraps, so nothing ends up drawn on top of anything else.
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var legendVisible by remember { mutableStateOf(false) }

    MapScreenContent(
        state = state,
        legendVisible = legendVisible,
        onToggleLegend = { legendVisible = !legendVisible },
        onSetDisplayMode = viewModel::setDisplayMode,
        onSetNetworkFilter = viewModel::setNetworkFilter,
        onToggleFollowUser = viewModel::toggleFollowUser,
        onCycleTileSource = { cycleStyle(state.tileSource, viewModel::setTileSource) },
        onToggleDashboard = viewModel::toggleDashboard,
        onStartMapping = viewModel::startMapping,
        onStopMapping = viewModel::stopMapping,
        modifier = modifier,
    )
}

/**
 * Stateless layout, with the map itself behind a slot. Keeping it free of ViewModel and osmdroid
 * dependencies is what allows the overlap regression tests to render and measure this screen.
 */
@Composable
fun MapScreenContent(
    state: MapUiState,
    legendVisible: Boolean,
    onToggleLegend: () -> Unit,
    onSetDisplayMode: (MapDisplayMode) -> Unit,
    onSetNetworkFilter: (NetworkFilter) -> Unit,
    onToggleFollowUser: () -> Unit,
    onCycleTileSource: () -> Unit,
    onToggleDashboard: () -> Unit,
    onStartMapping: () -> Unit,
    onStopMapping: () -> Unit,
    modifier: Modifier = Modifier,
    mapContent: @Composable () -> Unit = {
        OsmMapView(
            points = state.points,
            displayMode = state.displayMode,
            tileSource = state.tileSource,
            customTileSource = state.customTileSource,
            followUser = state.followUser,
            currentLocation = state.currentLocation,
            modifier = Modifier.fillMaxSize(),
        )
    },
) {
    Box(modifier = modifier.fillMaxSize()) {
        mapContent()

        // ---- Top region: controls on the left, icon buttons on the right ----
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DisplayModeSelector(state.displayMode, onSetDisplayMode)
                NetworkFilterRow(state.networkFilter, onSetNetworkFilter)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallFloatingActionButton(onClick = onToggleFollowUser) {
                    Icon(
                        Icons.Filled.MyLocation,
                        contentDescription = if (state.followUser) "Stop following my location" else "Follow my location",
                    )
                }
                SmallFloatingActionButton(onClick = onCycleTileSource) {
                    Icon(Icons.Filled.Layers, contentDescription = "Change map style")
                }
                SmallFloatingActionButton(onClick = onToggleLegend) {
                    Icon(Icons.Filled.Info, contentDescription = if (legendVisible) "Hide legend" else "Show legend")
                }
            }
        }

        // ---- Bottom region: everything stacks vertically, so nothing overlaps ----
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (legendVisible) {
                SignalLegend(thresholds = state.signalThresholds)
            }

            SignalDashboard(
                signal = state.currentSignal,
                quality = state.currentSignalQuality,
                gpsAccuracyMeters = state.currentLocation?.accuracyMeters,
                measurementCount = state.measurementCount,
                distanceMeters = state.distanceMeters,
                durationMillis = state.mappingDurationMillis,
                expanded = state.showDashboard,
                onToggleExpanded = onToggleDashboard,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = state.tileSource.attribution,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                ExtendedFloatingActionButton(
                    onClick = { if (state.isMapping) onStopMapping() else onStartMapping() },
                    icon = {
                        Icon(
                            if (state.isMapping) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                            contentDescription = null,
                        )
                    },
                    text = { Text(if (state.isMapping) "Stop" else "Start mapping") },
                )
            }
        }
    }
}

/** Cycles through the built-in sources only; CUSTOM needs its URL configured in Settings first. */
private fun cycleStyle(current: MapTileSource, onSelect: (MapTileSource) -> Unit) {
    val values = MapTileSource.entries.filterNot { it.isCustom }
    val next = values[(values.indexOf(current).coerceAtLeast(0) + 1) % values.size]
    onSelect(next)
}

@Composable
private fun DisplayModeSelector(current: MapDisplayMode, onSelect: (MapDisplayMode) -> Unit) {
    SingleChoiceSegmentedButtonRow {
        MapDisplayMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = current == mode,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = MapDisplayMode.entries.size),
            ) {
                Text(mode.label, maxLines = 1, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun NetworkFilterRow(current: NetworkFilter, onSelect: (NetworkFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
        items(NetworkFilter.entries) { filter ->
            FilterChip(
                selected = current == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label, maxLines = 1, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}
