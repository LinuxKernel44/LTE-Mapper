package com.ltemapper.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.GeocodingResult
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.OfflineRegion
import com.ltemapper.app.domain.model.TileDownloadProgress
import com.ltemapper.app.util.formatBytes
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineMapsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfflineMapsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var pendingClearAll by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<OfflineRegion?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Offline maps") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    "Pre-download map tiles so they still show while you're offline. Areas you " +
                        "haven't downloaded keep loading from the network as usual whenever you have a connection.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            item {
                CacheUsageCard(
                    totalBytes = state.totalCacheUsageBytes,
                    wifiOnly = state.downloadOnWifiOnly,
                    onWifiOnlyChange = viewModel::setDownloadOnWifiOnly,
                    onClearAll = { pendingClearAll = true },
                )
            }

            item { SectionTitle("Map source") }
            item {
                TileSourceCard(
                    selected = state.tileSource,
                    custom = state.customTileSource,
                    onSelect = viewModel::setTileSource,
                )
            }

            item { SectionTitle("Search a country, region, or city") }
            item {
                SearchCard(
                    query = state.searchQuery,
                    isSearching = state.isSearching,
                    results = state.searchResults,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSearch = viewModel::search,
                    onSelect = viewModel::selectPlace,
                )
            }

            item { SectionTitle("Quick picks") }
            item {
                QuickPicksRow(picks = state.quickPicks, onSelect = viewModel::selectPlace)
            }

            state.selectedPlace?.let { place ->
                item {
                    SelectedPlaceCard(
                        place = place,
                        minZoom = state.minZoom,
                        maxZoom = state.maxZoom,
                        estimatedTiles = state.estimatedTiles,
                        estimatedChunks = state.estimatedChunks,
                        estimatedSizeBytes = state.estimatedSizeBytes,
                        progress = state.downloadProgress,
                        canDownload = state.canStartDownload,
                        exceedsHardCap = state.exceedsHardCap,
                        waitingForConstraints = state.waitingForConstraints,
                        onZoomRangeChange = viewModel::setZoomRange,
                        onDownload = viewModel::startDownload,
                        onCancel = viewModel::cancelDownload,
                        onDismiss = viewModel::clearSelection,
                    )
                }
            }

            item { SectionTitle("Downloaded (${state.downloadedRegions.size})") }
            if (state.downloadedRegions.isEmpty()) {
                item { Text("Nothing downloaded yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(state.downloadedRegions, key = { it.id }) { region ->
                    DownloadedRegionRow(region = region, onDelete = { pendingDelete = region })
                }
            }
        }
    }

    if (pendingClearAll) {
        AlertDialog(
            onDismissRequest = { pendingClearAll = false },
            title = { Text("Clear all offline tiles?") },
            text = { Text("This deletes every downloaded map area from this device. You can always re-download them later.") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllOfflineTiles(); pendingClearAll = false }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { pendingClearAll = false }) { Text("Cancel") } },
        )
    }

    pendingDelete?.let { region ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${region.name}\"?") },
            text = { Text("This removes the offline tiles for this area. The map keeps working there whenever you're online.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteRegion(region); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CacheUsageCard(
    totalBytes: Long,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onClearAll: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Offline tile storage", style = MaterialTheme.typography.bodyMedium)
                    Text(formatBytes(totalBytes), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = onClearAll) { Text("Clear all") }
            }
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Download on Wi-Fi only", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Offline areas can be hundreds of megabytes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
            }
        }
    }
}

