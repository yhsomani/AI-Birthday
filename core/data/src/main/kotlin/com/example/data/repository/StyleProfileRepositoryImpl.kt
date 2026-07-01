package com.example.data.repository

import com.example.core.db.dao.StyleProfileDao
import com.example.domain.model.style.StyleProfileHistoryRecord
import com.example.domain.model.style.StyleProfileRecord
import com.example.domain.repository.StyleProfileRepository
import com.example.domain.style.toEntity
import com.example.domain.style.toRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StyleProfileRepositoryImpl @Inject constructor(
    private val styleProfileDao: StyleProfileDao
) : StyleProfileRepository {
    override fun getProfile(): Flow<StyleProfileRecord?> =
        styleProfileDao.getFlow().map { it?.toRecord() }

    override suspend fun getProfileOnce(): StyleProfileRecord? =
        styleProfileDao.get()?.toRecord()

    override suspend fun upsert(profile: StyleProfileRecord) =
        styleProfileDao.upsert(profile.toEntity())

    override suspend fun upsertWithHistory(profile: StyleProfileRecord, history: StyleProfileHistoryRecord) =
        styleProfileDao.upsertWithHistory(profile.toEntity(), history.toEntity())

    override suspend fun getHistory(): List<StyleProfileHistoryRecord> =
        styleProfileDao.getHistory().map { it.toRecord() }
}
