package com.ltemapper.app.service

import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.LocationSample
import com.ltemapper.app.domain.model.SignalQuality
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class LiveMappingState(
    val isMapping: Boolean = false,
    val sessionId: Long? = null,
    val startTime: Long? = null,
    val measurementCount: Int = 0,
    val distanceMeters: Double = 0.0,
    val latestCellSignal: CellSignalInfo? = null,
    val latestLocation: LocationSample? = null,
    val latestQuality: SignalQuality = SignalQuality.UNKNOWN,
)

/**
 * Process-wide, in-memory bridge between [MappingForegroundService] (the actual collector) and
 * the UI layer, which never binds to the service directly — it just observes this state and
 * sends start/stop intents.
 */
@Singleton
class MappingSessionState @Inject constructor() {
    private val _state = MutableStateFlow(LiveMappingState())
    val state: StateFlow<LiveMappingState> = _state.asStateFlow()

    fun update(transform: (LiveMappingState) -> LiveMappingState) {
        _state.update(transform)
    }
}
