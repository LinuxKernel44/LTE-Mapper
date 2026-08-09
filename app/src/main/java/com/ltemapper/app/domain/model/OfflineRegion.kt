package com.ltemapper.app.domain.model

/** A user-initiated offline tile download, tracked so it can be listed and deleted later. */
data class OfflineRegion(
    val id: Long = 0,
    val name: String,
    val boundingBox: BoundingBox,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Int,
    val sizeBytes: Long,
    val downloadedAt: Long,
)
