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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.model.SignalThresholds
import com.ltemapper.app.mapping.SignalColor

/**
 * Signal quality is never conveyed by color alone: every swatch carries its label and dBm range
 * so the legend stays usable for color-blind users and screen readers. Entries wrap so the card
 * stays short instead of covering the map controls.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalLegend(thresholds: SignalThresholds, modifier: Modifier = Modifier) {
    val rows = listOf(
        SignalQuality.EXCELLENT to "≥ ${thresholds.excellentMinDbm}",
        SignalQuality.GOOD to "${thresholds.goodMinDbm}…${thresholds.excellentMinDbm}",
        SignalQuality.FAIR to "${thresholds.fairMinDbm}…${thresholds.goodMinDbm}",
        SignalQuality.WEAK to "${thresholds.weakMinDbm}…${thresholds.fairMinDbm}",
        SignalQuality.VERY_WEAK to "< ${thresholds.weakMinDbm}",
        SignalQuality.NO_SIGNAL to "none",
    )
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Signal quality (RSRP, dBm)", style = MaterialTheme.typography.labelMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                rows.forEach { (quality, range) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.semantics { contentDescription = "${quality.label}: $range dBm" },
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(SignalColor.compose(quality)),
                        )
                        Text(quality.label, style = MaterialTheme.typography.labelSmall)
                        Text(
                            range,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
