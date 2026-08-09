package com.ltemapper.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ltemapper.app.R
import com.ltemapper.app.domain.model.MappingSettings
import com.ltemapper.app.domain.model.SignalThresholds
import com.ltemapper.app.export.ExportFormat
import com.ltemapper.app.util.hasBackgroundLocationPermission
import com.ltemapper.app.util.requestIgnoreBatteryOptimizationsIntent
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenPrivacy: () -> Unit,
    onOpenOfflineMaps: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val backgroundLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.setBackgroundMappingEnabled(true)
        } else {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Background location permission denied — background mapping stays off.")
            }
        }
    }
    val onRequestBackgroundMapping: () -> Unit = {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || context.hasBackgroundLocationPermission()) {
            viewModel.setBackgroundMappingEnabled(true)
        } else {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    // The battery-optimization exemption is granted/denied in a system dialog outside our
    // process, so re-check it whenever the user comes back to this screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentViewModel = rememberUpdatedState(viewModel)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentViewModel.value.refreshBatteryOptimizationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.statusMessage) {
        state.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.importCsv(it) }
    }

    var exportSessionId by remember { mutableStateOf<Long?>(null) }
    var pendingExportFormat by remember { mutableStateOf<ExportFormat?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val format = pendingExportFormat
        val sessionId = exportSessionId
        if (uri != null && format != null && sessionId != null) {
            viewModel.exportSession(sessionId, format, uri)
        }
        pendingExportFormat = null
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { SectionTitle("Mapping settings") }
            item {
                MappingSettingsCard(
                    settings = state.mappingSettings,
                    onChange = viewModel::updateMappingKnobs,
                    onRequestBackgroundMapping = onRequestBackgroundMapping,
                )
            }

            item { SectionTitle("Battery optimization") }
            item {
                BatteryOptimizationCard(
                    isIgnoring = state.isIgnoringBatteryOptimizations,
                )
            }

            item { SectionTitle("Signal thresholds (RSRP, dBm)") }
            item {
                ThresholdsCard(
                    thresholds = state.mappingSettings.signalThresholds,
                    onChange = viewModel::updateThresholds,
                )
            }

            item { SectionTitle("Multi-SIM") }
            item {
                SimInfoCard(
                    sims = state.activeSims,
                    monitoredSubscriptionId = state.mappingSettings.monitoredSubscriptionId,
                    onSelect = viewModel::setMonitoredSubscriptionId,
                )
            }

            item { SectionTitle("Offline maps") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Download map tiles for a country, region, or city so the map still works without a connection.")
                        Button(onClick = onOpenOfflineMaps) { Text("Manage offline maps") }
                    }
                }
            }

            item { SectionTitle("Export") }
            item {
                ExportCard(
                    sessions = state.sessions.map { it.id to it.startTime },
                    onExport = { sessionId, format ->
                        exportSessionId = sessionId
                        pendingExportFormat = format
                        exportLauncher.launch("lte-mapper-session.${format.extension}")
                    },
                )
            }

            item { SectionTitle("Import") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Import a previously exported LTE Mapper CSV file. Malformed rows are skipped automatically.")
                        Button(onClick = { importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "*/*")) }) {
                            Text("Import CSV")
                        }
                    }
                }
            }

            item { SectionTitle("Privacy") }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Review what data LTE Mapper stores and delete it at any time.")
                        Button(onClick = onOpenPrivacy) { Text("Open privacy settings") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MappingSettingsCard(
    settings: MappingSettings,
    onChange: (Float, Long, Long, Boolean) -> Unit,
    onRequestBackgroundMapping: () -> Unit,
) {
    var minDistance by remember(settings.minDistanceMeters) { mutableStateOf(settings.minDistanceMeters) }
    var minInterval by remember(settings.minIntervalMillis) { mutableStateOf(settings.minIntervalMillis.toFloat()) }
    var locationInterval by remember(settings.locationUpdateIntervalMillis) { mutableStateOf(settings.locationUpdateIntervalMillis.toFloat()) }
    val backgroundEnabled = settings.backgroundMappingEnabled

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Minimum distance between points: ${minDistance.toInt()} m")
            Slider(
                value = minDistance,
                onValueChange = { minDistance = it },
                onValueChangeFinished = { onChange(minDistance, minInterval.toLong(), locationInterval.toLong(), backgroundEnabled) },
                valueRange = 5f..10f,
            )
            Text("Minimum time between points: ${(minInterval / 1000).toInt()} s")
            Slider(
                value = minInterval,
                onValueChange = { minInterval = it },
                onValueChangeFinished = { onChange(minDistance, minInterval.toLong(), locationInterval.toLong(), backgroundEnabled) },
                valueRange = 1000f..5000f,
            )
            Text("GPS update interval: ${(locationInterval / 1000).toInt()} s")
            Slider(
                value = locationInterval,
                onValueChange = { locationInterval = it },
                onValueChangeFinished = { onChange(minDistance, minInterval.toLong(), locationInterval.toLong(), backgroundEnabled) },
                valueRange = 1000f..10000f,
            )
            HorizontalDivider()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Background mapping")
                    Text(
                        "Keep mapping after you leave the app or lock the screen (requires \"Allow all the time\" location). " +
                            "Off by default: leaving the app stops mapping and closes out the session.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = backgroundEnabled,
                    onCheckedChange = { turnedOn ->
                        if (turnedOn) {
                            // Gated on ACCESS_BACKGROUND_LOCATION: onRequestBackgroundMapping
                            // only flips the setting on once that permission is confirmed granted,
                            // so `backgroundEnabled` (read straight from settings) updates itself
                            // once that round-trips back through the ViewModel.
                            onRequestBackgroundMapping()
                        } else {
                            onChange(minDistance, minInterval.toLong(), locationInterval.toLong(), false)
                        }
                    },
                )
            }
            Text(
                "GPS and cellular measurements can noticeably increase battery use while mapping is active.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BatteryOptimizationCard(isIgnoring: Boolean) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.battery_optimization_rationale), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (isIgnoring) {
                    stringResource(R.string.battery_optimization_status_exempt)
                } else {
                    stringResource(R.string.battery_optimization_status_not_exempt)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isIgnoring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!isIgnoring) {
                Button(onClick = { context.startActivity(context.requestIgnoreBatteryOptimizationsIntent()) }) {
                    Text(stringResource(R.string.battery_optimization_button))
                }
            }
        }
    }
}

