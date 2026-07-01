package com.example.domain.message

import com.example.core.db.entities.MessageFeedbackEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageFeedbackRecord

fun MessageFeedbackEntity.toRecord(): MessageFeedbackRecord {
    return MessageFeedbackRecord(
        id = MessageFeedbackId(id),
        pendingMessageId = MessageDraftId(pendingMessageId),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
        reasonKey = reasonKey,
        instruction = instruction,
        draftText = draftText,
        appliedToRegeneration = appliedToRegeneration,
        createdAtMs = createdAtMs,
    )
}

fun MessageFeedbackRecord.toEntity(): MessageFeedbackEntity {
    return MessageFeedbackEntity(
        id = id.value,
        pendingMessageId = pendingMessageId.value,
        contactId = contactId.value,
        eventId = occasionId.value,
        reasonKey = reasonKey,
        instruction = instruction,
        draftText = draftText,
        appliedToRegeneration = appliedToRegeneration,
        createdAtMs = createdAtMs,
    )
}
