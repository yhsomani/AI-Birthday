package com.example.data.repository

import com.example.core.db.dao.ActivityLogDao
import com.example.domain.activity.toEntity
import com.example.domain.activity.toRecord
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityLogRepositoryImpl @Inject constructor(
    private val activityLogDao: ActivityLogDao,
) : ActivityLogRepository {
    override fun getRecent(limit: Int): Flow<List<ActivityLogRecord>> =
        activityLogDao.getRecent(limit).map { entries ->
            entries.map { it.toRecord() }
        }

    override fun getByType(type: String, limit: Int): Flow<List<ActivityLogRecord>> =
        activityLogDao.getByType(type, limit).map { entries ->
            entries.map { it.toRecord() }
        }

    override fun getByStatus(status: String, limit: Int): Flow<List<ActivityLogRecord>> =
        activityLogDao.getByStatus(status, limit).map { entries ->
            entries.map { it.toRecord() }
        }

    override fun search(query: String, limit: Int): Flow<List<ActivityLogRecord>> =
        activityLogDao.search(query, limit).map { entries ->
            entries.map { it.toRecord() }
        }

    override suspend fun record(entry: ActivityLogRecord) = activityLogDao.insert(entry.toEntity())

    override suspend fun deleteOlderThan(cutoffMs: Long) = activityLogDao.deleteOlderThan(cutoffMs)
}
