package com.example.data.repository

import com.example.core.db.dao.EventDao
import com.example.core.db.entities.EventEntity
import com.example.domain.repository.EventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val eventDao: EventDao
) : EventRepository {

    override fun getAll(): Flow<List<EventEntity>> = eventDao.getAll()

    override suspend fun getEventsBefore(timeMs: Long): List<EventEntity> = eventDao.getEventsBefore(timeMs)

    override suspend fun getUpcoming(days: Int): List<EventEntity> = eventDao.getUpcoming(days)

    override suspend fun upsert(event: EventEntity) = eventDao.upsert(event)

    override suspend fun delete(event: EventEntity) = eventDao.delete(event)
}