@Composable
private fun TileSourceCard(
    selected: MapTileSource,
    custom: CustomTileSourceConfig,
    onSelect: (MapTileSource, CustomTileSourceConfig) -> Unit,
) {
    var urlTemplate by remember(custom.urlTemplate) { mutableStateOf(custom.urlTemplate) }
    var apiKey by remember(custom.apiKey) { mutableStateOf(custom.apiKey) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "This is the map you see AND the map that gets downloaded — offline tiles only show " +
                    "up for the source they were fetched for.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MapTileSource.entries.forEach { source ->
                Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(
                            selected = selected == source,
                            onClick = { onSelect(source, CustomTileSourceConfig(urlTemplate, apiKey)) },
                        )
                        Column {
                            Text(source.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                source.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (source.supportsBulkDownload) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                            )
                        }
                    }
                }
            }

            if (selected == MapTileSource.CUSTOM) {
                HorizontalDivider()
                OutlinedTextField(
                    value = urlTemplate,
                    onValueChange = { urlTemplate = it },
                    label = { Text("Tile URL template") },
                    placeholder = { Text("https://provider/tiles/{z}/{x}/{y}.png?key={apiKey}") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = { onSelect(MapTileSource.CUSTOM, CustomTileSourceConfig(urlTemplate, apiKey)) }) {
                    Text("Save provider")
                }
                if (!CustomTileSourceConfig(urlTemplate, apiKey).isValid && urlTemplate.isNotBlank()) {
                    Text(
                        "The template must start with https:// and contain {z}, {x} and {y}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchCard(
    query: String,
    isSearching: Boolean,
    results: List<GeocodingResult>,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelect: (GeocodingResult) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    label = { Text("e.g. \"Bretagne\", \"Japan\", \"Lyon\"") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onSearch, enabled = query.isNotBlank() && !isSearching) { Text("Search") }
            }
            if (isSearching) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp))
            }
            results.forEach { result ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .then(Modifier),
                ) {
                    TextButton(onClick = { onSelect(result) }) {
                        Text(result.displayName, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(
                "Search uses OpenStreetMap's free lookup service — © OpenStreetMap contributors.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickPicksRow(picks: List<GeocodingResult>, onSelect: (GeocodingResult) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        picks.forEach { pick ->
            FilterChip(selected = false, onClick = { onSelect(pick) }, label = { Text(pick.displayName) })
        }
    }
}

@Composable
private fun SelectedPlaceCard(
    place: GeocodingResult,
    minZoom: Int,
    maxZoom: Int,
    estimatedTiles: Long?,
    estimatedChunks: Int?,
    estimatedSizeBytes: Long?,
    progress: TileDownloadProgress,
    canDownload: Boolean,
    exceedsHardCap: Boolean,
    waitingForConstraints: Boolean,
    onZoomRangeChange: (Int, Int) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(place.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onDismiss) { Text("Change") }
            }

            Text("Zoom levels: $minZoom – $maxZoom (higher = more detail, far more tiles)")
            RangeSlider(
                value = minZoom.toFloat()..maxZoom.toFloat(),
                onValueChange = { range -> onZoomRangeChange(range.start.toInt(), range.endInclusive.toInt()) },
                valueRange = OfflineMapsUiState.MIN_SELECTABLE_ZOOM.toFloat()..OfflineMapsUiState.MAX_SELECTABLE_ZOOM.toFloat(),
                steps = OfflineMapsUiState.MAX_SELECTABLE_ZOOM - OfflineMapsUiState.MIN_SELECTABLE_ZOOM - 1,
            )

            when (progress) {
                is TileDownloadProgress.SourceForbidsBulkDownload -> {
                    Text(
                        "\"${progress.sourceLabel}\" doesn't allow downloading areas in advance — its " +
                            "operator forbids bulk downloads. Pick a download-friendly map source above, " +
                            "then try again.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                is TileDownloadProgress.Downloading -> {
                    LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "${"%,d".format(progress.downloadedTiles)} / ${"%,d".format(progress.totalTiles)} tiles " +
                            "· part ${progress.currentChunk} of ${progress.totalChunks}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "You can leave this screen or close the app — the download continues in the background.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
                is TileDownloadProgress.Estimating -> {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    if (waitingForConstraints) {
                        Text(
                            "Waiting for Wi-Fi — the download will start on its own once you're connected.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = onCancel) { Text("Cancel") }
                }
                is TileDownloadProgress.Failed -> {
                    Text(progress.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = onDownload, enabled = canDownload) { Text("Retry") }
                }
                is TileDownloadProgress.Completed -> {
                    Text(
                        if (progress.failedTiles > 0) {
                            "Downloaded ✓ (${"%,d".format(progress.failedTiles)} tiles couldn't be fetched and will load online)"
                        } else {
                            "Downloaded ✓"
                        },
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                TileDownloadProgress.Idle -> {
                    estimatedTiles?.let { tiles ->
                        Text("≈ ${"%,d".format(tiles)} tiles, about ${estimatedSizeBytes?.let { formatBytes(it) } ?: "N/A"}")
                        if ((estimatedChunks ?: 1) > 1) {
                            Text(
                                "Large area — it will be downloaded automatically in ${estimatedChunks} parts, one after another.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(onClick = onDownload, enabled = canDownload && estimatedTiles != null) { Text("Download") }
                    if (exceedsHardCap) {
                        Text(
                            "This area is too large to download in one go (limit: ${"%,d".format(OfflineMapsUiState.MAX_TILES_HARD_CAP)} tiles). Pick a smaller area or a narrower zoom range.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else if (!canDownload) {
                        Text(
                            "The current map source doesn't allow offline downloads. Choose another one above.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadedRegionRow(region: OfflineRegion, onDelete: () -> Unit) {
    val formatter = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            Column {
                Text(region.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "Zoom ${region.minZoom}–${region.maxZoom} · ${"%,d".format(region.tileCount)} tiles · " +
                        "${formatBytes(region.sizeBytes)} · ${formatter.format(Date(region.downloadedAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete downloaded area")
            }
        }
    }
}
