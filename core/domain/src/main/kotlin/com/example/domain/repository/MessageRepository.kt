package com.example.domain.repository

import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getAllPending(): Flow<List<PendingMessageEntity>>
    suspend fun getAllApproved(): List<PendingMessageEntity>
    suspend fun getPendingByEventId(eventId: String): PendingMessageEntity?
    suspend fun pendingExistsForEvent(eventId: String): Boolean
    suspend fun insertPending(message: PendingMessageEntity)
    suspend fun updatePendingStatus(id: String, status: String)
    suspend fun updatePendingStatusByEventId(eventId: String, status: String)
    fun getAllSent(): Flow<List<SentMessageEntity>>
    suspend fun getSentByContact(contactId: String, limit: Int): List<SentMessageEntity>
    fun countAllSent(): Flow<Int>
    fun countPending(): Flow<Int>
    suspend fun insertSent(message: SentMessageEntity)
}
