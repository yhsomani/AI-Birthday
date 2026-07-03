package com.example.data.repository

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.PendingMessageRecord

internal fun PendingMessageEntity.toPendingMessageRecord(): PendingMessageRecord {
    return PendingMessageRecord(
        id = MessageDraftId(id),
        contactId = ContactId(contactId),
        occasionId = OccasionId(eventId),
        shortVariant = shortVariant,
        standardVariant = standardVariant,
        longVariant = longVariant,
        formalVariant = formalVariant,
        funnyVariant = funnyVariant,
        emotionalVariant = emotionalVariant,
        selectedVariant = selectedVariant,
        selectedVariantText = selectedVariantText,
        channel = MessageChannel.fromRaw(channel),
        scheduledForMs = scheduledForMs,
        approvalMode = ApprovalMode.fromRaw(approvalMode),
        status = MessageStatus.fromRaw(status),
        aiModel = aiModel,
        generatedAtMs = generatedAtMs,
        editedByUser = editedByUser,
        userEditedText = userEditedText,
        qualityScore = qualityScore,
        tone = tone,
        length = length,
        includeEmoji = includeEmoji,
        scheduledYear = scheduledYear,
        isUsingFallback = isUsingFallback,
    )
}

internal fun Iterable<PendingMessageEntity>.toPendingMessageRecords(): List<PendingMessageRecord> {
    return map { it.toPendingMessageRecord() }
}

internal fun PendingMessageRecord.toPendingMessageEntity(): PendingMessageEntity {
    return PendingMessageEntity(
        id = id.value,
        contactId = contactId.value,
        eventId = occasionId.value,
        shortVariant = shortVariant,
        standardVariant = standardVariant,
        longVariant = longVariant,
        formalVariant = formalVariant,
        funnyVariant = funnyVariant,
        emotionalVariant = emotionalVariant,
        selectedVariant = selectedVariant,
        selectedVariantText = selectedVariantText,
        channel = channel.raw,
        scheduledForMs = scheduledForMs,
        approvalMode = approvalMode.raw,
        status = status.raw,
        aiModel = aiModel,
        generatedAtMs = generatedAtMs.takeIf { it > 0L } ?: System.currentTimeMillis(),
        editedByUser = editedByUser,
        userEditedText = userEditedText,
        qualityScore = qualityScore,
        tone = tone,
        length = length,
        includeEmoji = includeEmoji,
        scheduledYear = scheduledYear,
        isUsingFallback = isUsingFallback,
    )
}
