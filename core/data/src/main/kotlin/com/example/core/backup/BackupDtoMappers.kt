package com.example.core.backup

import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity

internal fun PendingMessageEntity.toBackupDto() = BackupPendingMessageDto(
    id = id,
    contactId = contactId,
    eventId = eventId,
    shortVariant = shortVariant,
    standardVariant = standardVariant,
    longVariant = longVariant,
    formalVariant = formalVariant,
    funnyVariant = funnyVariant,
    emotionalVariant = emotionalVariant,
    selectedVariant = selectedVariant,
    selectedVariantText = selectedVariantText,
    channel = channel,
    scheduledForMs = scheduledForMs,
    approvalMode = approvalMode,
    status = status,
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

internal fun BackupPendingMessageDto.toEntity() = PendingMessageEntity(
    id = id,
    contactId = contactId,
    eventId = eventId,
    shortVariant = shortVariant,
    standardVariant = standardVariant,
    longVariant = longVariant,
    formalVariant = formalVariant,
    funnyVariant = funnyVariant,
    emotionalVariant = emotionalVariant,
    selectedVariant = selectedVariant,
    selectedVariantText = selectedVariantText,
    channel = channel,
    scheduledForMs = scheduledForMs,
    approvalMode = approvalMode,
    status = status,
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

internal fun SentMessageEntity.toBackupDto() = BackupSentMessageDto(
    id = id,
    contactId = contactId,
    eventType = eventType,
    eventId = eventId,
    occasionType = occasionType,
    occasionLabel = occasionLabel,
    eventYear = eventYear,
    messageText = messageText,
    channel = channel,
    sentAtMs = sentAtMs,
    deliveryStatus = deliveryStatus,
    aiGenerated = aiGenerated,
    geminiModel = geminiModel,
    variantUsed = variantUsed,
    replyReceived = replyReceived,
    replyAtMs = replyAtMs,
    isContactDeleted = isContactDeleted,
)

internal fun BackupSentMessageDto.toEntity() = SentMessageEntity(
    id = id,
    contactId = contactId,
    eventType = eventType,
    eventId = eventId,
    occasionType = occasionType,
    occasionLabel = occasionLabel,
    eventYear = eventYear,
    messageText = messageText,
    channel = channel,
    sentAtMs = sentAtMs,
    deliveryStatus = deliveryStatus,
    aiGenerated = aiGenerated,
    geminiModel = geminiModel,
    variantUsed = variantUsed,
    replyReceived = replyReceived,
    replyAtMs = replyAtMs,
    isContactDeleted = isContactDeleted,
)

internal fun StyleProfileEntity.toBackupDto() = BackupStyleProfileDto(
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

internal fun BackupStyleProfileDto.toEntity() = StyleProfileEntity(
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

internal fun MemoryNoteEntity.toBackupDto() = BackupMemoryNoteDto(
    id = id,
    contactId = contactId,
    noteText = noteText,
    category = category,
    dateMs = dateMs,
    isPinned = isPinned,
)

internal fun BackupMemoryNoteDto.toEntity() = MemoryNoteEntity(
    id = id,
    contactId = contactId,
    noteText = noteText,
    category = category,
    dateMs = dateMs,
    isPinned = isPinned,
)

internal fun GiftHistoryEntity.toBackupDto() = BackupGiftHistoryDto(
    id = id,
    contactId = contactId,
    giftName = giftName,
    giftCategory = giftCategory,
    occasionType = occasionType,
    year = year,
    approxCostInr = approxCostInr,
    receivedWell = receivedWell,
    notes = notes,
)

internal fun BackupGiftHistoryDto.toEntity() = GiftHistoryEntity(
    id = id,
    contactId = contactId,
    giftName = giftName,
    giftCategory = giftCategory,
    occasionType = occasionType,
    year = year,
    approxCostInr = approxCostInr,
    receivedWell = receivedWell,
    notes = notes,
)

internal fun ActivityLogEntity.toBackupDto() = BackupActivityLogDto(
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

internal fun BackupActivityLogDto.toEntity() = ActivityLogEntity(
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

internal fun MessageFeedbackEntity.toBackupDto() = BackupMessageFeedbackDto(
    id = id,
    pendingMessageId = pendingMessageId,
    contactId = contactId,
    eventId = eventId,
    reasonKey = reasonKey,
    instruction = instruction,
    draftText = draftText,
    appliedToRegeneration = appliedToRegeneration,
    createdAtMs = createdAtMs,
)

internal fun BackupMessageFeedbackDto.toEntity() = MessageFeedbackEntity(
    id = id,
    pendingMessageId = pendingMessageId,
    contactId = contactId,
    eventId = eventId,
    reasonKey = reasonKey,
    instruction = instruction,
    draftText = draftText,
    appliedToRegeneration = appliedToRegeneration,
    createdAtMs = createdAtMs,
)

internal fun DispatchAttemptEntity.toBackupDto() = BackupDispatchAttemptDto(
    id = id,
    messageDraftId = messageDraftId,
    contactId = contactId,
    occasionId = occasionId,
    channel = channel,
    routeRank = routeRank,
    eligibilityDecision = eligibilityDecision,
    blockOrDeferReason = blockOrDeferReason,
    requestedAtMs = requestedAtMs,
    attemptedAtMs = attemptedAtMs,
    resolvedAtMs = resolvedAtMs,
    result = result,
    deliveryStatus = deliveryStatus,
    providerMessageId = providerMessageId,
    errorType = errorType,
    errorCode = errorCode,
    redactedErrorMessage = redactedErrorMessage,
    retryCount = retryCount,
    nextRetryAtMs = nextRetryAtMs,
    deadLetteredAtMs = deadLetteredAtMs,
    createdBy = createdBy,
    metadataJson = metadataJson,
)

internal fun BackupDispatchAttemptDto.toEntity() = DispatchAttemptEntity(
    id = id,
    messageDraftId = messageDraftId,
    contactId = contactId,
    occasionId = occasionId,
    channel = channel,
    routeRank = routeRank,
    eligibilityDecision = eligibilityDecision,
    blockOrDeferReason = blockOrDeferReason,
    requestedAtMs = requestedAtMs,
    attemptedAtMs = attemptedAtMs,
    resolvedAtMs = resolvedAtMs,
    result = result,
    deliveryStatus = deliveryStatus,
    providerMessageId = providerMessageId,
    errorType = errorType,
    errorCode = errorCode,
    redactedErrorMessage = redactedErrorMessage,
    retryCount = retryCount,
    nextRetryAtMs = nextRetryAtMs,
    deadLetteredAtMs = deadLetteredAtMs,
    createdBy = createdBy,
    metadataJson = metadataJson,
)
