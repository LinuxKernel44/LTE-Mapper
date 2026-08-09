package com.ltemapper.app.telephony

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SimInfo
import com.ltemapper.app.util.hasFineLocationPermission
import com.ltemapper.app.util.hasPhoneStatePermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TelephonyRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : TelephonyRepository {

    private val telephonyManager: TelephonyManager? = context.getSystemService()
    private val subscriptionManager: SubscriptionManager? = context.getSystemService()

    @SuppressLint("MissingPermission")
    override fun observeCellSignal(): Flow<CellSignalInfo> = callbackFlow {
        if (!context.hasPhoneStatePermission()) {
            trySend(CellSignalInfo.unavailable("READ_PHONE_STATE not granted"))
            close()
            return@callbackFlow
        }
        val tm = telephonyManager
        if (tm == null) {
            trySend(CellSignalInfo.unavailable("No telephony hardware"))
            close()
            return@callbackFlow
        }

        val subId = SubscriptionManager.getDefaultDataSubscriptionId()
        val targetTm = if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            runCatching { tm.createForSubscriptionId(subId) }.getOrDefault(tm)
        } else tm
        val simSlot = activeSimSlotFor(subId)
        val operatorName = runCatching { targetTm.networkOperatorName }.getOrNull()?.takeIf { it.isNotBlank() }

        val emitter = SignalEmitter(
            hasLocationPermission = context.hasFineLocationPermission(),
            subscriptionId = subId.takeIf { it != SubscriptionManager.INVALID_SUBSCRIPTION_ID },
            simSlotIndex = simSlot,
            operatorName = operatorName,
            fallbackNetworkType = { runCatching { rawDataNetworkTypeToRan(targetTm.dataNetworkType) }.getOrDefault(RanType.UNKNOWN) },
            onUpdate = { trySend(it) },
        )

        val mainHandler = Handler(Looper.getMainLooper())
        var telephonyCallback: TelephonyCallback? = null
        var legacyListener: PhoneStateListener? = null

        mainHandler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = ModernTelephonyCallback(emitter)
                telephonyCallback = callback
                runCatching {
                    targetTm.registerTelephonyCallback(context.mainExecutor, callback)
                }
            } else {
                val listener = LegacyPhoneStateListener(emitter)
                legacyListener = listener
                runCatching {
                    targetTm.listen(
                        listener,
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS or PhoneStateListener.LISTEN_CELL_INFO or PhoneStateListener.LISTEN_SERVICE_STATE,
                    )
                }
            }
            // Prime the stream immediately rather than waiting for the first change event.
            if (context.hasFineLocationPermission()) {
                runCatching { emitter.onCellInfoChanged(targetTm.allCellInfo) }
            } else {
                emitter.onCellInfoChanged(null)
            }
        }

        awaitClose {
            mainHandler.post {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    telephonyCallback?.let { runCatching { targetTm.unregisterTelephonyCallback(it) } }
                }
                legacyListener?.let { runCatching { targetTm.listen(it, PhoneStateListener.LISTEN_NONE) } }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun getActiveSims(): List<SimInfo> {
        if (!context.hasPhoneStatePermission()) return emptyList()
        val sm = subscriptionManager ?: return emptyList()
        val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    simSlotIndex = info.simSlotIndex,
                    carrierName = info.carrierName?.toString(),
                    displayName = info.displayName?.toString(),
                    isDefaultData = info.subscriptionId == defaultDataSubId,
                    mcc = runCatching { info.mccString }.getOrNull(),
                    mnc = runCatching { info.mncString }.getOrNull(),
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun activeSimSlotFor(subId: Int): Int? {
        if (!context.hasPhoneStatePermission()) return null
        val sm = subscriptionManager ?: return null
        return runCatching {
            sm.activeSubscriptionInfoList.orEmpty().firstOrNull { it.subscriptionId == subId }?.simSlotIndex
        }.getOrNull()
    }

    private fun rawDataNetworkTypeToRan(networkType: Int): RanType = when (networkType) {
        TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN, TelephonyManager.NETWORK_TYPE_GSM,
        -> RanType.GSM

        TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA, TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_EVDO_B, TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_TD_SCDMA,
        -> RanType.UMTS

        TelephonyManager.NETWORK_TYPE_LTE -> RanType.LTE
        TelephonyManager.NETWORK_TYPE_NR -> RanType.NR
        else -> RanType.UNKNOWN
    }

    /** Merges the last known cell identity with the freshest signal-strength push into one snapshot. */
    private class SignalEmitter(
        private val hasLocationPermission: Boolean,
        private val subscriptionId: Int?,
        private val simSlotIndex: Int?,
        private val operatorName: String?,
        private val fallbackNetworkType: () -> RanType,
        private val onUpdate: (CellSignalInfo) -> Unit,
    ) {
        @Volatile private var lastParsed: CellSignalInfo? = null

        fun onCellInfoChanged(cellInfoList: List<android.telephony.CellInfo>?) {
            if (!hasLocationPermission) {
                emitFallback("Location permission required for cell identity")
                return
            }
            val list = cellInfoList.orEmpty()
            val registered = list.firstOrNull { it.isRegistered } ?: list.firstOrNull()
            val parsed = registered?.let { CellInfoParser.parse(it, subscriptionId, simSlotIndex, operatorName) }
            if (parsed != null) {
                lastParsed = parsed
                onUpdate(parsed)
            } else {
                emitFallback(null)
            }
        }

        fun onSignalStrengthChanged(signalStrength: SignalStrength?) {
            val current = lastParsed
            if (signalStrength == null) return
            if (current != null) {
                // Refresh dBm-ish fields from the higher-frequency SignalStrength push while
                // keeping the last known cell identity (PCI/EARFCN/cellId change far less often).
                val refreshed = refreshFromSignalStrength(current, signalStrength)
                lastParsed = refreshed
                onUpdate(refreshed)
            } else {
                emitFallback(null)
            }
        }

        private fun refreshFromSignalStrength(base: CellSignalInfo, ss: SignalStrength): CellSignalInfo {
            val level = ss.level
            return base.copy(signalLevel0to4 = level, timestamp = System.currentTimeMillis())
        }

        private fun emitFallback(reason: String?) {
            val type = fallbackNetworkType()
            onUpdate(
                CellSignalInfo(
                    networkType = type,
                    connectionState = reason,
                    isRegistered = type != RanType.UNKNOWN,
                    operatorName = operatorName,
                    subscriptionId = subscriptionId,
                    simSlotIndex = simSlotIndex,
                ),
            )
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.S)
    private class ModernTelephonyCallback(private val emitter: SignalEmitter) :
        TelephonyCallback(),
        TelephonyCallback.CellInfoListener,
        TelephonyCallback.SignalStrengthsListener {
        override fun onCellInfoChanged(cellInfo: MutableList<android.telephony.CellInfo>) {
            emitter.onCellInfoChanged(cellInfo)
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
            emitter.onSignalStrengthChanged(signalStrength)
        }
    }

    @Suppress("DEPRECATION")
    private class LegacyPhoneStateListener(private val emitter: SignalEmitter) : PhoneStateListener() {
        override fun onCellInfoChanged(cellInfo: MutableList<android.telephony.CellInfo>?) {
            emitter.onCellInfoChanged(cellInfo)
        }

        override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
            emitter.onSignalStrengthChanged(signalStrength)
        }
    }
}
