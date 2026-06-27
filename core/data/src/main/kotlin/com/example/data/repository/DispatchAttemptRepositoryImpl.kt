package com.example.data.repository

import com.example.core.db.dao.DispatchAttemptDao
import com.example.domain.dispatch.toDispatchAttempt
import com.example.domain.dispatch.toEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.repository.DispatchAttemptRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

@Singleton
class DispatchAttemptRepositoryImpl @Inject constructor(
    private val dispatchAttemptDao: DispatchAttemptDao,
) : DispatchAttemptRepository {
    override suspend fun upsert(attempt: DispatchAttempt) {
        dispatchAttemptDao.upsert(attempt.toEntity())
    }

    override fun countDeadLettered(): Flow<Int> {
        return dispatchAttemptDao.countDeadLettered()
    }

    override fun countFailureRecoveryQueue(): Flow<Int> {
        return dispatchAttemptDao.countFailureRecoveryQueue()
    }

    override suspend fun getFailureRecoveryQueue(limit: Int): List<DispatchAttempt> {
        return dispatchAttemptDao.getFailureRecoveryQueue(limit).map { it.toDispatchAttempt() }
    }

    override suspend fun getLatestFailureForMessageDraft(messageDraftId: MessageDraftId): DispatchAttempt? {
        return dispatchAttemptDao.getLatestFailureForMessageDraft(messageDraftId.value)?.toDispatchAttempt()
    }

    override suspend fun updateOutcome(
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
    ) {
        dispatchAttemptDao.updateOutcome(
            id = id.value,
            attemptedAtMs = attemptedAtMs,
            resolvedAtMs = resolvedAtMs,
            result = result.raw,
            channel = channel?.raw,
            deliveryStatus = deliveryStatus.raw,
            providerMessageId = providerMessageId,
            errorType = errorType,
            errorCode = errorCode,
            redactedErrorMessage = redactedErrorMessage,
            retryCount = retryCount,
            nextRetryAtMs = nextRetryAtMs,
            deadLetteredAtMs = deadLetteredAtMs,
        )
    }
}
