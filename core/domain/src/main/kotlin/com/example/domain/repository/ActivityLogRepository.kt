package com.example.domain.repository

import com.example.core.db.entities.ActivityLogEntity
import kotlinx.coroutines.flow.Flow

interface ActivityLogRepository {
    fun getRecent(limit: Int): Flow<List<ActivityLogEntity>>
    fun getByType(type: String, limit: Int): Flow<List<ActivityLogEntity>>
    suspend fun record(entry: ActivityLogEntity)
    suspend fun deleteOlderThan(cutoffMs: Long)
}
