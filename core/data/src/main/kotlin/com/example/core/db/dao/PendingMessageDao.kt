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

    @Query("SELECT * FROM pending_messages WHERE status = 'DISPATCHING'")
    suspend fun getDispatchingMessages(): List<PendingMessageEntity>

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

    @Query("""
        UPDATE pending_messages
        SET status = 'SENT'
        WHERE id = :id
          AND status IN ('PENDING', 'APPROVED', 'DISPATCHING', 'SENT')
    """)
    suspend fun markSmsHandoffSentIfAwaitingCallback(id: String): Int

    @Query("""
        UPDATE pending_messages
        SET status = 'FAILED'
        WHERE id = :id
          AND status IN ('PENDING', 'APPROVED', 'DISPATCHING', 'SENT')
    """)
    suspend fun markSmsCallbackFailed(id: String): Int

    @Query("""
        UPDATE pending_messages
        SET status = :newStatus
        WHERE id = :id AND status = :expectedStatus
    """)
    suspend fun updateStatusIfCurrent(
        id: String,
        expectedStatus: String,
        newStatus: String,
    ): Int

    @Query("UPDATE pending_messages SET scheduledForMs = :scheduledForMs WHERE id = :id")
    suspend fun updateScheduledFor(id: String, scheduledForMs: Long)

    @Query("""
        UPDATE pending_messages
        SET status = :status,
            selectedVariantText = :selectedVariantText,
            editedByUser = :editedByUser,
            userEditedText = :userEditedText
        WHERE id = :id
    """)
    suspend fun updateApprovalState(
        id: String,
        status: String,
        selectedVariantText: String,
        editedByUser: Boolean,
        userEditedText: String?,
    )

    @Query("""
        UPDATE pending_messages
        SET status = :status,
            scheduledForMs = :scheduledForMs
        WHERE id = :id
    """)
    suspend fun updateRetryState(
        id: String,
        status: String,
        scheduledForMs: Long,
    )

    @Query("""
        UPDATE pending_messages
        SET status = :newStatus,
            scheduledForMs = :scheduledForMs
        WHERE id = :id AND status = :expectedStatus
    """)
    suspend fun updateStatusAndScheduledForIfCurrent(
        id: String,
        expectedStatus: String,
        newStatus: String,
        scheduledForMs: Long,
    ): Int

    @Query("UPDATE pending_messages SET status = :status WHERE eventId = :eventId")
    suspend fun updateStatusByEventId(eventId: String, status: String)

    @Query("SELECT * FROM pending_messages")
    suspend fun getAllSync(): List<PendingMessageEntity>

    @Query("DELETE FROM pending_messages")
    suspend fun deleteAll()

    @Query("UPDATE pending_messages SET isUsingFallback = :isUsingFallback WHERE id = :messageId")
    suspend fun setFallbackFlag(messageId: String, isUsingFallback: Boolean)

    @Query("SELECT COUNT(*) FROM pending_messages WHERE status = 'PENDING'")
    fun countPending(): Flow<Int>
}
