package com.ltemapper.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ltemapper.app.domain.model.BoundingBox
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineDownloadScheduler @Inject constructor(@ApplicationContext private val context: Context) {

    private val workManager get() = WorkManager.getInstance(context)

    fun enqueue(
        name: String,
        box: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        source: MapTileSource,
        custom: CustomTileSourceConfig,
        wifiOnly: Boolean,
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresStorageNotLow(true)
            .build()

        val request = OneTimeWorkRequestBuilder<OfflineTileDownloadWorker>()
            .setInputData(OfflineTileDownloadWorker.buildInputData(name, box, minZoom, maxZoom, source, custom))
            .setConstraints(constraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .build()

        // KEEP: if a download is already running, don't silently replace it — the UI reconnects
        // to the in-flight one instead.
        workManager.enqueueUniqueWork(
            OfflineTileDownloadWorker.UNIQUE_WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancel() {
        workManager.cancelUniqueWork(OfflineTileDownloadWorker.UNIQUE_WORK_NAME)
    }

    /** Emits the current state of the download, if any — including after the app was closed and
     * reopened, so the screen always reflects work that is still running. */
    fun observeWork(): Flow<WorkInfo?> =
        workManager.getWorkInfosForUniqueWorkFlow(OfflineTileDownloadWorker.UNIQUE_WORK_NAME)
            .map { infos -> infos.firstOrNull { !it.state.isFinished } ?: infos.lastOrNull() }

    companion object {
        const val TAG = "offline_download"
    }
}
