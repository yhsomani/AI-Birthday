package com.example.core.db.dao

import androidx.room.*
import com.example.core.db.entities.PendingMessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages")
    fun getAll(): Flow<List<PendingMessageEntity>>

    @Query("SELECT * FROM pending_messages WHERE status = 'APPROVED'")
    suspend fun getAllApproved(): List<PendingMessageEntity>

    @Query("""
        SELECT * FROM pending_messages
        WHERE status = 'APPROVED'
           OR (status = 'PENDING' AND approvalMode = 'SMART_APPROVE')
    """)
    suspend fun getBootRecoverableAutoSends(): List<PendingMessageEntity>

    @Query("SELECT * FROM pending_messages WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingMessageEntity?
    
    @Query("SELECT * FROM pending_messages WHERE eventId = :eventId LIMIT 1")
    suspend fun getByEventId(eventId: String): PendingMessageEntity?

    @Query("SELECT * FROM pending_messages WHERE contactId = :contactId AND eventId = :eventId AND scheduledYear = :scheduledYear LIMIT 1")
    suspend fun getPendingMessage(contactId: String, eventId: String, scheduledYear: Int): PendingMessageEntity?
    
    @Query("SELECT EXISTS(SELECT 1 FROM pending_messages WHERE eventId = :eventId)")
    suspend fun existsForEvent(eventId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM pending_messages WHERE contactId = :contactId AND eventId = :eventId AND scheduledYear = :scheduledYear)")
    suspend fun existsForEventOccurrence(contactId: String, eventId: String, scheduledYear: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: PendingMessageEntity)

    @Query("UPDATE pending_messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE pending_messages SET status = :status WHERE eventId = :eventId")
    suspend fun updateStatusByEventId(eventId: String, status: String)

    @Query("SELECT * FROM pending_messages")
    suspend fun getAllSync(): List<PendingMessageEntity>

    @Query("UPDATE pending_messages SET isUsingFallback = :isUsingFallback WHERE id = :messageId")
    suspend fun setFallbackFlag(messageId: String, isUsingFallback: Boolean)

    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>
}
