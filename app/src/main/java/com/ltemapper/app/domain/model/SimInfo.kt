package com.ltemapper.app.domain.model

data class SimInfo(
    val subscriptionId: Int,
    val simSlotIndex: Int,
    val carrierName: String?,
    val displayName: String?,
    val isDefaultData: Boolean,
    val mcc: String?,
    val mnc: String?,
)
