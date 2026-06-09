package com.example.domain.repository

import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getAllPending(): Flow<List<PendingMessageEntity>>
    suspend fun getAllApproved(): List<PendingMessageEntity>
    suspend fun getPendingById(id: String): PendingMessageEntity?
    suspend fun getPendingByEventId(eventId: String): PendingMessageEntity?
    suspend fun getPendingForEventOccurrence(contactId: String, eventId: String, scheduledYear: Int): PendingMessageEntity?
    suspend fun pendingExistsForEvent(eventId: String): Boolean
    suspend fun pendingExistsForEventOccurrence(contactId: String, eventId: String, scheduledYear: Int): Boolean
    suspend fun insertPending(message: PendingMessageEntity)
    suspend fun updatePendingStatus(id: String, status: String)
    suspend fun updatePendingStatusByEventId(eventId: String, status: String)
    fun getAllSent(): Flow<List<SentMessageEntity>>
    suspend fun getSentByContact(contactId: String, limit: Int): List<SentMessageEntity>
    suspend fun getRecentForStyleAnalysis(sinceMs: Long, limit: Int = 100): List<SentMessageEntity>
    suspend fun getSentSinceYearStart(yearStartMs: Long): List<SentMessageEntity>
    fun countAllSent(): Flow<Int>
    fun countPending(): Flow<Int>
    suspend fun insertSent(message: SentMessageEntity)
}
