package com.ltemapper.app.ui.map

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.LocationSample
import com.ltemapper.app.domain.model.MapDisplayMode
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.domain.usecase.ClassifySignalUseCase
import com.ltemapper.app.data.repository.SettingsRepository
import com.ltemapper.app.location.LocationTracker
import com.ltemapper.app.mapping.toMapPoint
import com.ltemapper.app.service.MappingForegroundService
import com.ltemapper.app.service.MappingSessionState
import com.ltemapper.app.telephony.TelephonyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mappingRepository: MappingRepository,
    private val telephonyRepository: TelephonyRepository,
    private val locationTracker: LocationTracker,
    private val settingsRepository: SettingsRepository,
    private val sessionState: MappingSessionState,
    private val classifySignal: ClassifySignalUseCase,
) : ViewModel() {

    private val displayMode = MutableStateFlow(MapDisplayMode.POINTS)
    private val networkFilter = MutableStateFlow(NetworkFilter.ALL)
    private val followUser = MutableStateFlow(true)
    private val showDashboard = MutableStateFlow(false)

    private val latestSessionId = mappingRepository.observeSessions()
        .map { sessions -> sessions.firstOrNull()?.id }
        .distinctUntilChanged()

    private val effectiveSessionId = combine(
        sessionState.state.map { it.sessionId }.distinctUntilChanged(),
        latestSessionId,
    ) { liveId, latestId -> liveId ?: latestId }

    private val isMapping = sessionState.state.map { it.isMapping }.distinctUntilChanged()

    private val points = combine(effectiveSessionId, networkFilter, isMapping) { id, filter, mapping ->
        Triple(id, filter, mapping)
    }.flatMapLatest { (id, filter, mapping) ->
        if (id == null) {
            flowOf(emptyList())
        } else {
            flow {
                // Poll only while a session is actually recording. A finished session's points
                // never change, so re-querying (and re-emitting a new list, which forced the map
                // to redraw) every few seconds was pure waste.
                while (currentCoroutineContext().isActive) {
                    val fetched = runCatching { mappingRepository.getMeasurementsForDisplay(id, filter) }.getOrDefault(emptyList())
                    emit(fetched.map { it.toMapPoint() })
                    if (!mapping) break
                    delay(POINTS_REFRESH_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Live "current signal" preview, independent of whether a session is being recorded.
     *
     * Two details matter here. The `distinctUntilChanged` drops emissions where only the
     * timestamp moved — the modem re-reports identical readings constantly, and each one used to
     * recompose the entire screen. The `onStart(null)` seed matters because [combine] emits
     * nothing until *every* source has emitted at least once: without it, a device that hasn't
     * got a GPS fix yet (indoors) would leave the whole map screen stuck on its initial state.
     */
    private val liveTelephony: Flow<CellSignalInfo?> = telephonyRepository.observeCellSignal()
        .distinctUntilChanged { old, new -> old.copy(timestamp = 0) == new.copy(timestamp = 0) }
        .onStart<CellSignalInfo?> { emit(null) }

    private val liveLocation: Flow<LocationSample?> =
        locationTracker.observeLocation(LOCATION_PREVIEW_INTERVAL_MS).onStart<LocationSample?> { emit(null) }

    /** Ticks while a session runs so the displayed duration actually advances, instead of only
     * moving whenever some unrelated flow happened to emit. */
    private val mappingDuration: Flow<Long> = sessionState.state
        .map { it.startTime }
        .distinctUntilChanged()
        .flatMapLatest { startTime ->
            if (startTime == null) {
                flowOf(0L)
            } else {
                flow {
                    while (currentCoroutineContext().isActive) {
                        emit(System.currentTimeMillis() - startTime)
                        delay(DURATION_TICK_INTERVAL_MS)
                    }
                }
            }
        }

    val uiState: StateFlow<MapUiState> = combine(
        sessionState.state,
        points,
        combine(displayMode, networkFilter, followUser, showDashboard, ::Toggles),
        combine(liveTelephony, liveLocation, mappingDuration, ::Triple),
        settingsRepository.settingsFlow,
    ) { live, pts, toggles, (tele, loc, duration), settings ->
        val effectiveSignal = if (live.isMapping) live.latestCellSignal else tele
        val effectiveLocation = if (live.isMapping) live.latestLocation ?: loc else loc
        val quality = classifySignal(effectiveSignal?.rsrpDbm, settings.signalThresholds)
        MapUiState(
            isMapping = live.isMapping,
            currentSignal = effectiveSignal,
            currentSignalQuality = quality,
            currentLocation = effectiveLocation,
            measurementCount = live.measurementCount,
            distanceMeters = live.distanceMeters,
            mappingDurationMillis = duration,
            points = pts,
            displayMode = toggles.displayMode,
            networkFilter = toggles.networkFilter,
            followUser = toggles.followUser,
            showDashboard = toggles.showDashboard,
            signalThresholds = settings.signalThresholds,
            tileSource = settings.tileSource,
            customTileSource = settings.customTileSource,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    private data class Toggles(
        val displayMode: MapDisplayMode,
        val networkFilter: NetworkFilter,
        val followUser: Boolean,
        val showDashboard: Boolean,
    )

    fun startMapping() = MappingForegroundService.start(context)
    fun stopMapping() = MappingForegroundService.stop(context)

    fun setDisplayMode(mode: MapDisplayMode) { displayMode.value = mode }
    fun setNetworkFilter(filter: NetworkFilter) { networkFilter.value = filter }

    /** Persisted, because the offline downloader must target the same source the map displays. */
    fun setTileSource(source: MapTileSource) {
        viewModelScope.launch {
            settingsRepository.updateTileSource(source, uiState.value.customTileSource)
        }
    }
    fun toggleFollowUser() { followUser.value = !followUser.value }
    fun toggleDashboard() { showDashboard.value = !showDashboard.value }

    companion object {
        private const val POINTS_REFRESH_INTERVAL_MS = 3_000L
        private const val LOCATION_PREVIEW_INTERVAL_MS = 4_000L
        private const val DURATION_TICK_INTERVAL_MS = 1_000L
    }
}
