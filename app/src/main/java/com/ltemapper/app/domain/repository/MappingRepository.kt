package com.ltemapper.app.domain.repository

import androidx.paging.PagingData
import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.domain.model.SessionAggregateRaw
import kotlinx.coroutines.flow.Flow

/** Maximum number of points ever pulled into memory at once for map rendering. */
const val MAX_MAP_RENDER_POINTS = 5000

interface MappingRepository {
    suspend fun createSession(startTime: Long): Long
    suspend fun endSession(sessionId: Long, endTime: Long, distanceMeters: Double)
    suspend fun updateSessionProgress(sessionId: Long, distanceMeters: Double)
    suspend fun deleteSession(sessionId: Long)
    suspend fun deleteAllData()

    suspend fun insertMeasurement(measurement: Measurement): Long
    suspend fun insertMeasurements(measurements: List<Measurement>)

    fun observeSessions(): Flow<List<MappingSession>>
    suspend fun getSession(sessionId: Long): MappingSession?
    fun observeSession(sessionId: Long): Flow<MappingSession?>
    fun observeMeasurementCount(sessionId: Long): Flow<Int>

    fun pagedMeasurements(sessionId: Long): Flow<PagingData<Measurement>>

    /** Downsampled (at most [MAX_MAP_RENDER_POINTS]) rows for map/heatmap rendering. */
    suspend fun getMeasurementsForDisplay(sessionId: Long, networkFilter: NetworkFilter): List<Measurement>

    suspend fun getLatestMeasurements(sessionId: Long, limit: Int): List<Measurement>

    /** One page of a session's measurements ordered by time, for streaming export. */
    suspend fun getMeasurementsPage(sessionId: Long, limit: Int, offset: Int): List<Measurement>

    suspend fun getSessionAggregate(sessionId: Long): SessionAggregateRaw

    suspend fun importMeasurements(sessionId: Long, measurements: List<Measurement>)

    /** Corrects a session's start/end time and distance after a streamed bulk import; see
     * [com.ltemapper.app.export.CsvImporter]. */
    suspend fun finalizeImportedSession(sessionId: Long, startTime: Long, endTime: Long, distanceMeters: Double)
}
