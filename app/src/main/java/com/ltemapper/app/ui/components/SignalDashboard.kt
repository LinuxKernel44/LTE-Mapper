package com.ltemapper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.mapping.SignalColor
import com.ltemapper.app.util.formatDb
import com.ltemapper.app.util.formatDbm
import com.ltemapper.app.util.formatDistance
import com.ltemapper.app.util.formatDuration
import com.ltemapper.app.util.formatOrNa

/**
 * Compact live readout. Metrics wrap in a [FlowRow] rather than being forced onto one line, so
 * long values (a 7-digit cell ID next to "-116 dBm") reflow instead of colliding on narrow phones.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalDashboard(
    signal: CellSignalInfo?,
    quality: SignalQuality,
    gpsAccuracyMeters: Float?,
    measurementCount: Int,
    distanceMeters: Double,
    durationMillis: Long,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(6.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QualityDot(SignalColor.compose(quality))
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = signal?.networkType?.label ?: "No signal",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = quality.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalColor.compose(quality),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        signal?.operatorName ?: "Unknown operator",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = onToggleExpanded, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                        contentDescription = if (expanded) "Collapse details" else "Expand details",
                    )
                }
            }

            // Always-visible essentials, even when collapsed.
            MetricFlow(
                listOf(
                    "RSRP" to formatDbm(signal?.rsrpDbm),
                    "GPS" to (gpsAccuracyMeters?.let { "±${it.toInt()} m" } ?: "N/A"),
                    "Points" to "%,d".format(measurementCount),
                ),
            )

            if (expanded) {
                MetricFlow(
                    listOf(
                        "RSRQ" to formatDb(signal?.rsrqDb),
                        "SNR" to formatDb(signal?.rssnrDb),
                        "PCI" to formatOrNa(signal?.pci),
                        "EARFCN" to formatOrNa(signal?.earfcn),
                        "Cell ID" to formatOrNa(signal?.cellId),
                        "TAC" to formatOrNa(signal?.tac),
                    ),
                )
                MetricFlow(
                    listOf(
                        "Distance" to formatDistance(distanceMeters),
                        "Duration" to formatDuration(durationMillis),
                    ),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricFlow(items: List<Pair<String, String>>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { (label, value) ->
            Column(modifier = Modifier.widthIn(min = 68.dp)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QualityDot(color: Color) {
    Box(
        modifier = Modifier
            .size(12.dp)
            .clip(CircleShape)
            .background(color),
    )
}
