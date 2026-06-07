package com.example.domain.repository

import com.example.core.db.entities.StyleProfileEntity
import kotlinx.coroutines.flow.Flow

interface StyleProfileRepository {
    fun getProfile(): Flow<StyleProfileEntity?>
    suspend fun getProfileOnce(): StyleProfileEntity?
    suspend fun upsert(profile: StyleProfileEntity)
    suspend fun upsertWithHistory(profile: StyleProfileEntity, history: com.example.core.db.entities.StyleProfileHistoryEntity)
    suspend fun getHistory(): List<com.example.core.db.entities.StyleProfileHistoryEntity>
}
