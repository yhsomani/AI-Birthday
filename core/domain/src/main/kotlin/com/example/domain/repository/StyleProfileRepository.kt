package com.example.domain.repository

import com.example.domain.model.style.StyleProfileHistoryRecord
import com.example.domain.model.style.StyleProfileRecord
import kotlinx.coroutines.flow.Flow

interface StyleProfileRepository {
    fun getProfile(): Flow<StyleProfileRecord?>
    suspend fun getProfileOnce(): StyleProfileRecord?
    suspend fun upsert(profile: StyleProfileRecord)
    suspend fun upsertWithHistory(profile: StyleProfileRecord, history: StyleProfileHistoryRecord)
    suspend fun getHistory(): List<StyleProfileHistoryRecord>
}
