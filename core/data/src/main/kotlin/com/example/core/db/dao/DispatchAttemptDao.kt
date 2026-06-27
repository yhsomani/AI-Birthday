package com.example.core.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.db.entities.DispatchAttemptEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DispatchAttemptDao {
    @Query("SELECT * FROM dispatch_attempts WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DispatchAttemptEntity?

    @Query("SELECT * FROM dispatch_attempts WHERE messageDraftId = :messageDraftId ORDER BY requestedAtMs DESC")
    fun getByMessageDraft(messageDraftId: String): Flow<List<DispatchAttemptEntity>>

    @Query(
        """
        SELECT * FROM dispatch_attempts
        WHERE deadLetteredAtMs IS NOT NULL
           OR result IN ('FAILED_RETRYABLE', 'FAILED_FINAL')
        ORDER BY COALESCE(deadLetteredAtMs, resolvedAtMs, requestedAtMs) DESC
        LIMIT :limit
        """,
    )
    suspend fun getFailureRecoveryQueue(limit: Int = 100): List<DispatchAttemptEntity>

    @Query(
        """
        SELECT * FROM dispatch_attempts
        WHERE messageDraftId = :messageDraftId
          AND (
              deadLetteredAtMs IS NOT NULL
              OR result IN ('FAILED_RETRYABLE', 'FAILED_FINAL')
          )
        ORDER BY COALESCE(deadLetteredAtMs, resolvedAtMs, requestedAtMs) DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestFailureForMessageDraft(messageDraftId: String): DispatchAttemptEntity?

    @Query("SELECT COUNT(*) FROM dispatch_attempts WHERE deadLetteredAtMs IS NOT NULL")
    fun countDeadLettered(): Flow<Int>

    @Query(
        """
        SELECT COUNT(*) FROM dispatch_attempts
        WHERE deadLetteredAtMs IS NOT NULL
           OR result IN ('FAILED_RETRYABLE', 'FAILED_FINAL')
        """,
    )
    fun countFailureRecoveryQueue(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attempt: DispatchAttemptEntity)

    @Query(
        """
        UPDATE dispatch_attempts
        SET attemptedAtMs = :attemptedAtMs,
            resolvedAtMs = :resolvedAtMs,
            result = :result,
            channel = COALESCE(:channel, channel),
            deliveryStatus = :deliveryStatus,
            providerMessageId = :providerMessageId,
            errorType = :errorType,
            errorCode = :errorCode,
            redactedErrorMessage = :redactedErrorMessage,
            retryCount = :retryCount,
            nextRetryAtMs = :nextRetryAtMs,
            deadLetteredAtMs = :deadLetteredAtMs
        WHERE id = :id
        """,
    )
    suspend fun updateOutcome(
        id: String,
        attemptedAtMs: Long?,
        resolvedAtMs: Long?,
        result: String,
        channel: String?,
        deliveryStatus: String,
        providerMessageId: String?,
        errorType: String?,
        errorCode: String?,
        redactedErrorMessage: String?,
        retryCount: Int,
        nextRetryAtMs: Long?,
        deadLetteredAtMs: Long?,
    )

    @Query("SELECT * FROM dispatch_attempts ORDER BY requestedAtMs DESC")
    suspend fun getAllSync(): List<DispatchAttemptEntity>

    @Query("DELETE FROM dispatch_attempts")
    suspend fun deleteAll()
}
