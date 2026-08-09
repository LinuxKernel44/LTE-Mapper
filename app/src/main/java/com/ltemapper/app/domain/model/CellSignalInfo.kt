package com.ltemapper.app.domain.model

/**
 * Snapshot of everything Android's non-root telephony APIs exposed at a point in time.
 * Every field the platform does not currently expose for this device/carrier/Android version
 * is left null rather than guessed — callers must render "N/A" for null fields, never fabricate.
 */
data class CellSignalInfo(
    val networkType: RanType,
    val connectionState: String?,
    val isRegistered: Boolean,

    // Signal levels
    val rssiDbm: Int? = null,
    val rsrpDbm: Int? = null,
    val rsrqDb: Int? = null,
    val rssnrDb: Int? = null,
    val cqi: Int? = null,
    val asuLevel: Int? = null,
    val signalLevel0to4: Int? = null,

    // Cell identity
    val pci: Int? = null,
    val earfcn: Int? = null,
    val cellId: Long? = null,
    val tac: Int? = null,
    val mcc: String? = null,
    val mnc: String? = null,

    // Operator / SIM
    val operatorName: String? = null,
    val operatorNumeric: String? = null,
    val subscriptionId: Int? = null,
    val simSlotIndex: Int? = null,

    val timestamp: Long = System.currentTimeMillis(),
) {
    companion object {
        fun unavailable(reason: String? = null): CellSignalInfo = CellSignalInfo(
            networkType = RanType.UNKNOWN,
            connectionState = reason,
            isRegistered = false,
        )
    }
}
