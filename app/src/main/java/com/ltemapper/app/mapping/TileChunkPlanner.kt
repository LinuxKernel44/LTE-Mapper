package com.ltemapper.app.mapping

import com.ltemapper.app.domain.model.BoundingBox

/**
 * Splits a large area into a queue of smaller bounding boxes, each below a tile budget, so a
 * whole country can be downloaded as a sequence of manageable jobs instead of one oversized
 * request. Downloading them back-to-back also keeps memory flat and makes the operation
 * cancellable and resumable at chunk granularity.
 *
 * Pure and deterministic (no Android/osmdroid types) so the splitting logic is unit tested.
 */
object TileChunkPlanner {

    /** Safety valve: stop subdividing past this depth even if a chunk is still over budget.
     * 8 levels of quad-splitting is 65 536 chunks — far beyond any realistic request, and it
     * guarantees termination for degenerate inputs (e.g. a huge zoom range on a tiny box). */
    private const val MAX_DEPTH = 8

    data class Plan(
        val chunks: List<BoundingBox>,
        val totalTiles: Long,
    ) {
        val chunkCount: Int get() = chunks.size
    }

    /**
     * @param maxTilesPerChunk per-chunk budget; each returned box is at or under this unless
     *   [MAX_DEPTH] was reached (a single tile can never be split further).
     */
    fun plan(box: BoundingBox, minZoom: Int, maxZoom: Int, maxTilesPerChunk: Int): Plan {
        require(maxTilesPerChunk > 0) { "maxTilesPerChunk must be positive" }
        val total = TileMath.tileCount(box, minZoom, maxZoom)
        val chunks = mutableListOf<BoundingBox>()
        subdivide(box, minZoom, maxZoom, maxTilesPerChunk.toLong(), depth = 0, out = chunks)
        return Plan(chunks = chunks, totalTiles = total)
    }

    private fun subdivide(
        box: BoundingBox,
        minZoom: Int,
        maxZoom: Int,
        budget: Long,
        depth: Int,
        out: MutableList<BoundingBox>,
    ) {
        val count = TileMath.tileCount(box, minZoom, maxZoom)
        if (count <= budget || depth >= MAX_DEPTH) {
            out.add(box)
            return
        }

        // Split along the longer side so chunks stay roughly square in tile terms, which keeps
        // each sub-request compact rather than a long thin strip spanning the whole area.
        val latSpan = box.maxLat - box.minLat
        val lonSpan = box.maxLon - box.minLon
        val halves = if (latSpan >= lonSpan) {
            val midLat = (box.minLat + box.maxLat) / 2.0
            listOf(
                box.copy(maxLat = midLat),
                box.copy(minLat = midLat),
            )
        } else {
            val midLon = (box.minLon + box.maxLon) / 2.0
            listOf(
                box.copy(maxLon = midLon),
                box.copy(minLon = midLon),
            )
        }

        // A box that can no longer be meaningfully halved (degenerate span) must not recurse
        // forever — emit it as-is.
        if (halves.any { it.minLat >= it.maxLat && it.minLon >= it.maxLon }) {
            out.add(box)
            return
        }

        halves.forEach { subdivide(it, minZoom, maxZoom, budget, depth + 1, out) }
    }
}
