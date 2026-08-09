package com.ltemapper.app.domain.model

sealed interface TileDownloadProgress {
    data object Idle : TileDownloadProgress

    data class Estimating(val name: String) : TileDownloadProgress

    /**
     * The selected tile server forbids bulk pre-downloading (e.g. the public OpenStreetMap
     * server). Nothing was downloaded; the user needs to pick a bulk-friendly source.
     */
    data class SourceForbidsBulkDownload(val sourceLabel: String) : TileDownloadProgress

    data class Downloading(
        val name: String,
        val downloadedTiles: Int,
        val totalTiles: Int,
        val currentChunk: Int,
        val totalChunks: Int,
    ) : TileDownloadProgress {
        val fraction: Float get() = if (totalTiles > 0) downloadedTiles / totalTiles.toFloat() else 0f
    }

    data class Completed(
        val region: OfflineRegion,
        val failedTiles: Int = 0,
    ) : TileDownloadProgress

    data class Failed(val message: String) : TileDownloadProgress
}
