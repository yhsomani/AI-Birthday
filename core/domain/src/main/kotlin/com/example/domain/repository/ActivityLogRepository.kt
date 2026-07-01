package com.example.domain.repository

import com.example.domain.model.activity.ActivityLogRecord
import kotlinx.coroutines.flow.Flow

interface ActivityLogRepository {
    fun getRecent(limit: Int): Flow<List<ActivityLogRecord>>
    fun getByType(type: String, limit: Int): Flow<List<ActivityLogRecord>>
    fun getByStatus(status: String, limit: Int): Flow<List<ActivityLogRecord>>
    fun search(query: String, limit: Int): Flow<List<ActivityLogRecord>>
    suspend fun record(entry: ActivityLogRecord)
    suspend fun deleteOlderThan(cutoffMs: Long)
}
