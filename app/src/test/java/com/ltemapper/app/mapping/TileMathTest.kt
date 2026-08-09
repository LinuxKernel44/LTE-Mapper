package com.ltemapper.app.mapping

import com.ltemapper.app.domain.model.BoundingBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TileMathTest {

    @Test
    fun `zoom 0 is a single tile for the whole world`() {
        val world = BoundingBox(minLat = -85.0, maxLat = 85.0, minLon = -180.0, maxLon = 179.9)
        assertEquals(1L, TileMath.tileCountAtZoom(world, 0))
    }

    @Test
    fun `zoom 1 splits the world into four tiles`() {
        val world = BoundingBox(minLat = -85.0, maxLat = 85.0, minLon = -180.0, maxLon = 179.9)
        assertEquals(4L, TileMath.tileCountAtZoom(world, 1))
    }

    @Test
    fun `known reference tile - Greenwich at zoom 1`() {
        // Longitude 0 sits at the boundary: it belongs to the eastern tile (x=1) at zoom 1.
        assertEquals(1, TileMath.tileX(0.0, 1))
        // Latitude 0 (equator) likewise falls into the southern tile (y=1).
        assertEquals(1, TileMath.tileY(0.0, 1))
    }

    @Test
    fun `known reference tile - Paris at zoom 12`() {
        // Paris ≈ 48.8566 N, 2.3522 E → tile 2074/1409 at z12 (standard slippy-map reference).
        assertEquals(2074, TileMath.tileX(2.3522, 12))
        assertEquals(1409, TileMath.tileY(48.8566, 12))
    }

    @Test
    fun `tile indices stay within bounds at the poles`() {
        val maxIndex = (1 shl 5) - 1
        assertTrue(TileMath.tileY(89.9, 5) in 0..maxIndex)
        assertTrue(TileMath.tileY(-89.9, 5) in 0..maxIndex)
    }

    @Test
    fun `tileCount sums every zoom in the range`() {
        val box = BoundingBox(minLat = 48.0, maxLat = 49.0, minLon = 2.0, maxLon = 3.0)
        val z10 = TileMath.tileCountAtZoom(box, 10)
        val z11 = TileMath.tileCountAtZoom(box, 11)
        val z12 = TileMath.tileCountAtZoom(box, 12)
        assertEquals(z10 + z11 + z12, TileMath.tileCount(box, 10, 12))
    }

    @Test
    fun `tile count grows roughly fourfold per zoom level`() {
        val box = BoundingBox(minLat = 45.0, maxLat = 50.0, minLon = 0.0, maxLon = 5.0)
        val lower = TileMath.tileCountAtZoom(box, 10)
        val higher = TileMath.tileCountAtZoom(box, 11)
        assertTrue("expected ~4x growth, got $lower -> $higher", higher >= lower * 3)
    }

    @Test
    fun `inverted zoom range yields zero`() {
        val box = BoundingBox(minLat = 48.0, maxLat = 49.0, minLon = 2.0, maxLon = 3.0)
        assertEquals(0L, TileMath.tileCount(box, 12, 10))
    }

    @Test
    fun `a tiny area is always at least one tile per zoom`() {
        val point = BoundingBox(minLat = 48.8566, maxLat = 48.8566, minLon = 2.3522, maxLon = 2.3522)
        assertEquals(1L, TileMath.tileCountAtZoom(point, 15))
        assertEquals(3L, TileMath.tileCount(point, 13, 15))
    }

    @Test
    fun `antimeridian-crossing box wraps instead of going negative`() {
        // Fiji-like box: west (minLon) is east of east (maxLon), wrapping across 180/-180.
        val fiji = BoundingBox(minLat = -20.0, maxLat = -15.0, minLon = 177.0, maxLon = -178.0)
        val count = TileMath.tileCountAtZoom(fiji, 6)
        assertTrue("wrapped tile count must be positive, was $count", count > 0)
    }

    @Test
    fun `antimeridian-crossing box matches the equivalent non-crossing box by symmetry`() {
        // A box spanning [170, 180] and its mirror [-180, -170] should together tile the same as
        // one box that wraps across the seam from 170 to -170.
        val zoom = 8
        val east = BoundingBox(minLat = 0.0, maxLat = 1.0, minLon = 170.0, maxLon = 179.9999)
        val west = BoundingBox(minLat = 0.0, maxLat = 1.0, minLon = -179.9999, maxLon = -170.0)
        val wrapped = BoundingBox(minLat = 0.0, maxLat = 1.0, minLon = 170.0, maxLon = -170.0)

        val expected = TileMath.tileCountAtZoom(east, zoom) + TileMath.tileCountAtZoom(west, zoom)
        assertEquals(expected, TileMath.tileCountAtZoom(wrapped, zoom))
    }
}
