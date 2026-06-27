package com.example.domain.model.message

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class RetryableMessageDraft(
    val id: MessageDraftId,
    val contactId: ContactId,
    val occasionId: OccasionId,
    val channel: MessageChannel,
    val status: MessageStatus,
    val scheduledForMs: Long,
) {
    fun queuedForRetry(retryAtMs: Long): RetryQueuedMessageDraft {
        return RetryQueuedMessageDraft(
            id = id,
            status = MessageStatus.APPROVED,
            scheduledForMs = retryAtMs,
        )
    }
}

data class RetryQueuedMessageDraft(
    val id: MessageDraftId,
    val status: MessageStatus,
    val scheduledForMs: Long,
)
