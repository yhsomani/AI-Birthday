package com.example.domain.model.message

import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.common.OccasionId

data class MessageFeedbackRecord(
    val id: MessageFeedbackId,
    val pendingMessageId: MessageDraftId,
    val contactId: ContactId,
    val occasionId: OccasionId,
    val reasonKey: String,
    val instruction: String,
    val draftText: String,
    val appliedToRegeneration: Boolean = false,
    val createdAtMs: Long,
)
