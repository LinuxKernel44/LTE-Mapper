package com.ltemapper.app.location

import com.ltemapper.app.domain.model.LocationSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationFilterTest {

    @Test
    fun `first sample is always accepted`() {
        val filter = LocationFilter(minDistanceMeters = 8f, minIntervalMillis = 3000L)
        assertTrue(filter.shouldAccept(sample(0.0, 0.0, 0)))
    }

    @Test
    fun `sample too soon after the last accepted one is rejected`() {
        val filter = LocationFilter(minDistanceMeters = 1f, minIntervalMillis = 3000L)
        assertTrue(filter.shouldAccept(sample(0.0, 0.0, 0)))
        assertFalse(filter.shouldAccept(sample(0.001, 0.001, 1000)))
    }

    @Test
    fun `sample too close after enough time is rejected`() {
        val filter = LocationFilter(minDistanceMeters = 100f, minIntervalMillis = 1000L)
        assertTrue(filter.shouldAccept(sample(0.0, 0.0, 0)))
        assertFalse(filter.shouldAccept(sample(0.00001, 0.00001, 5000)))
    }

    @Test
    fun `sample far enough away after enough time is accepted`() {
        val filter = LocationFilter(minDistanceMeters = 8f, minIntervalMillis = 1000L)
        assertTrue(filter.shouldAccept(sample(0.0, 0.0, 0)))
        // ~0.0002 degrees latitude is roughly 22 meters.
        assertTrue(filter.shouldAccept(sample(0.0002, 0.0, 5000)))
    }

    @Test
    fun `haversine distance between identical points is zero`() {
        assertEquals(0.0, LocationFilter.haversineMeters(45.0, 9.0, 45.0, 9.0), 0.001)
    }

    @Test
    fun `haversine distance for one degree of latitude is about 111km`() {
        val distance = LocationFilter.haversineMeters(0.0, 0.0, 1.0, 0.0)
        assertTrue(distance in 110_000.0..112_000.0)
    }

    @Test
    fun `fix with accuracy worse than the ceiling is rejected outright`() {
        val filter = LocationFilter(minDistanceMeters = 8f, minIntervalMillis = 1000L)
        assertTrue(filter.shouldAccept(sample(0.0, 0.0, 0)))
        assertFalse(filter.shouldAccept(sample(0.0002, 0.0, 5000, accuracy = 150f)))
    }

    @Test
    fun `fix with moderate accuracy is still accepted, only flagged`() {
        val filter = LocationFilter(minDistanceMeters = 8f, minIntervalMillis = 1000L)
        assertTrue(filter.shouldAccept(sample(0.0, 0.0, 0)))
        // 45m accuracy is poor but below the hard rejection ceiling, unlike LOW_ACCURACY_THRESHOLD.
        assertTrue(filter.shouldAccept(sample(0.0002, 0.0, 5000, accuracy = 45f)))
    }

    @Test
    fun `fix with unknown accuracy is not rejected by the accuracy check`() {
        val filter = LocationFilter(minDistanceMeters = 8f, minIntervalMillis = 1000L)
        assertTrue(filter.shouldAccept(sample(0.0, 0.0, 0, accuracy = null)))
        assertTrue(filter.shouldAccept(sample(0.0002, 0.0, 5000, accuracy = null)))
    }

    private fun sample(lat: Double, lon: Double, timestamp: Long, accuracy: Float? = 5f) = LocationSample(
        latitude = lat,
        longitude = lon,
        accuracyMeters = accuracy,
        timestamp = timestamp,
    )
}
