package com.ltemapper.app.domain.model

/** Radio access technology family, coarse enough to be stable across Android/OEM versions. */
enum class RanType(val label: String) {
    GSM("2G"),
    UMTS("3G"),
    LTE("4G LTE"),
    NR("5G"),
    UNKNOWN("Unknown");

    val isLte: Boolean get() = this == LTE
    val isNr: Boolean get() = this == NR
}

enum class NetworkFilter(val label: String) {
    ALL("All cellular"),
    LTE_ONLY("LTE only"),
    NR_ONLY("5G only");

    fun matches(type: RanType): Boolean = when (this) {
        ALL -> true
        LTE_ONLY -> type == RanType.LTE
        NR_ONLY -> type == RanType.NR
    }
}
