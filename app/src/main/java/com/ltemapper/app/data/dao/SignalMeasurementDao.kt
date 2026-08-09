package com.ltemapper.app.data.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ltemapper.app.data.entities.SignalMeasurementEntity
import kotlinx.coroutines.flow.Flow

data class BasicAggregate(
    val totalCount: Int,
    val minRsrp: Int?,
    val maxRsrp: Int?,
    val avgRsrp: Double?,
    val avgRsrq: Double?,
    val avgSnr: Double?,
    val minLat: Double?,
    val maxLat: Double?,
    val minLon: Double?,
    val maxLon: Double?,
)

data class QualityCount(val signalQuality: String, val count: Int)

@Dao
interface SignalMeasurementDao {
    @Insert
    suspend fun insert(measurement: SignalMeasurementEntity): Long

    @Insert
    suspend fun insertAll(measurements: List<SignalMeasurementEntity>)

    @Query("SELECT COUNT(*) FROM signal_measurements WHERE sessionId = :sessionId")
    fun observeCount(sessionId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM signal_measurements WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    @Query("SELECT * FROM signal_measurements WHERE sessionId = :sessionId ORDER BY timestamp DESC")
    fun pagingSource(sessionId: Long): PagingSource<Int, SignalMeasurementEntity>

    @Query(
        "SELECT * FROM signal_measurements WHERE sessionId = :sessionId AND networkType IN (:types) " +
            "AND (id % :stride) = 0 ORDER BY id ASC LIMIT :limit",
    )
    suspend fun getForDisplay(sessionId: Long, types: List<String>, stride: Int, limit: Int): List<SignalMeasurementEntity>

    @Query("SELECT * FROM signal_measurements WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getLatest(sessionId: Long, limit: Int): List<SignalMeasurementEntity>

    @Query("SELECT * FROM signal_measurements WHERE sessionId = :sessionId ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getPage(sessionId: Long, limit: Int, offset: Int): List<SignalMeasurementEntity>

    @Query(
        "SELECT COUNT(*) as totalCount, MIN(rsrp) as minRsrp, MAX(rsrp) as maxRsrp, AVG(rsrp) as avgRsrp, " +
            "AVG(rsrq) as avgRsrq, AVG(rssnr) as avgSnr, MIN(latitude) as minLat, MAX(latitude) as maxLat, " +
            "MIN(longitude) as minLon, MAX(longitude) as maxLon FROM signal_measurements WHERE sessionId = :sessionId",
    )
    suspend fun getBasicAggregate(sessionId: Long): BasicAggregate

    @Query("SELECT signalQuality, COUNT(*) as count FROM signal_measurements WHERE sessionId = :sessionId GROUP BY signalQuality")
    suspend fun getQualityCounts(sessionId: Long): List<QualityCount>

    @Query("SELECT COUNT(*) FROM signal_measurements WHERE sessionId = :sessionId AND rsrp IS NOT NULL")
    suspend fun getRsrpCount(sessionId: Long): Int

    @Query("SELECT rsrp FROM signal_measurements WHERE sessionId = :sessionId AND rsrp IS NOT NULL ORDER BY rsrp ASC LIMIT 1 OFFSET :offset")
    suspend fun getRsrpAtOffset(sessionId: Long, offset: Int): Int?

    @Query("DELETE FROM signal_measurements WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    @Query("DELETE FROM signal_measurements")
    suspend fun deleteAll()
}
