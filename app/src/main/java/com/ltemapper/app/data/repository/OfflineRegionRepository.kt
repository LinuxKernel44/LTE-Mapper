package com.ltemapper.app.data.repository

import com.ltemapper.app.data.dao.OfflineRegionDao
import com.ltemapper.app.data.entities.toDomain
import com.ltemapper.app.data.entities.toEntity
import com.ltemapper.app.domain.model.OfflineRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflineRegionRepository @Inject constructor(private val dao: OfflineRegionDao) {

    fun observeRegions(): Flow<List<OfflineRegion>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun save(region: OfflineRegion): Long = dao.insert(region.toEntity())

    suspend fun delete(region: OfflineRegion) = dao.delete(region.toEntity())

    suspend fun deleteAll() = dao.deleteAll()
}
