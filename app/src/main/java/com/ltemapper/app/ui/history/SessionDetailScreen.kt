package com.ltemapper.app.ui.history

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ltemapper.app.domain.model.MapDisplayMode
import com.ltemapper.app.export.ExportFormat
import com.ltemapper.app.ui.components.SignalDistributionChart
import com.ltemapper.app.ui.map.OsmMapView
import com.ltemapper.app.util.formatDistance
import com.ltemapper.app.util.formatDuration

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SessionDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SessionDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingFormat by remember { mutableStateOf<ExportFormat?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingFormat
        if (uri != null && format != null) {
            viewModel.exportTo(uri, format)
        }
        pendingFormat = null
    }

    LaunchedEffect(state.lastExportMessage) {
        state.lastExportMessage?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Session detail") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        // Scrollable: map + stats + chart + export buttons exceed a phone screen, and without
        // this the export actions were simply clipped off the bottom.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                OsmMapView(
                    points = state.points,
                    displayMode = MapDisplayMode.POINTS,
                    tileSource = state.tileSource,
                    customTileSource = state.customTileSource,
                    followUser = false,
                    currentLocation = null,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.isExporting) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                state.summary?.let { summary ->
                    val duration = (summary.session.endTime ?: summary.session.startTime) - summary.session.startTime
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatColumn("Duration", formatDuration(duration))
                        StatColumn("Distance", formatDistance(summary.session.distanceMeters))
                        StatColumn("Points", "%,d".format(summary.session.measurementCount))
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatColumn("Min RSRP", summary.minRsrpDbm?.let { "$it dBm" } ?: "N/A")
                        StatColumn("Max RSRP", summary.maxRsrpDbm?.let { "$it dBm" } ?: "N/A")
                        StatColumn("Median RSRP", summary.medianRsrpDbm?.let { "$it dBm" } ?: "N/A")
                    }
                    Text("Signal distribution", style = MaterialTheme.typography.titleMedium)
                    SignalDistributionChart(summary.qualityDistributionPercent)
                }

                Text("Export session", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { format ->
                        Button(onClick = {
                            pendingFormat = format
                            createDocumentLauncher.launch(defaultFileName(format))
                        }) {
                            Text(format.label)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatColumn(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun defaultFileName(format: ExportFormat): String = "lte-mapper-session.${format.extension}"
