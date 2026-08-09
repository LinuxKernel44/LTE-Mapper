package com.ltemapper.app.ui.statistics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ltemapper.app.ui.components.SignalDistributionChart
import com.ltemapper.app.util.formatDistance
import com.ltemapper.app.util.formatDuration
import java.text.DateFormat
import java.util.Date

@Composable
fun StatisticsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatisticsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(modifier = modifier) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.sessions.isEmpty() -> Text(
                    "No sessions recorded yet.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SessionPicker(
                        sessions = state.sessions.map { it.id to it.startTime },
                        selectedId = state.selectedSessionId,
                        onSelect = viewModel::selectSession,
                    )

                    state.summary?.let { summary ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Session overview", style = MaterialTheme.typography.titleMedium)
                                val duration = (summary.session.endTime ?: summary.session.startTime) - summary.session.startTime
                                StatRow("Duration", formatDuration(duration))
                                StatRow("Distance", formatDistance(summary.session.distanceMeters))
                                StatRow("Measurements", "%,d".format(summary.session.measurementCount))
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("RSRP statistics", style = MaterialTheme.typography.titleMedium)
                                StatRow("Average RSRP", summary.avgRsrpDbm?.let { "%.1f dBm".format(it) } ?: "N/A")
                                StatRow("Median RSRP", summary.medianRsrpDbm?.let { "$it dBm" } ?: "N/A")
                                StatRow("Minimum RSRP", summary.minRsrpDbm?.let { "$it dBm" } ?: "N/A")
                                StatRow("Maximum RSRP", summary.maxRsrpDbm?.let { "$it dBm" } ?: "N/A")
                                StatRow("Average RSRQ", summary.avgRsrqDb?.let { "%.1f dB".format(it) } ?: "N/A")
                                StatRow("Average SNR", summary.avgSnrDb?.let { "%.1f dB".format(it) } ?: "N/A")
                            }
                        }

                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Signal distribution", style = MaterialTheme.typography.titleMedium)
                                SignalDistributionChart(summary.qualityDistributionPercent)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SessionPicker(sessions: List<Pair<Long, Long>>, selectedId: Long?, onSelect: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val selectedLabel = sessions.firstOrNull { it.first == selectedId }?.let { formatter.format(Date(it.second)) } ?: "Select a session"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Session") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            sessions.sortedByDescending { it.second }.forEach { (id, startTime) ->
                DropdownMenuItem(
                    text = { Text(formatter.format(Date(startTime))) },
                    onClick = { onSelect(id); expanded = false },
                )
            }
        }
    }
}
