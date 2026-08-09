package com.ltemapper.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ltemapper.app.data.repository.OfflineRegionRepository
import com.ltemapper.app.data.repository.SettingsRepository
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.GeocodingResult
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.OfflineRegion
import com.ltemapper.app.domain.model.TileDownloadProgress
import androidx.work.WorkInfo
import com.ltemapper.app.mapping.OfflineMapManager
import com.ltemapper.app.mapping.geocoding.NominatimGeocoder
import com.ltemapper.app.mapping.geocoding.QuickPickCountries
import com.ltemapper.app.work.OfflineDownloadScheduler
import com.ltemapper.app.work.OfflineTileDownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class OfflineMapsUiState(
    val searchQuery: String = "",
    val searchResults: List<GeocodingResult> = emptyList(),
    val isSearching: Boolean = false,
    val searchFailed: Boolean = false,
    val quickPicks: List<GeocodingResult> = QuickPickCountries.ALL,
    val selectedPlace: GeocodingResult? = null,
    val minZoom: Int = DEFAULT_MIN_ZOOM,
    val maxZoom: Int = DEFAULT_MAX_ZOOM,
    val estimatedTiles: Long? = null,
    val estimatedChunks: Int? = null,
    val estimatedSizeBytes: Long? = null,
    val downloadProgress: TileDownloadProgress = TileDownloadProgress.Idle,
    val downloadedRegions: List<OfflineRegion> = emptyList(),
    val totalCacheUsageBytes: Long = 0,
    val tileSource: MapTileSource = MapTileSource.MAPNIK,
    val customTileSource: CustomTileSourceConfig = CustomTileSourceConfig(),
    val downloadOnWifiOnly: Boolean = true,
    /** True while work is enqueued but held back by its constraints (e.g. waiting for Wi-Fi). */
    val waitingForConstraints: Boolean = false,
) {
    val isDownloading: Boolean get() = downloadProgress is TileDownloadProgress.Downloading ||
        downloadProgress is TileDownloadProgress.Estimating

    /** The chosen server refuses bulk pre-fetching, so nothing can be downloaded until it changes. */
    val sourceBlocksDownloads: Boolean get() = !tileSource.supportsBulkDownload

    companion object {
        const val DEFAULT_MIN_ZOOM = 8
        const val DEFAULT_MAX_ZOOM = 14
        const val MIN_SELECTABLE_ZOOM = 4
        const val MAX_SELECTABLE_ZOOM = 17
        /** Rough average PNG tile weight, used only to turn a tile count into a human estimate. */
        const val AVG_TILE_BYTES = 12_000L
    }
}

