package com.example.data.repository

import com.example.core.db.dao.StyleProfileDao
import com.example.core.db.entities.StyleProfileEntity
import com.example.domain.repository.StyleProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StyleProfileRepositoryImpl @Inject constructor(
    private val styleProfileDao: StyleProfileDao
) : StyleProfileRepository {
    override fun getProfile(): Flow<StyleProfileEntity?> = styleProfileDao.getFlow()
    override suspend fun getProfileOnce(): StyleProfileEntity? = styleProfileDao.get()
    override suspend fun upsert(profile: StyleProfileEntity) = styleProfileDao.upsert(profile)
}
