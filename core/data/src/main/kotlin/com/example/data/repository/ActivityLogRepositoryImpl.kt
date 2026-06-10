package com.example.data.repository

import com.example.core.db.dao.ActivityLogDao
import com.example.core.db.entities.ActivityLogEntity
import com.example.domain.repository.ActivityLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ActivityLogRepositoryImpl @Inject constructor(
    private val activityLogDao: ActivityLogDao,
) : ActivityLogRepository {
    override fun getRecent(limit: Int): Flow<List<ActivityLogEntity>> = activityLogDao.getRecent(limit)

    override fun getByType(type: String, limit: Int): Flow<List<ActivityLogEntity>> =
        activityLogDao.getByType(type, limit)

    override suspend fun record(entry: ActivityLogEntity) = activityLogDao.insert(entry)

    override suspend fun deleteOlderThan(cutoffMs: Long) = activityLogDao.deleteOlderThan(cutoffMs)
}
