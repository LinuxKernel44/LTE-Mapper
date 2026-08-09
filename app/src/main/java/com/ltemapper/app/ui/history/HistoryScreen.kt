package com.ltemapper.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ltemapper.app.domain.model.SessionSummary
import com.ltemapper.app.util.formatDistance
import com.ltemapper.app.util.formatDuration
import java.text.DateFormat
import java.util.Date

@Composable
fun HistoryScreen(
    onOpenSession: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var pendingDelete by remember { mutableStateOf<Long?>(null) }

    Scaffold(modifier = modifier) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                state.sessions.isEmpty() -> Text(
                    "No mapping sessions yet. Start mapping from the Map tab.",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.sessions, key = { it.session.id }) { summary ->
                        SessionCard(
                            summary = summary,
                            onClick = { onOpenSession(summary.session.id) },
                            onDelete = { pendingDelete = summary.session.id },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete session?") },
            text = { Text("This permanently removes this session and all of its measurements.") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteSession(id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SessionCard(summary: SessionSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(summary.session.startTime)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete session")
                }
            }
            val duration = (summary.session.endTime ?: summary.session.startTime) - summary.session.startTime
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue("Duration", formatDuration(duration))
                LabeledValue("Distance", formatDistance(summary.session.distanceMeters))
                LabeledValue("Points", "%,d".format(summary.session.measurementCount))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                LabeledValue("Min RSRP", summary.minRsrpDbm?.let { "$it dBm" } ?: "N/A")
                LabeledValue("Max RSRP", summary.maxRsrpDbm?.let { "$it dBm" } ?: "N/A")
                LabeledValue("Avg RSRP", summary.avgRsrpDbm?.let { "%.0f dBm".format(it) } ?: "N/A")
            }
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
