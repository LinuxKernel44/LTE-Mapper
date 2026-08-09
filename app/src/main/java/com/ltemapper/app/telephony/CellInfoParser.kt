package com.ltemapper.app.telephony

import android.os.Build
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrengthNr
import android.telephony.CellIdentityNr
import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.RanType

/**
 * Turns a platform [CellInfo] into our RAT-agnostic [CellSignalInfo].
 *
 * Only genuinely available fields are populated per radio access technology — LTE-specific
 * concepts (PCI, EARFCN, RSRP/RSRQ/RSSNR, CQI) are left null on GSM/WCDMA cells rather than
 * approximated, per the "never fake values" requirement. For 5G NR, the SS-RSRP/SS-RSRQ/SS-SINR
 * beam-level measurements are surfaced through the same rsrp/rsrq/rssnr fields since they play
 * the equivalent role for NR as RSRP/RSRQ/RSSNR do for LTE; NR-ARFCN is surfaced through the
 * `earfcn` field for the same reason. The Tracking/Location Area Code field (`tac`) is reused to
 * carry GSM/WCDMA's LAC, since both represent the same "area code" concept for their RAT.
 */
object CellInfoParser {

    fun parse(cellInfo: CellInfo, subscriptionId: Int?, simSlotIndex: Int?, operatorName: String?): CellSignalInfo? {
        return when (cellInfo) {
            is CellInfoLte -> parseLte(cellInfo, subscriptionId, simSlotIndex, operatorName)
            is CellInfoWcdma -> parseWcdma(cellInfo, subscriptionId, simSlotIndex, operatorName)
            is CellInfoGsm -> parseGsm(cellInfo, subscriptionId, simSlotIndex, operatorName)
            is CellInfoCdma -> parseCdma(cellInfo, subscriptionId, simSlotIndex, operatorName)
            else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && cellInfo is CellInfoNr) {
                parseNr(cellInfo, subscriptionId, simSlotIndex, operatorName)
            } else {
                null
            }
        }
    }

    private fun parseLte(cell: CellInfoLte, subId: Int?, slot: Int?, operatorName: String?): CellSignalInfo {
        val identity = cell.cellIdentity
        val strength = cell.cellSignalStrength
        val mcc = runCatching { identity.mccString }.getOrNull()
        val mnc = runCatching { identity.mncString }.getOrNull()
        val ci = identity.ci.takeIfValid()
        val pci = identity.pci.takeIfValid()
        val tac = identity.tac.takeIfValid()
        val earfcn = identity.earfcn.takeIfValid()

        val rssi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) strength.rssi.takeIfValidDbm() else null
        val cqi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) strength.cqi.takeIfValid() else null

        return CellSignalInfo(
            networkType = RanType.LTE,
            connectionState = if (cell.isRegistered) "REGISTERED" else "NEIGHBOR",
            isRegistered = cell.isRegistered,
            rssiDbm = rssi,
            rsrpDbm = strength.rsrp.takeIfValidDbm(),
            rsrqDb = strength.rsrq.takeIfValidDbm(),
            rssnrDb = strength.rssnr.takeIfValidSnr(),
            cqi = cqi,
            asuLevel = strength.asuLevel,
            signalLevel0to4 = strength.level,
            pci = pci,
            earfcn = earfcn,
            cellId = ci?.toLong(),
            tac = tac,
            mcc = mcc,
            mnc = mnc,
            operatorName = operatorName,
            subscriptionId = subId,
            simSlotIndex = slot,
        )
    }

    private fun parseNr(cell: CellInfoNr, subId: Int?, slot: Int?, operatorName: String?): CellSignalInfo {
        val identity = cell.cellIdentity as? CellIdentityNr
        val strength = cell.cellSignalStrength as? CellSignalStrengthNr
        val mcc = runCatching { identity?.mccString }.getOrNull()
        val mnc = runCatching { identity?.mncString }.getOrNull()
        val nci = runCatching { identity?.nci }.getOrNull()?.takeIfValid()
        val pci = runCatching { identity?.pci }.getOrNull()?.takeIfValid()
        val tac = runCatching { identity?.tac }.getOrNull()?.takeIfValid()
        val nrarfcn = runCatching { identity?.nrarfcn }.getOrNull()?.takeIfValid()

        return CellSignalInfo(
            networkType = RanType.NR,
            connectionState = if (cell.isRegistered) "REGISTERED" else "NEIGHBOR",
            isRegistered = cell.isRegistered,
            rsrpDbm = strength?.ssRsrp?.takeIfValidDbm(),
            rsrqDb = strength?.ssRsrq?.takeIfValidDbm(),
            rssnrDb = strength?.ssSinr?.takeIfValidSnr(),
            asuLevel = strength?.asuLevel,
            signalLevel0to4 = strength?.level,
            pci = pci,
            earfcn = nrarfcn,
            cellId = nci,
            tac = tac,
            mcc = mcc,
            mnc = mnc,
            operatorName = operatorName,
            subscriptionId = subId,
            simSlotIndex = slot,
        )
    }

    private fun parseWcdma(cell: CellInfoWcdma, subId: Int?, slot: Int?, operatorName: String?): CellSignalInfo {
        val identity = cell.cellIdentity
        val strength = cell.cellSignalStrength
        val mcc = runCatching { identity.mccString }.getOrNull()
        val mnc = runCatching { identity.mncString }.getOrNull()
        val cid = identity.cid.takeIfValid()
        val lac = identity.lac.takeIfValid()

        return CellSignalInfo(
            networkType = RanType.UMTS,
            connectionState = if (cell.isRegistered) "REGISTERED" else "NEIGHBOR",
            isRegistered = cell.isRegistered,
            rssiDbm = strength.dbm.takeIfValidDbm(),
            asuLevel = strength.asuLevel,
            signalLevel0to4 = strength.level,
            cellId = cid?.toLong(),
            tac = lac,
            mcc = mcc,
            mnc = mnc,
            operatorName = operatorName,
            subscriptionId = subId,
            simSlotIndex = slot,
        )
    }

    /** CDMA has no PLMN (MCC/MNC), PCI, EARFCN, or TAC concept, so those fields are left null
     * rather than approximated from an unrelated identity. */
    private fun parseCdma(cell: CellInfoCdma, subId: Int?, slot: Int?, operatorName: String?): CellSignalInfo {
        val identity = cell.cellIdentity
        val strength = cell.cellSignalStrength
        val basestationId = identity.basestationId.takeIfValid()

        return CellSignalInfo(
            networkType = RanType.CDMA,
            connectionState = if (cell.isRegistered) "REGISTERED" else "NEIGHBOR",
            isRegistered = cell.isRegistered,
            rssiDbm = strength.dbm.takeIfValidDbm(),
            asuLevel = strength.asuLevel,
            signalLevel0to4 = strength.level,
            cellId = basestationId?.toLong(),
            operatorName = operatorName,
            subscriptionId = subId,
            simSlotIndex = slot,
        )
    }

    private fun parseGsm(cell: CellInfoGsm, subId: Int?, slot: Int?, operatorName: String?): CellSignalInfo {
        val identity = cell.cellIdentity
        val strength = cell.cellSignalStrength
        val mcc = runCatching { identity.mccString }.getOrNull()
        val mnc = runCatching { identity.mncString }.getOrNull()
        val cid = identity.cid.takeIfValid()
        val lac = identity.lac.takeIfValid()

        return CellSignalInfo(
            networkType = RanType.GSM,
            connectionState = if (cell.isRegistered) "REGISTERED" else "NEIGHBOR",
            isRegistered = cell.isRegistered,
            rssiDbm = strength.dbm.takeIfValidDbm(),
            asuLevel = strength.asuLevel,
            signalLevel0to4 = strength.level,
            cellId = cid?.toLong(),
            tac = lac,
            mcc = mcc,
            mnc = mnc,
            operatorName = operatorName,
            subscriptionId = subId,
            simSlotIndex = slot,
        )
    }

    /** Android telephony APIs use Int.MAX_VALUE (or similarly large sentinels) for "unavailable". */
    private fun Int.takeIfValid(): Int? = if (this == Int.MAX_VALUE || this < 0) null else this
    private fun Long.takeIfValid(): Long? = if (this == Long.MAX_VALUE || this < 0) null else this
    private fun Int.takeIfValidDbm(): Int? = if (this == Int.MAX_VALUE || this == Int.MIN_VALUE) null else this
    private fun Int.takeIfValidSnr(): Int? = if (this == Int.MAX_VALUE) null else this
}
