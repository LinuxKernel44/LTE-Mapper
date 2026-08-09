package com.ltemapper.app.mapping

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ltemapper.app.domain.model.BoundingBox
import com.ltemapper.app.domain.model.CustomTileSourceConfig
import com.ltemapper.app.domain.model.MapTileSource
import com.ltemapper.app.domain.model.OfflineRegion
import com.ltemapper.app.domain.model.TileDownloadProgress
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.osmdroid.tileprovider.cachemanager.CacheManager
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Pre-downloads map tiles into the very same on-disk cache the map reads while browsing, which is
 * what makes offline coverage automatic: osmdroid always checks the cache first and only hits the
 * network for tiles that aren't there. Areas you never downloaded keep working online exactly as
 * before — that's the "fallback to network" behavior, and it needs no extra code.
 *
 * Two things this class is careful about:
 *
 * 1. **Bulk-download policy.** Tile servers advertise whether they tolerate bulk pre-fetching, and
 *    osmdroid enforces it by throwing [org.osmdroid.tileprovider.tilesource.TileSourcePolicyException]
 *    from `preCheck()` — which runs on the AsyncTask worker thread, where AsyncTask rethrows it as
 *    a fatal RuntimeException that no caller can catch. So we must check the policy *ourselves
 *    before* starting a task; that check is the reason downloads no longer crash the app.
 *
 * 2. **Large areas.** Rather than refusing anything big, an area is split by [TileChunkPlanner]
 *    into a queue of bounded chunks downloaded back to back, so a whole country is just "more
 *    chunks" — with aggregate progress, cancellation, and tolerance for individual tile failures.
 */
@Singleton
class OfflineMapManager @Inject constructor(@ApplicationContext private val context: Context) {

    private val tileWriter by lazy { SqlTileWriter() }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var activeTask: CacheManager.CacheManagerTask? = null
    @Volatile private var cancelled = false

    fun estimateTiles(box: BoundingBox, minZoom: Int, maxZoom: Int): Long =
        TileMath.tileCount(box, minZoom, maxZoom)

    fun planChunks(box: BoundingBox, minZoom: Int, maxZoom: Int): TileChunkPlanner.Plan =
        TileChunkPlanner.plan(box, minZoom, maxZoom, MAX_TILES_PER_CHUNK)

    /** Total size on disk of every cached tile (browsed or pre-downloaded). */
    fun currentCacheUsageBytes(): Long = runCatching { tileWriter.getSize() }.getOrDefault(0L)

    fun clearAllTiles(): Boolean = runCatching { tileWriter.purgeCache() }.getOrDefault(false)

    fun cancelActiveDownload() {
        cancelled = true
        mainHandler.post { runCatching { activeTask?.cancel(true) } }
    }

    fun downloadArea(
        name: String,
        box: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        source: MapTileSource,
        customConfig: CustomTileSourceConfig,
    ): Flow<TileDownloadProgress> = channelFlow {
        cancelled = false
        val tileSource = OsmMapConfig.tileSourceFor(source, customConfig)

        // MUST happen before any CacheManagerTask is created — see the class docs.
        if (!acceptsBulkDownload(tileSource)) {
            send(TileDownloadProgress.SourceForbidsBulkDownload(source.label))
            return@channelFlow
        }

        send(TileDownloadProgress.Estimating(name))

        val plan = runCatching { planChunks(box, minZoom, maxZoom) }.getOrNull()
        if (plan == null || plan.chunks.isEmpty() || plan.totalTiles <= 0) {
            send(TileDownloadProgress.Failed("No tiles to download for this area and zoom range."))
            return@channelFlow
        }

        val cacheManager = runCatching {
            CacheManager(tileSource, tileWriter, source.minZoom, source.maxZoom)
        }.getOrElse {
            send(TileDownloadProgress.Failed("Could not prepare the tile cache: ${it.message ?: "unknown error"}"))
            return@channelFlow
        }

        val sizeBefore = currentCacheUsageBytes()
        val totalTiles = plan.totalTiles.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        var tilesCompletedInPreviousChunks = 0
        var failedTiles = 0

        plan.chunks.forEachIndexed { index, chunk ->
            if (cancelled) return@forEachIndexed

            val chunkTiles = TileMath.tileCount(chunk, minZoom, maxZoom).toInt()
            val result = runCatching {
                downloadChunk(
                    cacheManager = cacheManager,
                    chunk = chunk,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    onTileProgress = { tilesInThisChunk ->
                        trySend(
                            TileDownloadProgress.Downloading(
                                name = name,
                                downloadedTiles = (tilesCompletedInPreviousChunks + tilesInThisChunk).coerceAtMost(totalTiles),
                                totalTiles = totalTiles,
                                currentChunk = index + 1,
                                totalChunks = plan.chunkCount,
                            ),
                        )
                    },
                )
            }.getOrElse { ChunkResult(failedTiles = chunkTiles) }

            failedTiles += result.failedTiles
            tilesCompletedInPreviousChunks += chunkTiles

            send(
                TileDownloadProgress.Downloading(
                    name = name,
                    downloadedTiles = tilesCompletedInPreviousChunks.coerceAtMost(totalTiles),
                    totalTiles = totalTiles,
                    currentChunk = index + 1,
                    totalChunks = plan.chunkCount,
                ),
            )
        }

        activeTask = null

        if (cancelled) {
            send(TileDownloadProgress.Failed("Download cancelled. Tiles already fetched were kept."))
            return@channelFlow
        }

        val addedBytes = (currentCacheUsageBytes() - sizeBefore).coerceAtLeast(0)
        send(
            TileDownloadProgress.Completed(
                region = OfflineRegion(
                    name = name,
                    boundingBox = box,
                    minZoom = minZoom,
                    maxZoom = maxZoom,
                    tileCount = totalTiles,
                    sizeBytes = addedBytes,
                    downloadedAt = System.currentTimeMillis(),
                ),
                failedTiles = failedTiles,
            ),
        )
    }

