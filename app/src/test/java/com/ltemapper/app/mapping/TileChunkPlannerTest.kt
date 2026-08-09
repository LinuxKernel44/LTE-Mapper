package com.ltemapper.app.mapping

import com.ltemapper.app.domain.model.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileChunkPlannerTest {

    private val france = BoundingBox(minLat = 41.0, maxLat = 51.5, minLon = -5.5, maxLon = 9.8)
    private val paris = BoundingBox(minLat = 48.8, maxLat = 48.9, minLon = 2.3, maxLon = 2.4)

    @Test
    fun `a small area needs a single chunk`() {
        val plan = TileChunkPlanner.plan(paris, minZoom = 10, maxZoom = 12, maxTilesPerChunk = 15_000)
        assertEquals(1, plan.chunkCount)
        assertEquals(paris, plan.chunks.single())
    }

    @Test
    fun `a large area is split into several chunks`() {
        val plan = TileChunkPlanner.plan(france, minZoom = 8, maxZoom = 14, maxTilesPerChunk = 15_000)
        assertTrue("expected France z8-14 to need multiple chunks, got ${plan.chunkCount}", plan.chunkCount > 1)
    }

    @Test
    fun `every produced chunk fits within the per-chunk budget`() {
        val budget = 5_000
        val plan = TileChunkPlanner.plan(france, minZoom = 8, maxZoom = 13, maxTilesPerChunk = budget)
        plan.chunks.forEach { chunk ->
            val tiles = TileMath.tileCount(chunk, 8, 13)
            assertTrue("chunk holds $tiles tiles, over budget $budget", tiles <= budget)
        }
    }

    @Test
    fun `chunks fully cover the original area without gaps`() {
        val plan = TileChunkPlanner.plan(france, minZoom = 8, maxZoom = 12, maxTilesPerChunk = 5_000)
        // Splitting is always an exact bisection, so the union's envelope must equal the original.
        assertEquals(france.minLat, plan.chunks.minOf { it.minLat }, 1e-9)
        assertEquals(france.maxLat, plan.chunks.maxOf { it.maxLat }, 1e-9)
        assertEquals(france.minLon, plan.chunks.minOf { it.minLon }, 1e-9)
        assertEquals(france.maxLon, plan.chunks.maxOf { it.maxLon }, 1e-9)
    }

    @Test
    fun `chunks do not overlap in area`() {
        val plan = TileChunkPlanner.plan(france, minZoom = 8, maxZoom = 11, maxTilesPerChunk = 2_000)
        val totalChunkArea = plan.chunks.sumOf { (it.maxLat - it.minLat) * (it.maxLon - it.minLon) }
        val originalArea = (france.maxLat - france.minLat) * (france.maxLon - france.minLon)
        assertEquals(originalArea, totalChunkArea, 1e-6)
    }

    @Test
    fun `totalTiles matches a direct computation over the whole area`() {
        val plan = TileChunkPlanner.plan(france, minZoom = 8, maxZoom = 12, maxTilesPerChunk = 5_000)
        assertEquals(TileMath.tileCount(france, 8, 12), plan.totalTiles)
    }

    @Test
    fun `planning terminates even for an extreme request`() {
        val world = BoundingBox(minLat = -85.0, maxLat = 85.0, minLon = -180.0, maxLon = 180.0)
        val plan = TileChunkPlanner.plan(world, minZoom = 0, maxZoom = 15, maxTilesPerChunk = 1_000)
        assertTrue(plan.chunks.isNotEmpty())
        // Capped by MAX_DEPTH = 8 quad-equivalent splits.
        assertTrue("chunk explosion: ${plan.chunkCount}", plan.chunkCount <= 256)
    }

    @Test
    fun `a degenerate zero-area box still yields one chunk`() {
        val point = BoundingBox(minLat = 48.85, maxLat = 48.85, minLon = 2.35, maxLon = 2.35)
        val plan = TileChunkPlanner.plan(point, minZoom = 10, maxZoom = 12, maxTilesPerChunk = 1)
        assertTrue(plan.chunks.isNotEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a non-positive budget is rejected`() {
        TileChunkPlanner.plan(paris, minZoom = 10, maxZoom = 12, maxTilesPerChunk = 0)
    }

    @Test
    fun `splitting prefers the longer side`() {
        // A tall, narrow box should first be cut horizontally, giving two boxes of equal latitude span.
        val tall = BoundingBox(minLat = 20.0, maxLat = 60.0, minLon = 2.0, maxLon = 3.0)
        val plan = TileChunkPlanner.plan(tall, minZoom = 10, maxZoom = 12, maxTilesPerChunk = 3_000)
        assertTrue(plan.chunkCount > 1)
        // All chunks keep the full longitude span until latitude has been divided down.
        assertTrue(plan.chunks.all { it.minLon >= 2.0 && it.maxLon <= 3.0 })
    }
}
