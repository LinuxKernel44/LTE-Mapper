package com.ltemapper.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.mapping.SignalColor
import java.util.Locale

/**
 * Horizontal bar chart of signal-quality percentages. Every bar carries its percentage as text
 * as well as color, so the distribution is legible without relying on color alone.
 */
@Composable
fun SignalDistributionChart(distributionPercent: Map<SignalQuality, Double>, modifier: Modifier = Modifier) {
    val orderedQualities = listOf(
        SignalQuality.EXCELLENT, SignalQuality.GOOD, SignalQuality.FAIR,
        SignalQuality.WEAK, SignalQuality.VERY_WEAK, SignalQuality.NO_SIGNAL,
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        orderedQualities.forEach { quality ->
            val percent = distributionPercent[quality] ?: 0.0
            DistributionRow(quality, percent)
        }
    }
}

@Composable
private fun DistributionRow(quality: SignalQuality, percent: Double) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(quality.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(80.dp))
        Box(modifier = Modifier.weight(1f).height(16.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = (percent / 100.0).toFloat().coerceIn(0f, 1f))
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(SignalColor.compose(quality)),
            )
        }
        Text(
            String.format(Locale.US, "%.0f%%", percent),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(40.dp),
        )
    }
}
