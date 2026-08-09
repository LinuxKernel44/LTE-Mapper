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
    private val maxAcceptableAccuracyMeters: Float = MAX_ACCEPTABLE_ACCURACY_METERS,
) {
    private var lastAccepted: LocationSample? = null

    fun shouldAccept(sample: LocationSample): Boolean {
        // A fix this imprecise (typically multipath indoors/urban canyon) is unusable: accepting
        // it would silently corrupt the accumulated distance and pollute the heatmap. Fixes with
        // moderate accuracy are still accepted — they're flagged via LocationSample.isLowAccuracy
        // rather than dropped, so the caller can decide how to treat them.
        val accuracy = sample.accuracyMeters
        if (accuracy != null && accuracy > maxAcceptableAccuracyMeters) return false

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
        /** Above this horizontal accuracy, a fix is rejected outright rather than recorded. */
        const val MAX_ACCEPTABLE_ACCURACY_METERS = 100f
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
