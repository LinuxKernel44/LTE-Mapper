package com.ltemapper.app.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ltemapper.app.R
import com.ltemapper.app.data.repository.OfflineRegionRepository
import com.ltemapper.app.domain.model.BoundingBox
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.TileDownloadProgress
import com.ltemapper.app.mapping.OfflineMapManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.collect

/**
 * Runs an offline tile download as foreground work, so it keeps going once the user leaves the
 * Offline Maps screen, switches apps, or locks the phone — a country-sized area can take many
 * minutes, which no ViewModel-scoped coroutine would survive.
 *
 * The download itself (policy pre-check, chunking, sequencing) lives in [OfflineMapManager]; this
 * class only adapts it to WorkManager: notification, progress publishing, and persistence of the
 * finished region.
 */
@HiltWorker
class OfflineTileDownloadWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val offlineMapManager: OfflineMapManager,
    private val offlineRegionRepository: OfflineRegionRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val name = inputData.getString(KEY_NAME) ?: return failure("Missing area name")
        val box = BoundingBox(
            minLat = inputData.getDouble(KEY_MIN_LAT, Double.NaN),
            maxLat = inputData.getDouble(KEY_MAX_LAT, Double.NaN),
            minLon = inputData.getDouble(KEY_MIN_LON, Double.NaN),
            maxLon = inputData.getDouble(KEY_MAX_LON, Double.NaN),
        )
        if (listOf(box.minLat, box.maxLat, box.minLon, box.maxLon).any { it.isNaN() }) {
            return failure("Invalid area")
        }
        val minZoom = inputData.getInt(KEY_MIN_ZOOM, 8)
        val maxZoom = inputData.getInt(KEY_MAX_ZOOM, 14)
        val source = inputData.getString(KEY_SOURCE)
            ?.let { runCatching { MapTileSource.valueOf(it) }.getOrNull() }
            ?: MapTileSource.MAPNIK
        val customConfig = CustomTileSourceConfig(
            urlTemplate = inputData.getString(KEY_CUSTOM_URL).orEmpty(),
            apiKey = inputData.getString(KEY_CUSTOM_KEY).orEmpty(),
        )

        setForeground(buildForegroundInfo(name, 0))

        var terminal: TileDownloadProgress? = null
        try {
            collectDownload(name, box, minZoom, maxZoom, source, customConfig) { terminal = it }
        } finally {
            // WorkManager stops us by cancelling this coroutine; make sure the osmdroid task
            // running underneath is torn down too instead of continuing headless.
            offlineMapManager.cancelActiveDownload()
        }

        return when (val result = terminal) {
            is TileDownloadProgress.Completed -> {
                offlineRegionRepository.save(result.region)
                Result.success(workDataOf(KEY_FAILED_TILES to result.failedTiles))
            }
            is TileDownloadProgress.SourceForbidsBulkDownload ->
                failure("\"${result.sourceLabel}\" doesn't allow downloading areas in advance. Pick a download-friendly map source.")
            is TileDownloadProgress.Failed -> retryOrFail(result.message, retryable = result.retryable)
            // The flow closed without ever reaching a terminal state (e.g. the process was torn
            // down mid-download) — ambiguous, but likely transient, so it's worth a bounded retry.
            else -> retryOrFail("Download did not complete", retryable = true)
        }
    }

    private fun retryOrFail(message: String, retryable: Boolean): Result =
        if (DownloadRetryPolicy.shouldRetry(retryable, runAttemptCount)) Result.retry() else failure(message)

    private suspend fun collectDownload(
        name: String,
        box: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        source: MapTileSource,
        customConfig: CustomTileSourceConfig,
        onTerminal: (TileDownloadProgress) -> Unit,
    ) {
        offlineMapManager.downloadArea(name, box, minZoom, maxZoom, source, customConfig).collect { progress ->
            when (progress) {
                is TileDownloadProgress.Downloading -> {
                    val percent = (progress.fraction * 100).toInt().coerceIn(0, 100)
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS_NAME to name,
                            KEY_PROGRESS_DOWNLOADED to progress.downloadedTiles,
                            KEY_PROGRESS_TOTAL to progress.totalTiles,
                            KEY_PROGRESS_CHUNK to progress.currentChunk,
                            KEY_PROGRESS_TOTAL_CHUNKS to progress.totalChunks,
                        ),
                    )
                    // Refresh the notification, but only on whole-percent changes so a large
                    // download doesn't post thousands of updates.
                    if (percent != lastNotifiedPercent) {
                        lastNotifiedPercent = percent
                        runCatching { setForeground(buildForegroundInfo(name, percent)) }
                    }
                }
                is TileDownloadProgress.Estimating -> Unit
                else -> onTerminal(progress)
            }
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        buildForegroundInfo(inputData.getString(KEY_NAME).orEmpty(), lastNotifiedPercent.coerceAtLeast(0))

    private var lastNotifiedPercent = -1

    private fun failure(message: String) = Result.failure(workDataOf(KEY_ERROR to message))

    private fun buildForegroundInfo(name: String, percent: Int): ForegroundInfo {
        createChannel()
        val cancelIntent = androidx.work.WorkManager.getInstance(appContext)
            .createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_signal)
            .setContentTitle(appContext.getString(R.string.download_notification_title))
            .setContentText(appContext.getString(R.string.download_notification_text, name.take(40), percent))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, percent, percent <= 0)
            .addAction(0, appContext.getString(R.string.download_notification_cancel), cancelIntent)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                appContext.getString(R.string.notification_channel_download_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = appContext.getString(R.string.notification_channel_download_desc)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "offline_tile_download"
        private const val CHANNEL_ID = "offline_download_channel"
        private const val NOTIFICATION_ID = 2001

        const val KEY_NAME = "name"
        const val KEY_MIN_LAT = "min_lat"
        const val KEY_MAX_LAT = "max_lat"
        const val KEY_MIN_LON = "min_lon"
        const val KEY_MAX_LON = "max_lon"
        const val KEY_MIN_ZOOM = "min_zoom"
        const val KEY_MAX_ZOOM = "max_zoom"
        const val KEY_SOURCE = "source"
        const val KEY_CUSTOM_URL = "custom_url"
        const val KEY_CUSTOM_KEY = "custom_key"

        const val KEY_PROGRESS_NAME = "progress_name"
        const val KEY_PROGRESS_DOWNLOADED = "progress_downloaded"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_PROGRESS_CHUNK = "progress_chunk"
        const val KEY_PROGRESS_TOTAL_CHUNKS = "progress_total_chunks"
        const val KEY_FAILED_TILES = "failed_tiles"
        const val KEY_ERROR = "error"

        fun buildInputData(
            name: String,
            box: BoundingBox,
            minZoom: Int,
            maxZoom: Int,
            source: MapTileSource,
            custom: CustomTileSourceConfig,
        ): Data = workDataOf(
            KEY_NAME to name,
            KEY_MIN_LAT to box.minLat,
            KEY_MAX_LAT to box.maxLat,
            KEY_MIN_LON to box.minLon,
            KEY_MAX_LON to box.maxLon,
            KEY_MIN_ZOOM to minZoom,
            KEY_MAX_ZOOM to maxZoom,
            KEY_SOURCE to source.name,
            KEY_CUSTOM_URL to custom.urlTemplate,
            KEY_CUSTOM_KEY to custom.apiKey,
        )
    }
}
