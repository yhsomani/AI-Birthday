package com.example.data.repository

import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.DiagnosticSnapshotEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.db.entities.StyleProfileHistoryEntity
import com.example.domain.model.activity.ActivityLogRecord
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DiagnosticSnapshotId
import com.example.domain.model.common.GiftHistoryId
import com.example.domain.model.common.MemoryNoteId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.diagnostic.DiagnosticSnapshot
import com.example.domain.model.diagnostic.DiagnosticSnapshotSource
import com.example.domain.model.diagnostic.DiagnosticSnapshotStatus
import com.example.domain.model.gift.GiftHistoryRecord
import com.example.domain.model.memory.MemoryNoteRecord
import com.example.domain.model.message.MessageFeedbackRecord
import com.example.domain.model.style.StyleProfileHistoryRecord
import com.example.domain.model.style.StyleProfileRecord

internal fun ActivityLogEntity.toRecord(): ActivityLogRecord {
    return ActivityLogRecord(
        id = id,
        type = type,
        title = title,
        detail = detail,
        contactId = contactId,
        eventId = eventId,
        messageId = messageId,
        severity = severity,
        status = status,
        actionRoute = actionRoute,
        metadataJson = metadataJson,
        createdAtMs = createdAtMs,
    )
}

internal fun ActivityLogRecord.toEntity(): ActivityLogEntity {
    return ActivityLogEntity(
        id = id,
        type = type,
        title = title,
        detail = detail,
        contactId = contactId,
        eventId = eventId,
        messageId = messageId,
        severity = severity,
        status = status,
        actionRoute = actionRoute,
        metadataJson = metadataJson,
        createdAtMs = createdAtMs,
    )
}

internal fun DiagnosticSnapshotEntity.toDiagnosticSnapshot(): DiagnosticSnapshot {
    return DiagnosticSnapshot(
        id = DiagnosticSnapshotId(id),
        source = DiagnosticSnapshotSource.fromRaw(source),
        status = DiagnosticSnapshotStatus.fromRaw(status),
        summary = summary,
        checksJson = checksJson,
        createdAtMs = createdAtMs,
    )
}

internal fun DiagnosticSnapshot.toEntity(): DiagnosticSnapshotEntity {
    return DiagnosticSnapshotEntity(
        id = id.value,
        source = source.raw,
        status = status.raw,
        summary = summary,
        checksJson = checksJson,
        createdAtMs = createdAtMs,
    )
}

internal fun GiftHistoryEntity.toRecord(): GiftHistoryRecord {
    return GiftHistoryRecord(
        id = GiftHistoryId(id),
        contactId = ContactId(contactId),
        giftName = giftName,
        giftCategory = giftCategory,
        occasionType = occasionType,
        year = year,
        approxCostInr = approxCostInr,
        receivedWell = receivedWell,
        notes = notes,
    )
}

internal fun GiftHistoryRecord.toEntity(): GiftHistoryEntity {
    return GiftHistoryEntity(
        id = id.value,
        contactId = contactId.value,
        giftName = giftName,
        giftCategory = giftCategory,
        occasionType = occasionType,
        year = year,
        approxCostInr = approxCostInr,
        receivedWell = receivedWell,
        notes = notes,
    )
}

internal fun MemoryNoteEntity.toRecord(): MemoryNoteRecord {
    return MemoryNoteRecord(
        id = MemoryNoteId(id),
        contactId = ContactId(contactId),
        noteText = noteText,
        category = category,
        dateMs = dateMs,
        isPinned = isPinned,
    )
}

internal fun MemoryNoteRecord.toEntity(): MemoryNoteEntity {
    return MemoryNoteEntity(
        id = id.value,
        contactId = contactId.value,
        noteText = noteText,
        category = category,
        dateMs = dateMs,
        isPinned = isPinned,
    )
}

internal fun MessageFeedbackEntity.toRecord(): MessageFeedbackRecord {
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

internal fun MessageFeedbackRecord.toEntity(): MessageFeedbackEntity {
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

internal fun StyleProfileEntity.toRecord(): StyleProfileRecord {
    return StyleProfileRecord(
        id = id,
        sampleMessagesJson = sampleMessagesJson,
        usesEmoji = usesEmoji,
        avgMessageLength = avgMessageLength,
        commonPhrasesJson = commonPhrasesJson,
        commonGreetingsJson = commonGreetingsJson,
        formalityLevel = formalityLevel,
        preferredLanguage = preferredLanguage,
        emojiSetJson = emojiSetJson,
        avoidPhrasesJson = avoidPhrasesJson,
        toneDescriptors = toneDescriptors,
        sampleCount = sampleCount,
        updatedAtMs = updatedAtMs,
    )
}

internal fun StyleProfileRecord.toEntity(): StyleProfileEntity {
    return StyleProfileEntity(
        id = id,
        sampleMessagesJson = sampleMessagesJson,
        usesEmoji = usesEmoji,
        avgMessageLength = avgMessageLength,
        commonPhrasesJson = commonPhrasesJson,
        commonGreetingsJson = commonGreetingsJson,
        formalityLevel = formalityLevel,
        preferredLanguage = preferredLanguage,
        emojiSetJson = emojiSetJson,
        avoidPhrasesJson = avoidPhrasesJson,
        toneDescriptors = toneDescriptors,
        sampleCount = sampleCount,
        updatedAtMs = updatedAtMs,
    )
}

internal fun StyleProfileHistoryEntity.toRecord(): StyleProfileHistoryRecord {
    return StyleProfileHistoryRecord(
        id = id,
        profileJson = profileJson,
        savedAtMs = savedAtMs,
        source = source,
    )
}

internal fun StyleProfileHistoryRecord.toEntity(): StyleProfileHistoryEntity {
    return StyleProfileHistoryEntity(
        id = id,
        profileJson = profileJson,
        savedAtMs = savedAtMs,
        source = source,
    )
}
