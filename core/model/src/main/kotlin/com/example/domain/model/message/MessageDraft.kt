package com.example.domain.model.message

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId

data class MessageDraft(
    val id: MessageDraftId,
    val contactId: ContactId,
    val occasionId: OccasionId,
    val scheduledForMs: Long,
    val approvalMode: ApprovalMode,
    val status: MessageStatus,
    val channel: MessageChannel,
    val scheduledYear: Int,
    val qualityScore: Int,
    val isUsingFallback: Boolean,
)

