package com.ltemapper.app.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable snapshot of the in-progress mapping session, written on every accepted measurement.
 * Individual [com.ltemapper.app.domain.model.Measurement] rows are already committed to Room as
 * they're recorded, so the only thing at risk if the process dies mid-session is the summary
 * session row never getting its `endTime`/final distance. This lets [MappingForegroundService]
 * resume (or, failing that, cleanly close) an interrupted session instead of leaving it open
 * forever.
 */
@Singleton
class MappingSessionPersistence @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class State(
        val sessionId: Long,
        val distanceMeters: Double,
        val measurementCount: Int,
        val lastLatitude: Double?,
        val lastLongitude: Double?,
        val lastTimestamp: Long,
    )

    fun save(state: State) {
        prefs.edit()
            .putLong(KEY_SESSION_ID, state.sessionId)
            .putLong(KEY_DISTANCE_BITS, java.lang.Double.doubleToRawLongBits(state.distanceMeters))
            .putInt(KEY_MEASUREMENT_COUNT, state.measurementCount)
            .putLong(KEY_LAST_LAT_BITS, state.lastLatitude?.let { java.lang.Double.doubleToRawLongBits(it) } ?: NULL_BITS)
            .putLong(KEY_LAST_LON_BITS, state.lastLongitude?.let { java.lang.Double.doubleToRawLongBits(it) } ?: NULL_BITS)
            .putLong(KEY_LAST_TIMESTAMP, state.lastTimestamp)
            .apply()
    }

    fun load(): State? {
        val sessionId = prefs.getLong(KEY_SESSION_ID, NO_SESSION)
        if (sessionId == NO_SESSION) return null
        val latBits = prefs.getLong(KEY_LAST_LAT_BITS, NULL_BITS)
        val lonBits = prefs.getLong(KEY_LAST_LON_BITS, NULL_BITS)
        return State(
            sessionId = sessionId,
            distanceMeters = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_DISTANCE_BITS, 0L)),
            measurementCount = prefs.getInt(KEY_MEASUREMENT_COUNT, 0),
            lastLatitude = if (latBits == NULL_BITS) null else java.lang.Double.longBitsToDouble(latBits),
            lastLongitude = if (lonBits == NULL_BITS) null else java.lang.Double.longBitsToDouble(lonBits),
            lastTimestamp = prefs.getLong(KEY_LAST_TIMESTAMP, 0L),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS_NAME = "mapping_session_state"
        private const val KEY_SESSION_ID = "session_id"
        private const val KEY_DISTANCE_BITS = "distance_bits"
        private const val KEY_MEASUREMENT_COUNT = "measurement_count"
        private const val KEY_LAST_LAT_BITS = "last_lat_bits"
        private const val KEY_LAST_LON_BITS = "last_lon_bits"
        private const val KEY_LAST_TIMESTAMP = "last_timestamp"
        private const val NO_SESSION = -1L
        private val NULL_BITS = java.lang.Double.doubleToRawLongBits(Double.NaN)
    }
}
