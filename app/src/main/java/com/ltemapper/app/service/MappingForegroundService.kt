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
import android.util.Log
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
import kotlinx.coroutines.CoroutineExceptionHandler
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
    @Inject lateinit var sessionPersistence: MappingSessionPersistence

    /** Without this, an uncaught exception from the collection pipeline (e.g. a transient
     * SQLiteException on insert) would crash the whole process and abandon the session mid-write.
     * With it, we get a chance to close the session out cleanly instead. */
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e(TAG, "Mapping session terminated unexpectedly", throwable)
        handleUnexpectedFailure()
    }
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + exceptionHandler)
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
            null -> resumeAfterProcessRestart()
        }
        return START_STICKY
    }

    /** START_STICKY can make the OS relaunch this service with a null Intent after killing its
     * process mid-session (low memory, etc.). Without this, the interrupted session would never
     * see [MappingRepository.endSession] and would stay open (endTime = null) forever. Resume in
     * place from the durable snapshot instead. */
    private fun resumeAfterProcessRestart() {
        if (mappingJob != null) return
        val saved = sessionPersistence.load() ?: return
        startMapping(resume = saved)
    }

    private fun startMapping(resume: MappingSessionPersistence.State? = null) {
        if (mappingJob != null) return

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(resume?.measurementCount ?: 0, resume?.distanceMeters ?: 0.0))

        mappingJob = serviceScope.launch {
            val settings = settingsRepository.settingsFlow.first()
            locationFilter = LocationFilter(settings.minDistanceMeters, settings.minIntervalMillis)
            accumulatedDistance = resume?.distanceMeters ?: 0.0
            measurementCount = resume?.measurementCount ?: 0
            lastAcceptedLocation = resume?.takeIf { it.lastLatitude != null && it.lastLongitude != null }?.let {
                com.ltemapper.app.domain.model.LocationSample(
                    latitude = it.lastLatitude!!,
                    longitude = it.lastLongitude!!,
                    accuracyMeters = null,
                    timestamp = it.lastTimestamp,
                )
            }

            val sessionId = resume?.sessionId ?: mappingRepository.createSession(System.currentTimeMillis())
            currentSessionId = sessionId
            persistState(sessionId)
            sessionState.update {
                LiveMappingState(isMapping = true, sessionId = sessionId, startTime = System.currentTimeMillis())
            }

            telephonyJob = launch {
                telephonyRepository.observeCellSignal(settings.monitoredSubscriptionId).collect { info ->
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
                persistState(sessionId)

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

    private fun persistState(sessionId: Long) {
        sessionPersistence.save(
            MappingSessionPersistence.State(
                sessionId = sessionId,
                distanceMeters = accumulatedDistance,
                measurementCount = measurementCount,
                lastLatitude = lastAcceptedLocation?.latitude,
                lastLongitude = lastAcceptedLocation?.longitude,
                lastTimestamp = lastAcceptedLocation?.timestamp ?: System.currentTimeMillis(),
            ),
        )
    }

    private fun stopMapping() {
        val sessionId = currentSessionId
        val finalDistance = accumulatedDistance
        telephonyJob?.cancel()
        mappingJob?.cancel()
        mappingJob = null
        telephonyJob = null
        currentSessionId = null

        serviceScope.launch { finishSession(sessionId, finalDistance) }
    }

    private fun handleUnexpectedFailure() {
        val sessionId = currentSessionId
        val finalDistance = accumulatedDistance
        mappingJob = null
        telephonyJob = null
        currentSessionId = null
        serviceScope.launch { finishSession(sessionId, finalDistance) }
    }

    /** Ends the session (if any) and only then tears down the foreground service, so
     * [onDestroy]'s `serviceScope.cancel()` can never race the DB write that closes it out — the
     * previous version called `stopSelf()` right after launching this write, which could cancel
     * it before it completed and leave the session's `endTime` unset. */
    private suspend fun finishSession(sessionId: Long?, finalDistance: Double) {
        if (sessionId != null) {
            runCatching { mappingRepository.endSession(sessionId, System.currentTimeMillis(), finalDistance) }
                .onFailure { Log.e(TAG, "Failed to close session $sessionId", it) }
        }
        sessionPersistence.clear()
        sessionState.update { LiveMappingState() }
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
        private const val TAG = "MappingService"
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