@HiltViewModel
class OfflineMapsViewModel @Inject constructor(
    private val geocoder: NominatimGeocoder,
    private val offlineMapManager: OfflineMapManager,
    private val offlineRegionRepository: OfflineRegionRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadScheduler: OfflineDownloadScheduler,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineMapsUiState())
    val uiState: StateFlow<OfflineMapsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            offlineRegionRepository.observeRegions().collect { regions ->
                _uiState.update { it.copy(downloadedRegions = regions) }
                refreshCacheUsage()
            }
        }
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.update {
                    it.copy(
                        tileSource = settings.tileSource,
                        customTileSource = settings.customTileSource,
                        downloadOnWifiOnly = settings.downloadOnWifiOnly,
                    )
                }
            }
        }
        // Reconnects the UI to a download that is still running in the background, so reopening
        // the screen (or the whole app) shows live progress instead of a blank form.
        viewModelScope.launch {
            downloadScheduler.observeWork().collect { info ->
                _uiState.update { it.copy(downloadProgress = info.toProgress(), waitingForConstraints = info?.state == WorkInfo.State.ENQUEUED) }
            }
        }
        refreshCacheUsage()
    }

    private fun WorkInfo?.toProgress(): TileDownloadProgress = when (this?.state) {
        null -> TileDownloadProgress.Idle
        WorkInfo.State.ENQUEUED -> TileDownloadProgress.Estimating(
            progress.getString(OfflineTileDownloadWorker.KEY_PROGRESS_NAME).orEmpty(),
        )
        WorkInfo.State.RUNNING -> {
            val total = progress.getInt(OfflineTileDownloadWorker.KEY_PROGRESS_TOTAL, 0)
            if (total <= 0) {
                TileDownloadProgress.Estimating(progress.getString(OfflineTileDownloadWorker.KEY_PROGRESS_NAME).orEmpty())
            } else {
                TileDownloadProgress.Downloading(
                    name = progress.getString(OfflineTileDownloadWorker.KEY_PROGRESS_NAME).orEmpty(),
                    downloadedTiles = progress.getInt(OfflineTileDownloadWorker.KEY_PROGRESS_DOWNLOADED, 0),
                    totalTiles = total,
                    currentChunk = progress.getInt(OfflineTileDownloadWorker.KEY_PROGRESS_CHUNK, 1),
                    totalChunks = progress.getInt(OfflineTileDownloadWorker.KEY_PROGRESS_TOTAL_CHUNKS, 1),
                )
            }
        }
        WorkInfo.State.SUCCEEDED -> {
            val region = _uiState.value.downloadedRegions.firstOrNull()
            if (region != null) {
                TileDownloadProgress.Completed(
                    region = region,
                    failedTiles = outputData.getInt(OfflineTileDownloadWorker.KEY_FAILED_TILES, 0),
                )
            } else {
                TileDownloadProgress.Idle
            }
        }
        WorkInfo.State.FAILED -> TileDownloadProgress.Failed(
            outputData.getString(OfflineTileDownloadWorker.KEY_ERROR) ?: "Download failed",
        )
        WorkInfo.State.CANCELLED -> TileDownloadProgress.Failed("Download cancelled. Tiles already fetched were kept.")
        WorkInfo.State.BLOCKED -> TileDownloadProgress.Idle
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, searchFailed = false) }
    }

    fun search() {
        val query = _uiState.value.searchQuery
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, searchFailed = false) }
            val results = geocoder.search(query)
            _uiState.update {
                it.copy(isSearching = false, searchResults = results, searchFailed = results.isEmpty())
            }
        }
    }

    fun selectPlace(place: GeocodingResult) {
        _uiState.update {
            it.copy(
                selectedPlace = place,
                estimatedTiles = null,
                estimatedChunks = null,
                estimatedSizeBytes = null,
                downloadProgress = TileDownloadProgress.Idle,
            )
        }
        recomputeEstimate()
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedPlace = null,
                estimatedTiles = null,
                estimatedChunks = null,
                estimatedSizeBytes = null,
                downloadProgress = TileDownloadProgress.Idle,
            )
        }
    }

    fun setZoomRange(minZoom: Int, maxZoom: Int) {
        val clampedMin = minZoom.coerceIn(OfflineMapsUiState.MIN_SELECTABLE_ZOOM, OfflineMapsUiState.MAX_SELECTABLE_ZOOM)
        val clampedMax = maxZoom.coerceIn(clampedMin, OfflineMapsUiState.MAX_SELECTABLE_ZOOM)
        _uiState.update { it.copy(minZoom = clampedMin, maxZoom = clampedMax) }
        recomputeEstimate()
    }

    fun setTileSource(source: MapTileSource, custom: CustomTileSourceConfig) {
        viewModelScope.launch {
            settingsRepository.updateTileSource(source, custom)
            recomputeEstimate()
        }
    }

    private fun recomputeEstimate() {
        val state = _uiState.value
        val place = state.selectedPlace ?: return
        viewModelScope.launch {
            val (tiles, chunks) = withContext(Dispatchers.Default) {
                val plan = offlineMapManager.planChunks(place.boundingBox, state.minZoom, state.maxZoom)
                plan.totalTiles to plan.chunkCount
            }
            _uiState.update {
                it.copy(
                    estimatedTiles = tiles,
                    estimatedChunks = chunks,
                    estimatedSizeBytes = tiles * OfflineMapsUiState.AVG_TILE_BYTES,
                )
            }
        }
    }

    /** Hands the download to WorkManager so it keeps running once the user leaves this screen
     * or the app entirely. */
    fun startDownload() {
        val state = _uiState.value
        val place = state.selectedPlace ?: return
        downloadScheduler.enqueue(
            name = place.displayName,
            box = place.boundingBox,
            minZoom = state.minZoom,
            maxZoom = state.maxZoom,
            source = state.tileSource,
            custom = state.customTileSource,
            wifiOnly = state.downloadOnWifiOnly,
        )
    }

    fun cancelDownload() {
        downloadScheduler.cancel()
        _uiState.update { it.copy(downloadProgress = TileDownloadProgress.Idle, waitingForConstraints = false) }
        refreshCacheUsage()
    }

    fun setDownloadOnWifiOnly(wifiOnly: Boolean) {
        viewModelScope.launch { settingsRepository.updateDownloadOnWifiOnly(wifiOnly) }
    }

    fun deleteRegion(region: OfflineRegion) {
        val state = _uiState.value
        viewModelScope.launch {
            offlineMapManager.deleteRegion(region, state.tileSource, state.customTileSource).collect { progress ->
                if (progress is TileDownloadProgress.Completed) {
                    offlineRegionRepository.delete(region)
                    refreshCacheUsage()
                }
            }
        }
    }

    fun clearAllOfflineTiles() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { offlineMapManager.clearAllTiles() }
            offlineRegionRepository.deleteAll()
            refreshCacheUsage()
        }
    }

    private fun refreshCacheUsage() {
        viewModelScope.launch {
            val usage = withContext(Dispatchers.IO) { offlineMapManager.currentCacheUsageBytes() }
            _uiState.update { it.copy(totalCacheUsageBytes = usage) }
        }
    }
}
