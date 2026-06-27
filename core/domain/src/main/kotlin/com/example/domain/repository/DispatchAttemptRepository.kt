package com.example.domain.repository

import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptResult
import kotlinx.coroutines.flow.Flow

interface DispatchAttemptRepository {
    suspend fun upsert(attempt: DispatchAttempt)

    fun countDeadLettered(): Flow<Int>

    fun countFailureRecoveryQueue(): Flow<Int>

    suspend fun getFailureRecoveryQueue(limit: Int = 100): List<DispatchAttempt>

    suspend fun getLatestFailureForMessageDraft(messageDraftId: MessageDraftId): DispatchAttempt?

    suspend fun updateOutcome(
        id: DispatchAttemptId,
        attemptedAtMs: Long?,
        resolvedAtMs: Long?,
        result: DispatchAttemptResult,
        channel: MessageChannel?,
        deliveryStatus: MessageDeliveryStatus,
        providerMessageId: String?,
        errorType: String?,
        errorCode: String?,
        redactedErrorMessage: String?,
        retryCount: Int,
        nextRetryAtMs: Long?,
        deadLetteredAtMs: Long?,
    )
}
