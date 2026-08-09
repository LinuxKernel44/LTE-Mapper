package com.ltemapper.app.export

import androidx.paging.PagingData
import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.domain.model.SessionAggregateRaw
import com.ltemapper.app.domain.repository.MappingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf

/** Minimal in-memory [MappingRepository] used to unit test the export/import layer without Room. */
class FakeMappingRepository(measurements: List<Measurement> = emptyList()) : MappingRepository {
    val measurements = measurements.toMutableList()
    val sessions = mutableListOf<MappingSession>()

    override suspend fun createSession(startTime: Long): Long {
        val id = (sessions.maxOfOrNull { it.id } ?: 0) + 1
        sessions.add(MappingSession(id = id, startTime = startTime, endTime = null, distanceMeters = 0.0, measurementCount = 0))
        return id
    }

    override suspend fun endSession(sessionId: Long, endTime: Long, distanceMeters: Double) {}
    override suspend fun updateSessionProgress(sessionId: Long, distanceMeters: Double) {}
    override suspend fun deleteSession(sessionId: Long) {
        measurements.removeAll { it.sessionId == sessionId }
    }

    override suspend fun deleteAllData() {
        measurements.clear()
        sessions.clear()
    }

    override suspend fun insertMeasurement(measurement: Measurement): Long {
        measurements.add(measurement)
        return measurements.size.toLong()
    }

    override suspend fun insertMeasurements(measurements: List<Measurement>) {
        this.measurements.addAll(measurements)
    }

    override fun observeSessions(): StateFlow<List<MappingSession>> = MutableStateFlow(sessions)
    override suspend fun getSession(sessionId: Long): MappingSession? = sessions.firstOrNull { it.id == sessionId }
    override fun observeSession(sessionId: Long) = flowOf(getSessionSync(sessionId))
    private fun getSessionSync(sessionId: Long) = sessions.firstOrNull { it.id == sessionId }
    override fun observeMeasurementCount(sessionId: Long) = flowOf(measurements.count { it.sessionId == sessionId })

    override fun pagedMeasurements(sessionId: Long) = flowOf(PagingData.from(measurements.filter { it.sessionId == sessionId }))

    override suspend fun getMeasurementsForDisplay(sessionId: Long, networkFilter: NetworkFilter): List<Measurement> =
        measurements.filter { it.sessionId == sessionId }

    override suspend fun getLatestMeasurements(sessionId: Long, limit: Int): List<Measurement> =
        measurements.filter { it.sessionId == sessionId }.sortedByDescending { it.timestamp }.take(limit)

    override suspend fun getMeasurementsPage(sessionId: Long, limit: Int, offset: Int): List<Measurement> =
        measurements.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }.drop(offset).take(limit)

    override suspend fun getSessionAggregate(sessionId: Long): SessionAggregateRaw {
        val rows = measurements.filter { it.sessionId == sessionId }
        return SessionAggregateRaw(
            totalCount = rows.size,
            minRsrpDbm = rows.mapNotNull { it.rsrpDbm }.minOrNull(),
            maxRsrpDbm = rows.mapNotNull { it.rsrpDbm }.maxOrNull(),
            avgRsrpDbm = rows.mapNotNull { it.rsrpDbm }.map { it.toDouble() }.average().takeIf { !it.isNaN() },
            medianRsrpDbm = null,
            avgRsrqDb = null,
            avgSnrDb = null,
            countExcellent = 0, countGood = 0, countFair = 0, countWeak = 0, countVeryWeak = 0, countNoSignal = 0, countUnknown = rows.size,
            minLat = rows.minOfOrNull { it.latitude },
            maxLat = rows.maxOfOrNull { it.latitude },
            minLon = rows.minOfOrNull { it.longitude },
            maxLon = rows.maxOfOrNull { it.longitude },
        )
    }

    override suspend fun importMeasurements(sessionId: Long, measurements: List<Measurement>) {
        this.measurements.addAll(measurements.map { it.copy(sessionId = sessionId) })
    }
}
