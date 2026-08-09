package com.ltemapper.app.telephony

import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.SimInfo
import kotlinx.coroutines.flow.Flow

/** Non-root access to whatever cell signal information the current Android version/OEM exposes. */
interface TelephonyRepository {
    /** Event-driven stream of the serving cell's signal snapshot. Never throws; emits
     * [CellSignalInfo.unavailable] when permissions are missing or no data is exposed. */
    fun observeCellSignal(): Flow<CellSignalInfo>

    /** All currently active SIM subscriptions this app is allowed to see. Empty if none / denied. */
    fun getActiveSims(): List<SimInfo>
}
