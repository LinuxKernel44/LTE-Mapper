package com.ltemapper.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ltemapper.app.data.repository.SettingsRepository
import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.MappingSettings
import com.ltemapper.app.domain.model.SignalThresholds
import com.ltemapper.app.domain.model.SimInfo
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.export.CsvExporter
import com.ltemapper.app.export.CsvImporter
import com.ltemapper.app.export.ExportFormat
import com.ltemapper.app.export.GeoJsonExporter
import com.ltemapper.app.export.KmlExporter
import com.ltemapper.app.telephony.TelephonyRepository
import com.ltemapper.app.util.isIgnoringBatteryOptimizations
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val mappingSettings: MappingSettings = MappingSettings.DEFAULT,
    val sessions: List<MappingSession> = emptyList(),
    val activeSims: List<SimInfo> = emptyList(),
    val statusMessage: String? = null,
    val isIgnoringBatteryOptimizations: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val mappingRepository: MappingRepository,
    private val telephonyRepository: TelephonyRepository,
    private val csvExporter: CsvExporter,
    private val geoJsonExporter: GeoJsonExporter,
    private val kmlExporter: KmlExporter,
    private val csvImporter: CsvImporter,
) : ViewModel() {

    private val statusMessage = MutableStateFlow<String?>(null)
    private val ignoringBatteryOptimizations = MutableStateFlow(context.isIgnoringBatteryOptimizations())

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        mappingRepository.observeSessions(),
        statusMessage,
        ignoringBatteryOptimizations,
    ) { settings, sessions, status, ignoringBattery ->
        SettingsUiState(
            mappingSettings = settings,
            sessions = sessions,
            activeSims = runCatching { telephonyRepository.getActiveSims() }.getOrDefault(emptyList()),
            statusMessage = status,
            isIgnoringBatteryOptimizations = ignoringBattery,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    /** Re-checks the OS battery-optimization exemption; call when the screen resumes, since the
     * user grants/denies it from a system dialog outside our process. */
    fun refreshBatteryOptimizationStatus() {
        ignoringBatteryOptimizations.value = context.isIgnoringBatteryOptimizations()
    }

    fun updateMappingKnobs(minDistanceMeters: Float, minIntervalMillis: Long, locationUpdateIntervalMillis: Long, backgroundMappingEnabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateMappingKnobs(minDistanceMeters, minIntervalMillis, locationUpdateIntervalMillis, backgroundMappingEnabled)
        }
    }

    fun updateThresholds(thresholds: SignalThresholds) {
        viewModelScope.launch { settingsRepository.updateThresholds(thresholds) }
    }

    /** Pins mapping to a specific SIM's signal on a dual-SIM device; null goes back to following
     * the default data SIM. */
    fun setMonitoredSubscriptionId(subscriptionId: Int?) {
        viewModelScope.launch { settingsRepository.updateMonitoredSubscriptionId(subscriptionId) }
    }

    /** Sets just the background-mapping flag, leaving the other mapping knobs untouched. Split
     * out from [updateMappingKnobs] so the caller (a permission-gated toggle) doesn't need to know
     * the current slider values to flip this one field. */
    fun setBackgroundMappingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val current = uiState.value.mappingSettings
            settingsRepository.updateMappingKnobs(
                current.minDistanceMeters,
                current.minIntervalMillis,
                current.locationUpdateIntervalMillis,
                enabled,
            )
        }
    }

    fun exportSession(sessionId: Long, format: ExportFormat, uri: Uri) {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    when (format) {
                        ExportFormat.CSV -> csvExporter.export(sessionId, stream)
                        ExportFormat.GEOJSON -> geoJsonExporter.export(sessionId, stream)
                        ExportFormat.KML -> kmlExporter.export(sessionId, stream)
                    }
                } ?: 0
            }
            statusMessage.value = "Exported $count measurements"
        }
    }

    fun importCsv(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input -> csvImporter.import(input) }
            }
            statusMessage.value = if (result == null) {
                "Import failed: could not open file"
            } else {
                "Imported ${result.importedCount} rows, skipped ${result.skippedCount}"
            }
        }
    }

    fun deleteAllData() {
        viewModelScope.launch { mappingRepository.deleteAllData() }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }
}
