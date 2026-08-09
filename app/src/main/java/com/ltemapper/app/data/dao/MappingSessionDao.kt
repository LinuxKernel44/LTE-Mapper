package com.ltemapper.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ltemapper.app.data.entities.MappingSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MappingSessionDao {
    @Insert
    suspend fun insert(session: MappingSessionEntity): Long

    @Query("SELECT * FROM mapping_sessions ORDER BY startTime DESC")
    fun observeAll(): Flow<List<MappingSessionEntity>>

    @Query("SELECT * FROM mapping_sessions WHERE id = :id")
    suspend fun getById(id: Long): MappingSessionEntity?

    @Query("SELECT * FROM mapping_sessions WHERE id = :id")
    fun observeById(id: Long): Flow<MappingSessionEntity?>

    @Query("UPDATE mapping_sessions SET endTime = :endTime, distanceMeters = :distanceMeters, measurementCount = :count WHERE id = :id")
    suspend fun finishSession(id: Long, endTime: Long, distanceMeters: Double, count: Int)

    /** Corrects both bounds after a bulk import, where the true start/end are only known once
     * the whole file has been scanned (unlike live mapping, where startTime is accurate from the
     * moment the session row is created). */
    @Query("UPDATE mapping_sessions SET startTime = :startTime, endTime = :endTime, distanceMeters = :distanceMeters, measurementCount = :count WHERE id = :id")
    suspend fun finishImportedSession(id: Long, startTime: Long, endTime: Long, distanceMeters: Double, count: Int)

    @Query("UPDATE mapping_sessions SET distanceMeters = :distanceMeters, measurementCount = (SELECT COUNT(*) FROM signal_measurements WHERE sessionId = :id) WHERE id = :id")
    suspend fun updateProgress(id: Long, distanceMeters: Double)

    @Query("DELETE FROM mapping_sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM mapping_sessions")
    suspend fun deleteAll()
}