    private data class ChunkResult(val failedTiles: Int = 0)

    /** Suspends until this chunk's [CacheManager.CacheManagerTask] finishes, so chunks run
     * strictly one after another instead of flooding the tile server in parallel. */
    private suspend fun downloadChunk(
        cacheManager: CacheManager,
        chunk: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        onTileProgress: (Int) -> Unit,
    ): ChunkResult = suspendCancellableCoroutine { continuation ->
        val callback = object : CacheManager.CacheManagerCallback {
            override fun onTaskComplete() {
                continuation.resumeIfActive(ChunkResult())
            }

            override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) {
                onTileProgress(progress)
            }

            override fun downloadStarted() = Unit

            override fun setPossibleTilesInArea(total: Int) = Unit

            /** osmdroid reports the number of individual tiles that failed, not a fatal error —
             * a few misses shouldn't abandon the remaining chunks. */
            override fun onTaskFailed(errors: Int) {
                continuation.resumeIfActive(ChunkResult(failedTiles = errors))
            }
        }

        // CacheManagerTask is an AsyncTask: it must be created and executed on the main thread.
        mainHandler.post {
            try {
                activeTask = cacheManager.downloadAreaAsync(
                    context,
                    chunk.toOsmdroid(),
                    minZoom,
                    maxZoom,
                    callback,
                )
            } catch (throwable: Throwable) {
                continuation.resumeIfActive(ChunkResult(failedTiles = 0))
            }
        }

        continuation.invokeOnCancellation {
            mainHandler.post { runCatching { activeTask?.cancel(true) } }
        }
    }

    fun deleteRegion(region: OfflineRegion, source: MapTileSource, customConfig: CustomTileSourceConfig): Flow<TileDownloadProgress> = flow {
        val tileSource = OsmMapConfig.tileSourceFor(source, customConfig)
        val cacheManager = runCatching {
            CacheManager(tileSource, tileWriter, source.minZoom, source.maxZoom)
        }.getOrElse {
            emit(TileDownloadProgress.Failed("Could not open the tile cache."))
            return@flow
        }

        val cleaned = suspendCancellableCoroutine { continuation ->
            mainHandler.post {
                try {
                    val task = cacheManager.cleanAreaAsync(
                        context,
                        region.boundingBox.toOsmdroid(),
                        region.minZoom,
                        region.maxZoom,
                    )
                    task.addCallback(object : CacheManager.CacheManagerCallback {
                        override fun onTaskComplete() = continuation.resumeIfActive(true)
                        override fun updateProgress(progress: Int, currentZoomLevel: Int, zoomMin: Int, zoomMax: Int) = Unit
                        override fun downloadStarted() = Unit
                        override fun setPossibleTilesInArea(total: Int) = Unit
                        override fun onTaskFailed(errors: Int) = continuation.resumeIfActive(true)
                    })
                } catch (throwable: Throwable) {
                    continuation.resumeIfActive(false)
                }
            }
        }

        if (cleaned) {
            emit(TileDownloadProgress.Completed(region))
        } else {
            emit(TileDownloadProgress.Failed("Could not remove the tiles for this area."))
        }
    }

    private fun acceptsBulkDownload(tileSource: ITileSource): Boolean =
        tileSource is OnlineTileSourceBase && tileSource.tileSourcePolicy.acceptsBulkDownload()

    private fun <T> CancellableContinuation<T>.resumeIfActive(value: T) {
        if (isActive) resume(value)
    }

    private fun BoundingBox.toOsmdroid() = org.osmdroid.util.BoundingBox(maxLat, maxLon, minLat, minLon)

    companion object {
        /** Per-chunk tile budget. Small enough that each osmdroid task stays responsive and
         * cancellable; large areas simply produce more chunks. */
        const val MAX_TILES_PER_CHUNK = 15_000
    }
}
