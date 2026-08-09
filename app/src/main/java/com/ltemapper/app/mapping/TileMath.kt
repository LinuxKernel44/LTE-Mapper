package com.ltemapper.app.mapping

import com.ltemapper.app.domain.model.BoundingBox
import kotlin.math.PI
import kotlin.math.asinh
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.tan

/**
 * Standard slippy-map tile arithmetic (the Web Mercator scheme used by OSM and every XYZ tile
 * provider). Kept free of Android/osmdroid types so download planning stays pure and unit
 * testable — osmdroid's own `possibleTilesInArea` needs a live CacheManager.
 */
object TileMath {

    /** Web Mercator is only defined up to ~±85.0511°; latitudes beyond that are clamped. */
    private const val MAX_LATITUDE = 85.05112877980659

    fun tileX(longitude: Double, zoom: Int): Int {
        val n = 2.0.pow(zoom)
        val normalized = ((longitude + 180.0) % 360.0 + 360.0) % 360.0 / 360.0
        return floor(normalized * n).toInt().coerceIn(0, (n - 1).toInt())
    }

    fun tileY(latitude: Double, zoom: Int): Int {
        val n = 2.0.pow(zoom)
        val clamped = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val latRad = clamped * PI / 180.0
        // asinh(tan(lat)) is the numerically stable form of ln(tan(lat) + sec(lat)).
        val y = (1.0 - asinh(tan(latRad)) / PI) / 2.0
        return floor(y * n).toInt().coerceIn(0, (n - 1).toInt())
    }

    /** Number of tiles covering [box] at a single [zoom] level. */
    fun tileCountAtZoom(box: BoundingBox, zoom: Int): Long {
        val xMin = tileX(box.minLon, zoom)
        val xMax = tileX(box.maxLon, zoom)
        val yMin = tileY(box.maxLat, zoom) // north edge maps to the smaller Y
        val yMax = tileY(box.minLat, zoom)
        val width = if (box.minLon <= box.maxLon) {
            (xMax - xMin).toLong() + 1
        } else {
            // Antimeridian crossing (e.g. Fiji, the Aleutians) — Nominatim legitimately returns
            // west > east for these, meaning the box wraps eastward from minLon, through
            // 180°/-180°, to maxLon. xMin > xMax in that case, so the naive (xMax - xMin) would
            // be negative; wrap around the full tile row instead.
            val tilesPerAxis = 1L shl zoom
            (tilesPerAxis - xMin) + (xMax + 1)
        }
        val height = (yMax - yMin).toLong() + 1
        return width * height
    }

    /** Total tiles covering [box] across the inclusive zoom range. */
    fun tileCount(box: BoundingBox, minZoom: Int, maxZoom: Int): Long {
        if (minZoom > maxZoom) return 0
        var total = 0L
        for (zoom in minZoom..maxZoom) {
            total += tileCountAtZoom(box, zoom)
        }
        return total
    }
}
