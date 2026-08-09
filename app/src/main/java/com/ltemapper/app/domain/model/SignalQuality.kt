package com.ltemapper.app.domain.model

/** Coarse, human-facing signal quality bucket derived primarily from RSRP. */
enum class SignalQuality(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    WEAK("Weak"),
    VERY_WEAK("Very weak"),
    NO_SIGNAL("No signal"),
    UNKNOWN("Unknown");
}

/**
 * User-configurable RSRP thresholds (in dBm) used to bucket a measurement into a [SignalQuality].
 * Values are the minimum RSRP (inclusive) required to reach that bucket, matching 3GPP-style
 * RSRP ranges. Defaults follow common industry rules of thumb.
 */
data class SignalThresholds(
    val excellentMinDbm: Int = -80,
    val goodMinDbm: Int = -90,
    val fairMinDbm: Int = -100,
    val weakMinDbm: Int = -110,
    // Anything below weakMinDbm is "very weak"; RSRP <= noSignalMaxDbm is treated as no signal.
    val noSignalMaxDbm: Int = -140,
) {
    companion object {
        val DEFAULT = SignalThresholds()
    }
}
