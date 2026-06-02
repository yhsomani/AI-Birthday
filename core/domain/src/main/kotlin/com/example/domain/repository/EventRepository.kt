package com.example.domain.repository

import com.example.core.db.entities.EventEntity
import kotlinx.coroutines.flow.Flow

interface EventRepository {
    fun getAll(): Flow<List<EventEntity>>
    suspend fun getEventsBefore(timeMs: Long): List<EventEntity>
    suspend fun getUpcoming(days: Int): List<EventEntity>
    suspend fun upsert(event: EventEntity)
    suspend fun delete(event: EventEntity)
}
