package com.ltemapper.app.ui.history

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.domain.model.SessionSummary
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.domain.usecase.CalculateSessionStatsUseCase
import com.ltemapper.app.export.CsvExporter
import com.ltemapper.app.export.ExportFormat
import com.ltemapper.app.export.GeoJsonExporter
import com.ltemapper.app.export.KmlExporter
import com.ltemapper.app.mapping.MapPoint
import com.ltemapper.app.mapping.toMapPoint
import com.ltemapper.app.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SessionDetailUiState(
    val summary: SessionSummary? = null,
    val points: List<MapPoint> = emptyList(),
    val tileSource: MapTileSource = MapTileSource.MAPNIK,
    val customTileSource: CustomTileSourceConfig = CustomTileSourceConfig(),
    val isExporting: Boolean = false,
    val lastExportMessage: String? = null,
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val repository: MappingRepository,
    private val calculateStats: CalculateSessionStatsUseCase,
    private val csvExporter: CsvExporter,
    private val geoJsonExporter: GeoJsonExporter,
    private val kmlExporter: KmlExporter,
    private val settingsRepository: com.ltemapper.app.data.repository.SettingsRepository,
) : ViewModel() {

    private val sessionId: Long = checkNotNull(savedStateHandle.get<String>(Destination.SessionDetail.ARG_SESSION_ID)).toLong()

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { settings ->
                _uiState.value = _uiState.value.copy(
                    tileSource = settings.tileSource,
                    customTileSource = settings.customTileSource,
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val session = repository.getSession(sessionId) ?: return@launch
            val aggregate = repository.getSessionAggregate(sessionId)
            val summary = calculateStats(session, aggregate)
            val points = repository.getMeasurementsForDisplay(sessionId, NetworkFilter.ALL).map { it.toMapPoint() }
            _uiState.value = _uiState.value.copy(summary = summary, points = points)
        }
    }

    fun exportTo(uri: Uri, format: ExportFormat) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isExporting = true, lastExportMessage = null)
            val count = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    when (format) {
                        ExportFormat.CSV -> csvExporter.export(sessionId, stream)
                        ExportFormat.GEOJSON -> geoJsonExporter.export(sessionId, stream)
                        ExportFormat.KML -> kmlExporter.export(sessionId, stream)
                    }
                } ?: 0
            }
            _uiState.value = _uiState.value.copy(isExporting = false, lastExportMessage = "Exported $count measurements")
        }
    }
}
