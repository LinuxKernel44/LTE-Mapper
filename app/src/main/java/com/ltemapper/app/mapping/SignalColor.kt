package com.ltemapper.app.mapping

import androidx.compose.ui.graphics.Color
import com.ltemapper.app.domain.model.SignalQuality

/**
 * Single source of truth for signal-quality colors, shared between Compose UI (legend, badges)
 * and osmdroid map overlays (which need plain ARGB ints, not Compose [Color]).
 */
object SignalColor {
    private val argb: Map<SignalQuality, Long> = mapOf(
        SignalQuality.EXCELLENT to 0xFF2E7D32,
        SignalQuality.GOOD to 0xFF8BC34A,
        SignalQuality.FAIR to 0xFFFFEB3B,
        SignalQuality.WEAK to 0xFFFF9800,
        SignalQuality.VERY_WEAK to 0xFFF44336,
        SignalQuality.NO_SIGNAL to 0xFF616161,
        SignalQuality.UNKNOWN to 0xFF9E9E9E,
    )

    fun compose(quality: SignalQuality): Color = Color(argb.getValue(quality))

    fun androidArgb(quality: SignalQuality): Int = argb.getValue(quality).toInt()
}
