package com.ltemapper.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ltemapper.app.MainActivity
import com.ltemapper.app.R
import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.repository.MappingRepository
import com.ltemapper.app.domain.usecase.ClassifySignalUseCase
import com.ltemapper.app.data.repository.SettingsRepository
import com.ltemapper.app.location.LocationFilter
import com.ltemapper.app.location.LocationTracker
import com.ltemapper.app.telephony.TelephonyRepository
import com.ltemapper.app.util.formatDistance
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MappingForegroundService : Service() {

    @Inject lateinit var telephonyRepository: TelephonyRepository
    @Inject lateinit var locationTracker: LocationTracker
    @Inject lateinit var mappingRepository: MappingRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var sessionState: MappingSessionState
    @Inject lateinit var classifySignal: ClassifySignalUseCase

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var mappingJob: Job? = null
    private var telephonyJob: Job? = null

    private var currentSessionId: Long? = null
    private var locationFilter: LocationFilter? = null
    @Volatile private var lastCellSignal = com.ltemapper.app.domain.model.CellSignalInfo.unavailable()
    @Volatile private var accumulatedDistance = 0.0
    @Volatile private var lastAcceptedLocation: com.ltemapper.app.domain.model.LocationSample? = null
    @Volatile private var measurementCount = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startMapping()
            ACTION_STOP -> stopMapping()
        }
        return START_STICKY
    }

    private fun startMapping() {
        if (mappingJob != null) return

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0, 0.0))

        mappingJob = serviceScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            locationFilter = LocationFilter(settings.minDistanceMeters, settings.minIntervalMillis)
            accumulatedDistance = 0.0
            measurementCount = 0
            lastAcceptedLocation = null

            val sessionId = mappingRepository.createSession(System.currentTimeMillis())
            currentSessionId = sessionId
            sessionState.update {
                LiveMappingState(isMapping = true, sessionId = sessionId, startTime = System.currentTimeMillis())
            }

            telephonyJob = launch {
                telephonyRepository.observeCellSignal().collect { info ->
                    lastCellSignal = info
                    sessionState.update { it.copy(latestCellSignal = info) }
                }
            }

            locationTracker.observeLocation(settings.locationUpdateIntervalMillis).collect { raw ->
                sessionState.update { it.copy(latestLocation = raw) }
                val filter = locationFilter ?: return@collect
                if (!filter.shouldAccept(raw)) return@collect

                val previous = lastAcceptedLocation
                if (previous != null) {
                    accumulatedDistance += LocationFilter.haversineMeters(
                        previous.latitude, previous.longitude, raw.latitude, raw.longitude,
                    )
                }
                lastAcceptedLocation = raw

                val cellSignal = lastCellSignal
                val quality = classifySignal(cellSignal.rsrpDbm, settings.signalThresholds)
                val measurement = Measurement(
                    sessionId = sessionId,
                    timestamp = raw.timestamp,
                    latitude = raw.latitude,
                    longitude = raw.longitude,
                    accuracyMeters = raw.accuracyMeters,
                    isLowAccuracy = raw.isLowAccuracy,
                    networkType = cellSignal.networkType,
                    rssiDbm = cellSignal.rssiDbm,
                    rsrpDbm = cellSignal.rsrpDbm,
                    rsrqDb = cellSignal.rsrqDb,
                    rssnrDb = cellSignal.rssnrDb,
                    cqi = cellSignal.cqi,
                    pci = cellSignal.pci,
                    earfcn = cellSignal.earfcn,
                    cellId = cellSignal.cellId,
                    tac = cellSignal.tac,
                    mcc = cellSignal.mcc,
                    mnc = cellSignal.mnc,
                    operatorName = cellSignal.operatorName,
                    connectionState = cellSignal.connectionState,
                    signalQuality = quality,
                )
                mappingRepository.insertMeasurement(measurement)
                measurementCount++
                mappingRepository.updateSessionProgress(sessionId, accumulatedDistance)

                sessionState.update {
                    it.copy(
                        measurementCount = measurementCount,
                        distanceMeters = accumulatedDistance,
                        latestQuality = quality,
                    )
                }
                updateNotification(measurementCount, accumulatedDistance)
            }
        }
    }

    private fun stopMapping() {
        val sessionId = currentSessionId
        val finalDistance = accumulatedDistance
        telephonyJob?.cancel()
        mappingJob?.cancel()
        mappingJob = null
        telephonyJob = null
        currentSessionId = null

        serviceScope.launch {
            if (sessionId != null) {
                mappingRepository.endSession(sessionId, System.currentTimeMillis(), finalDistance)
            }
            sessionState.update { LiveMappingState() }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        telephonyJob?.cancel()
        mappingJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_mapping_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_mapping_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(count: Int, distanceMeters: Double): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, MappingForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text_format, count, formatDistance(distanceMeters)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent)
            .addAction(0, getString(R.string.notification_action_stop), stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(count: Int, distanceMeters: Double) {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification(count, distanceMeters))
    }

    companion object {
        const val ACTION_START = "com.ltemapper.app.action.START_MAPPING"
        const val ACTION_STOP = "com.ltemapper.app.action.STOP_MAPPING"
        private const val CHANNEL_ID = "mapping_channel"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, MappingForegroundService::class.java).setAction(ACTION_START)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, MappingForegroundService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
