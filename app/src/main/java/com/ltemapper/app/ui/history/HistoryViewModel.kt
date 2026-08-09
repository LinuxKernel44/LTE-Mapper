package com.ltemapper.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ltemapper.app.domain.model.SessionSummary
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.domain.usecase.CalculateSessionStatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HistoryUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: MappingRepository,
    private val calculateStats: CalculateSessionStatsUseCase,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = repository.observeSessions()
        .map { sessions ->
            val summaries = sessions.map { session -> calculateStats(session, repository.getSessionAggregate(session.id)) }
            HistoryUiState(sessions = summaries, isLoading = false)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch { repository.deleteSession(sessionId) }
    }
}
