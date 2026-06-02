package com.example.data.repository

import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.repository.MessageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val pendingMessageDao: PendingMessageDao,
    private val sentMessageDao: SentMessageDao
) : MessageRepository {

    override fun getAllPending(): Flow<List<PendingMessageEntity>> = pendingMessageDao.getAll()

    override suspend fun getAllApproved(): List<PendingMessageEntity> = pendingMessageDao.getAllApproved()

    override suspend fun getPendingByEventId(eventId: String): PendingMessageEntity? = pendingMessageDao.getByEventId(eventId)

    override suspend fun pendingExistsForEvent(eventId: String): Boolean = pendingMessageDao.existsForEvent(eventId)

    override suspend fun insertPending(message: PendingMessageEntity) = pendingMessageDao.insert(message)

    override suspend fun updatePendingStatus(id: String, status: String) = pendingMessageDao.updateStatus(id, status)

    override suspend fun updatePendingStatusByEventId(eventId: String, status: String) = pendingMessageDao.updateStatusByEventId(eventId, status)

    override fun getAllSent(): Flow<List<SentMessageEntity>> = sentMessageDao.getAll()

    override suspend fun getSentByContact(contactId: String, limit: Int): List<SentMessageEntity> = sentMessageDao.getByContact(contactId, limit)

    override fun countAllSent(): Flow<Int> = sentMessageDao.countAll()

    override fun countPending(): Flow<Int> = pendingMessageDao.countPending()

    override suspend fun insertSent(message: SentMessageEntity) = sentMessageDao.insert(message)
}
