package com.ltemapper.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.ltemapper.app.data.entities.OfflineRegionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OfflineRegionDao {
    @Insert
    suspend fun insert(region: OfflineRegionEntity): Long

    @Query("SELECT * FROM offline_regions ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<OfflineRegionEntity>>

    @Query("SELECT * FROM offline_regions WHERE id = :id")
    suspend fun getById(id: Long): OfflineRegionEntity?

    @Delete
    suspend fun delete(region: OfflineRegionEntity)

    @Query("DELETE FROM offline_regions")
    suspend fun deleteAll()
}