@Composable
private fun ThresholdsCard(thresholds: SignalThresholds, onChange: (SignalThresholds) -> Unit) {
    var excellent by remember(thresholds.excellentMinDbm) { mutableStateOf(thresholds.excellentMinDbm.toString()) }
    var good by remember(thresholds.goodMinDbm) { mutableStateOf(thresholds.goodMinDbm.toString()) }
    var fair by remember(thresholds.fairMinDbm) { mutableStateOf(thresholds.fairMinDbm.toString()) }
    var weak by remember(thresholds.weakMinDbm) { mutableStateOf(thresholds.weakMinDbm.toString()) }

    fun commit() {
        val e = excellent.toIntOrNull() ?: thresholds.excellentMinDbm
        val g = good.toIntOrNull() ?: thresholds.goodMinDbm
        val f = fair.toIntOrNull() ?: thresholds.fairMinDbm
        val w = weak.toIntOrNull() ?: thresholds.weakMinDbm
        onChange(thresholds.copy(excellentMinDbm = e, goodMinDbm = g, fairMinDbm = f, weakMinDbm = w))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ThresholdField("Excellent ≥", excellent) { excellent = it; commit() }
            ThresholdField("Good ≥", good) { good = it; commit() }
            ThresholdField("Fair ≥", fair) { fair = it; commit() }
            ThresholdField("Weak ≥", weak) { weak = it; commit() }
            Text("Below \"Weak\" is classified Very weak.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ThresholdField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("$label (dBm)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
    )
}

@Composable
private fun SimInfoCard(
    sims: List<com.ltemapper.app.domain.model.SimInfo>,
    monitoredSubscriptionId: Int?,
    onSelect: (Int?) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (sims.isEmpty()) {
                Text("No SIM information available.", style = MaterialTheme.typography.bodyMedium)
            } else if (sims.size == 1) {
                val sim = sims.single()
                Text(
                    "Slot ${sim.simSlotIndex + 1}: ${sim.carrierName ?: sim.displayName ?: "Unknown"}",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                Text(
                    "Multiple SIMs detected — choose which one to record signal from.",
                    style = MaterialTheme.typography.bodySmall,
                )
                SimRow(
                    label = "Automatic (follow the default data SIM)",
                    selected = monitoredSubscriptionId == null,
                    onClick = { onSelect(null) },
                )
                sims.forEach { sim ->
                    SimRow(
                        label = "Slot ${sim.simSlotIndex + 1}: ${sim.carrierName ?: sim.displayName ?: "Unknown"}" +
                            if (sim.isDefaultData) " (default data)" else "",
                        selected = monitoredSubscriptionId == sim.subscriptionId,
                        onClick = { onSelect(sim.subscriptionId) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SimRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ExportCard(sessions: List<Pair<Long, Long>>, onExport: (Long, ExportFormat) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var selectedSessionId by remember(sessions) { mutableStateOf(sessions.maxByOrNull { it.second }?.first) }
    val formatter = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    val selectedLabel = sessions.firstOrNull { it.first == selectedSessionId }?.let { formatter.format(Date(it.second)) } ?: "No sessions"

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (sessions.isEmpty()) {
                Text("Record a session before exporting.")
            } else {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = selectedLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Session to export") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        sessions.sortedByDescending { it.second }.forEach { (id, startTime) ->
                            DropdownMenuItem(
                                text = { Text(formatter.format(Date(startTime))) },
                                onClick = { selectedSessionId = id; expanded = false },
                            )
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportFormat.entries.forEach { format ->
                        OutlinedButton(onClick = { selectedSessionId?.let { onExport(it, format) } }) {
                            Text(format.label)
                        }
                    }
                }
            }
        }
    }
}
