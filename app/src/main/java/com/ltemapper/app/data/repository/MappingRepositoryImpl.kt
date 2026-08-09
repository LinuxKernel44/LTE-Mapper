package com.ltemapper.app.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.ltemapper.app.data.dao.MappingSessionDao
import com.ltemapper.app.data.dao.SignalMeasurementDao
import com.ltemapper.app.data.entities.toDomain
import com.ltemapper.app.data.entities.toEntity
import com.ltemapper.app.domain.model.MappingSession
import com.ltemapper.app.domain.model.Measurement
import com.ltemapper.app.domain.model.NetworkFilter
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SessionAggregateRaw
import com.ltemapper.app.domain.repository.MAX_MAP_RENDER_POINTS
import com.ltemapper.app.domain.repository.MappingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MappingRepositoryImpl @Inject constructor(
    private val sessionDao: MappingSessionDao,
    private val measurementDao: SignalMeasurementDao,
) : MappingRepository {

    override suspend fun createSession(startTime: Long): Long =
        sessionDao.insert(
            com.ltemapper.app.data.entities.MappingSessionEntity(
                startTime = startTime,
                endTime = null,
                distanceMeters = 0.0,
                measurementCount = 0,
            ),
        )

    override suspend fun endSession(sessionId: Long, endTime: Long, distanceMeters: Double) {
        val count = measurementDao.count(sessionId)
        sessionDao.finishSession(sessionId, endTime, distanceMeters, count)
    }

    override suspend fun updateSessionProgress(sessionId: Long, distanceMeters: Double) {
        sessionDao.updateProgress(sessionId, distanceMeters)
    }

    override suspend fun deleteSession(sessionId: Long) {
        sessionDao.deleteById(sessionId)
    }

    override suspend fun deleteAllData() {
        measurementDao.deleteAll()
        sessionDao.deleteAll()
    }

    override suspend fun insertMeasurement(measurement: Measurement): Long =
        measurementDao.insert(measurement.toEntity())

    override suspend fun insertMeasurements(measurements: List<Measurement>) {
        measurementDao.insertAll(measurements.map { it.toEntity() })
    }

    override fun observeSessions(): Flow<List<MappingSession>> =
        sessionDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getSession(sessionId: Long): MappingSession? =
        sessionDao.getById(sessionId)?.toDomain()

    override fun observeSession(sessionId: Long): Flow<MappingSession?> =
        sessionDao.observeById(sessionId).map { it?.toDomain() }

    override fun observeMeasurementCount(sessionId: Long): Flow<Int> =
        measurementDao.observeCount(sessionId)

    override fun pagedMeasurements(sessionId: Long): Flow<PagingData<Measurement>> =
        Pager(
            config = PagingConfig(pageSize = 50, enablePlaceholders = false),
            pagingSourceFactory = { measurementDao.pagingSource(sessionId) },
        ).flow.map { pagingData -> pagingData.map { it.toDomain() } }

    override suspend fun getMeasurementsForDisplay(sessionId: Long, networkFilter: NetworkFilter): List<Measurement> {
        val total = measurementDao.count(sessionId)
        if (total == 0) return emptyList()
        val stride = (total / MAX_MAP_RENDER_POINTS).coerceAtLeast(1)
        val types = acceptedTypes(networkFilter)
        return measurementDao.getForDisplay(sessionId, types, stride, MAX_MAP_RENDER_POINTS)
            .map { it.toDomain() }
    }

    override suspend fun getLatestMeasurements(sessionId: Long, limit: Int): List<Measurement> =
        measurementDao.getLatest(sessionId, limit).map { it.toDomain() }

    override suspend fun getMeasurementsPage(sessionId: Long, limit: Int, offset: Int): List<Measurement> =
        measurementDao.getPage(sessionId, limit, offset).map { it.toDomain() }

    override suspend fun getSessionAggregate(sessionId: Long): SessionAggregateRaw {
        val basic = measurementDao.getBasicAggregate(sessionId)
        val qualityCounts = measurementDao.getQualityCounts(sessionId).associate { it.signalQuality to it.count }
        val rsrpCount = measurementDao.getRsrpCount(sessionId)
        val median = if (rsrpCount > 0) measurementDao.getRsrpAtOffset(sessionId, (rsrpCount - 1) / 2) else null

        fun countOf(name: String) = qualityCounts[name] ?: 0

        return SessionAggregateRaw(
            totalCount = basic.totalCount,
            minRsrpDbm = basic.minRsrp,
            maxRsrpDbm = basic.maxRsrp,
            avgRsrpDbm = basic.avgRsrp,
            medianRsrpDbm = median,
            avgRsrqDb = basic.avgRsrq,
            avgSnrDb = basic.avgSnr,
            countExcellent = countOf("EXCELLENT"),
            countGood = countOf("GOOD"),
            countFair = countOf("FAIR"),
            countWeak = countOf("WEAK"),
            countVeryWeak = countOf("VERY_WEAK"),
            countNoSignal = countOf("NO_SIGNAL"),
            countUnknown = countOf("UNKNOWN"),
            minLat = basic.minLat,
            maxLat = basic.maxLat,
            minLon = basic.minLon,
            maxLon = basic.maxLon,
        )
    }

    override suspend fun importMeasurements(sessionId: Long, measurements: List<Measurement>) {
        measurementDao.insertAll(measurements.map { it.copy(sessionId = sessionId).toEntity() })
    }

    override suspend fun finalizeImportedSession(sessionId: Long, startTime: Long, endTime: Long, distanceMeters: Double) {
        val count = measurementDao.count(sessionId)
        sessionDao.finishImportedSession(sessionId, startTime, endTime, distanceMeters, count)
    }

    private fun acceptedTypes(filter: NetworkFilter): List<String> = when (filter) {
        NetworkFilter.ALL -> RanType.entries.map { it.name }
        NetworkFilter.LTE_ONLY -> listOf(RanType.LTE.name)
        NetworkFilter.NR_ONLY -> listOf(RanType.NR.name)
    }
}
