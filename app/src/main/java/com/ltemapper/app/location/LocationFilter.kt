package com.ltemapper.app.location

import com.ltemapper.app.domain.model.LocationSample
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Decides whether a raw GPS fix should become a recorded measurement point, based on the user's
 * configured minimum distance and minimum time interval. Stateful across a single mapping
 * session; construct a new instance per session.
 */
class LocationFilter(
    private val minDistanceMeters: Float,
    private val minIntervalMillis: Long,
) {
    private var lastAccepted: LocationSample? = null

    fun shouldAccept(sample: LocationSample): Boolean {
        val previous = lastAccepted
        if (previous == null) {
            lastAccepted = sample
            return true
        }
        val elapsed = sample.timestamp - previous.timestamp
        if (elapsed < minIntervalMillis) return false

        val distance = haversineMeters(previous.latitude, previous.longitude, sample.latitude, sample.longitude)
        if (distance < minDistanceMeters) return false

        lastAccepted = sample
        return true
    }

    companion object {
        private const val EARTH_RADIUS_METERS = 6_371_000.0

        fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return EARTH_RADIUS_METERS * c
        }
    }
}
