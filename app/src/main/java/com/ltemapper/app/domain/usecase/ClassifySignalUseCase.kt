package com.ltemapper.app.domain.usecase

import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.domain.model.SignalThresholds
import javax.inject.Inject

/**
 * Buckets a raw RSRP value (dBm) into a [SignalQuality] using the user's configured thresholds.
 * A null RSRP (device/carrier does not expose it) always maps to [SignalQuality.UNKNOWN] — it is
 * never guessed from other fields.
 */
class ClassifySignalUseCase @Inject constructor() {
    operator fun invoke(rsrpDbm: Int?, thresholds: SignalThresholds = SignalThresholds.DEFAULT): SignalQuality {
        if (rsrpDbm == null) return SignalQuality.UNKNOWN
        return when {
            rsrpDbm <= thresholds.noSignalMaxDbm -> SignalQuality.NO_SIGNAL
            rsrpDbm >= thresholds.excellentMinDbm -> SignalQuality.EXCELLENT
            rsrpDbm >= thresholds.goodMinDbm -> SignalQuality.GOOD
            rsrpDbm >= thresholds.fairMinDbm -> SignalQuality.FAIR
            rsrpDbm >= thresholds.weakMinDbm -> SignalQuality.WEAK
            else -> SignalQuality.VERY_WEAK
        }
    }
}
