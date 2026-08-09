package com.ltemapper.app.ui.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.SessionSummary
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.domain.usecase.CalculateSessionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class StatisticsUiState(
    val sessions: List<MappingSession> = emptyList(),
    val selectedSessionId: Long? = null,
    val summary: SessionSummary? = null,
    val isLoading: Boolean = true,
)

@HiltViewModel
class StatisticsViewModel @Inject constructor(
    private val repository: MappingRepository,
    private val calculateStats: CalculateSessionStatsUseCase,
) : ViewModel() {

    private val selectedSessionId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<StatisticsUiState> = combine(repository.observeSessions(), selectedSessionId) { sessions, selectedId ->
        sessions to (selectedId ?: sessions.firstOrNull()?.id)
    }.flatMapLatest { (sessions, effectiveId) ->
        flow {
            if (effectiveId == null) {
                emit(StatisticsUiState(sessions = sessions, isLoading = false))
            } else {
                val session = sessions.firstOrNull { it.id == effectiveId }
                val summary = session?.let { calculateStats(it, repository.getSessionAggregate(effectiveId)) }
                emit(StatisticsUiState(sessions = sessions, selectedSessionId = effectiveId, summary = summary, isLoading = false))
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatisticsUiState())

    fun selectSession(sessionId: Long) {
        selectedSessionId.value = sessionId
    }
}
