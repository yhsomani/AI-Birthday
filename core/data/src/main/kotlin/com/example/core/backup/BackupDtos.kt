package com.example.core.backup

import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.db.entities.StyleProfileEntity
import com.example.core.prefs.SecurePrefs
import com.example.domain.event.toEventEntity
import com.example.domain.event.toOccasion
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import com.example.domain.service.BackupPreviewResult
import com.example.domain.service.BackupRecordCounts
import com.example.domain.service.BackupRestoreMode

internal const val CURRENT_BACKUP_VERSION = 3

internal data class BackupPayloadDto(
    val version: Int = CURRENT_BACKUP_VERSION,
    val timestampMs: Long = System.currentTimeMillis(),
    val manifest: BackupManifestDto? = null,
    val contacts: List<BackupContactDto> = emptyList(),
    val events: List<BackupEventDto> = emptyList(),
    val pendingMessages: List<BackupPendingMessageDto> = emptyList(),
    val sentMessages: List<BackupSentMessageDto> = emptyList(),
    val styleProfile: BackupStyleProfileDto? = null,
    val memoryNotes: List<BackupMemoryNoteDto> = emptyList(),
    val giftHistory: List<BackupGiftHistoryDto> = emptyList(),
    val activityLogs: List<BackupActivityLogDto> = emptyList(),
    val messageFeedback: List<BackupMessageFeedbackDto> = emptyList(),
    val dispatchAttempts: List<BackupDispatchAttemptDto> = emptyList(),
    val preferences: BackupPreferencesDto? = null,
) {
    fun toRecordSnapshot() = BackupRecordSnapshotDto(
        contacts = contacts,
        events = events,
        pendingMessages = pendingMessages,
        sentMessages = sentMessages,
        styleProfile = styleProfile,
        memoryNotes = memoryNotes,
        giftHistory = giftHistory,
        activityLogs = activityLogs,
        messageFeedback = messageFeedback,
        dispatchAttempts = dispatchAttempts,
        preferences = preferences,
    )

    fun toPreviewResult() = BackupPreviewResult(
        backupVersion = manifest?.backupVersion ?: version,
        appVersion = manifest?.appVersion ?: "unknown",
        exportedAtMs = manifest?.exportedAtMs ?: timestampMs,
        counts = manifest?.counts ?: toRecordSnapshot().counts(),
        restoreMode = BackupRestoreMode.REPLACE,
    )
}

internal data class BackupManifestDto(
    val backupVersion: Int,
    val appVersion: String,
    val exportedAtMs: Long,
    val counts: BackupRecordCounts,
    val dataChecksumSha256: String,
)

internal data class BackupRecordSnapshotDto(
    val contacts: List<BackupContactDto> = emptyList(),
    val events: List<BackupEventDto> = emptyList(),
    val pendingMessages: List<BackupPendingMessageDto> = emptyList(),
    val sentMessages: List<BackupSentMessageDto> = emptyList(),
    val styleProfile: BackupStyleProfileDto? = null,
    val memoryNotes: List<BackupMemoryNoteDto> = emptyList(),
    val giftHistory: List<BackupGiftHistoryDto> = emptyList(),
    val activityLogs: List<BackupActivityLogDto> = emptyList(),
    val messageFeedback: List<BackupMessageFeedbackDto> = emptyList(),
    val dispatchAttempts: List<BackupDispatchAttemptDto> = emptyList(),
    val preferences: BackupPreferencesDto? = null,
) {
    fun counts() = BackupRecordCounts(
        contacts = contacts.size,
        events = events.size,
        pendingMessages = pendingMessages.size,
        sentMessages = sentMessages.size,
        styleProfiles = if (styleProfile == null) 0 else 1,
        memoryNotes = memoryNotes.size,
        giftHistory = giftHistory.size,
        activityLogs = activityLogs.size,
        messageFeedback = messageFeedback.size,
        dispatchAttempts = dispatchAttempts.size,
        preferences = if (preferences == null) 0 else 1,
    )
}

internal data class BackupPreferencesDto(
    val globalAutomationMode: String,
    val themeMode: String,
    val blackoutDatesJson: String,
    val quietHoursStart: Int,
    val quietHoursEnd: Int,
    val channelBlackoutJson: String,
    val biometricLockEnabled: Boolean,
    val birthdayRemindersEnabled: Boolean,
    val aiWishGenerationEnabled: Boolean,
) {
    fun restoreTo(securePrefs: SecurePrefs) {
        securePrefs.setGlobalAutomationMode(globalAutomationMode)
        securePrefs.setThemeMode(themeMode)
        securePrefs.setBlackoutDates(blackoutDatesJson)
        securePrefs.setQuietHoursStart(quietHoursStart)
        securePrefs.setQuietHoursEnd(quietHoursEnd)
        securePrefs.setChannelBlackout(channelBlackoutJson)
        securePrefs.setBiometricLockEnabled(biometricLockEnabled)
        securePrefs.setBirthdayRemindersEnabled(birthdayRemindersEnabled)
        securePrefs.setAiWishGenerationEnabled(aiWishGenerationEnabled)
    }

    companion object {
        fun defaults() = BackupPreferencesDto(
            globalAutomationMode = ApprovalMode.SMART_APPROVE.raw,
            themeMode = "SYSTEM",
            blackoutDatesJson = "[]",
            quietHoursStart = 22,
            quietHoursEnd = 8,
            channelBlackoutJson = "[]",
            biometricLockEnabled = false,
            birthdayRemindersEnabled = true,
            aiWishGenerationEnabled = true,
        )

        fun from(securePrefs: SecurePrefs) = BackupPreferencesDto(
            globalAutomationMode = securePrefs.getGlobalAutomationMode(),
            themeMode = securePrefs.getThemeMode(),
            blackoutDatesJson = securePrefs.getBlackoutDates(),
            quietHoursStart = securePrefs.getQuietHoursStart(),
            quietHoursEnd = securePrefs.getQuietHoursEnd(),
            channelBlackoutJson = securePrefs.getChannelBlackout(),
            biometricLockEnabled = securePrefs.isBiometricLockEnabled(),
            birthdayRemindersEnabled = securePrefs.isBirthdayRemindersEnabled(),
            aiWishGenerationEnabled = securePrefs.isAiWishGenerationEnabled(),
        )
    }
}

internal data class BackupContactDto(
    val id: String,
    val googleContactId: String? = null,
    val name: String,
    val nickname: String? = null,
    val birthdayDay: Int? = null,
    val birthdayMonth: Int? = null,
    val birthdayYear: Int? = null,
    val anniversaryDay: Int? = null,
    val anniversaryMonth: Int? = null,
    val anniversaryYear: Int? = null,
    val workStartDay: Int? = null,
    val workStartMonth: Int? = null,
    val workStartYear: Int? = null,
    val primaryPhone: String? = null,
    val secondaryPhone: String? = null,
    val primaryEmail: String? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val address: String? = null,
    val profilePhotoUri: String? = null,
    val contactGroup: String? = null,
    val relationshipType: String = "UNKNOWN",
    val relationshipSubtype: String? = null,
    val preferredLanguage: String = "en",
    val preferredChannel: String = MessageChannel.SMS.raw,
    val formalityLevel: String = "CASUAL",
    val communicationStyle: String = "WARM",
    val classificationConfidence: Double = 0.0,
    val healthScore: Int = 50,
    val engagementScore: Int = 50,
    val interactionFrequencyPerMonth: Float = 0f,
    val lastInteractionDate: Long? = null,
    val lastWishedDate: Long? = null,
    val consecutiveYearsWished: Int = 0,
    val lastRevivalAttemptMs: Long = 0L,
    val automationMode: String = ApprovalMode.DEFAULT.raw,
    val giftBudgetInr: Int = 500,
    val annualBudgetInr: Int = 0,
    val skipAutoWish: Boolean = false,
    val customSendTimeHour: Int? = null,
    val customSendTimeMinute: Int? = null,
    val interestsJson: String = "[]",
    val hobbiesJson: String = "[]",
    val sharedHistoryJson: String = "[]",
    val favoritesJson: String = "{}",
    val relationsJson: String = "[]",
    val notesText: String = "",
    val typicalMoodWhenContacted: String = "NEUTRAL",
    val sensitiveTopicsJson: String = "[]",
    val currentLifePhaseJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
)

internal data class BackupEventDto(
    val id: String,
    val contactId: String,
    val type: String,
    val label: String? = null,
    val dayOfMonth: Int,
    val month: Int,
    val year: Int? = null,
    val nextOccurrenceMs: Long,
    val isActive: Boolean = true,
    val notifyDaysBefore: Int = 1,
    val source: String = "CONTACTS",
    val confidenceScore: Int = 100,
    val isVerified: Boolean = true,
)

internal data class BackupPendingMessageDto(
    val id: String,
    val contactId: String,
    val eventId: String,
    val shortVariant: String,
    val standardVariant: String,
    val longVariant: String,
    val formalVariant: String,
    val funnyVariant: String,
    val emotionalVariant: String,
    val selectedVariant: String = "standard",
    val selectedVariantText: String = "",
    val channel: String,
    val scheduledForMs: Long,
    val approvalMode: String,
    val status: String = MessageStatus.PENDING.raw,
    val aiModel: String = "flash",
    val generatedAtMs: Long = System.currentTimeMillis(),
    val editedByUser: Boolean = false,
    val userEditedText: String? = null,
    val qualityScore: Int = 0,
    val tone: String = "WARM",
    val length: String = "STANDARD",
    val includeEmoji: Boolean = true,
    val scheduledYear: Int = 0,
    val isUsingFallback: Boolean = false,
)

internal data class BackupSentMessageDto(
    val id: String,
    val contactId: String?,
    val eventType: String,
    val eventId: String? = null,
    val occasionType: String = eventType,
    val occasionLabel: String? = null,
    val eventYear: Int,
    val messageText: String,
    val channel: String,
    val sentAtMs: Long,
    val deliveryStatus: String,
    val aiGenerated: Boolean = true,
    val geminiModel: String = "flash",
    val variantUsed: String = "standard",
    val replyReceived: Boolean = false,
    val replyAtMs: Long? = null,
    val isContactDeleted: Boolean = false,
)

internal data class BackupStyleProfileDto(
    val id: Int = 1,
    val sampleMessagesJson: String = "[]",
    val usesEmoji: Boolean = true,
    val avgMessageLength: Int = 120,
    val commonPhrasesJson: String = "[]",
    val commonGreetingsJson: String = "[]",
    val formalityLevel: String = "CASUAL",
    val preferredLanguage: String = "en",
    val emojiSetJson: String = "[]",
    val avoidPhrasesJson: String = "[]",
    val toneDescriptors: String = "[]",
    val sampleCount: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis(),
)

internal data class BackupMemoryNoteDto(
    val id: String,
    val contactId: String,
    val noteText: String,
    val category: String = "GENERAL",
    val dateMs: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false,
)

internal data class BackupGiftHistoryDto(
    val id: String,
    val contactId: String,
    val giftName: String,
    val giftCategory: String,
    val occasionType: String,
    val year: Int,
    val approxCostInr: Int,
    val receivedWell: Boolean? = null,
    val notes: String = "",
)

internal data class BackupActivityLogDto(
    val id: String,
    val type: String,
    val title: String,
    val detail: String,
    val contactId: String? = null,
    val eventId: String? = null,
    val messageId: String? = null,
    val severity: String = ActivityLogSeverity.INFO.raw,
    val status: String = ActivityLogStatus.OPEN.raw,
    val actionRoute: String? = null,
    val metadataJson: String = "{}",
    val createdAtMs: Long = System.currentTimeMillis(),
)

internal data class BackupMessageFeedbackDto(
    val id: String,
    val pendingMessageId: String,
    val contactId: String,
    val eventId: String,
    val reasonKey: String,
    val instruction: String,
    val draftText: String,
    val appliedToRegeneration: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
)

internal data class BackupDispatchAttemptDto(
    val id: String,
    val messageDraftId: String,
    val contactId: String? = null,
    val occasionId: String? = null,
    val channel: String,
    val routeRank: Int = 0,
    val eligibilityDecision: String,
    val blockOrDeferReason: String? = null,
    val requestedAtMs: Long,
    val attemptedAtMs: Long? = null,
    val resolvedAtMs: Long? = null,
    val result: String,
    val deliveryStatus: String,
    val providerMessageId: String? = null,
    val errorType: String? = null,
    val errorCode: String? = null,
    val redactedErrorMessage: String? = null,
    val retryCount: Int = 0,
    val nextRetryAtMs: Long? = null,
    val deadLetteredAtMs: Long? = null,
    val createdBy: String,
    val metadataJson: String = "{}",
)

internal fun ContactEntity.toBackupDto() = BackupContactDto(
    id = id,
    googleContactId = googleContactId,
    name = name,
    nickname = nickname,
    birthdayDay = birthdayDay,
    birthdayMonth = birthdayMonth,
    birthdayYear = birthdayYear,
    anniversaryDay = anniversaryDay,
    anniversaryMonth = anniversaryMonth,
    anniversaryYear = anniversaryYear,
    workStartDay = workStartDay,
    workStartMonth = workStartMonth,
    workStartYear = workStartYear,
    primaryPhone = primaryPhone,
    secondaryPhone = secondaryPhone,
    primaryEmail = primaryEmail,
    company = company,
    jobTitle = jobTitle,
    address = address,
    profilePhotoUri = profilePhotoUri,
    contactGroup = contactGroup,
    relationshipType = relationshipType,
    relationshipSubtype = relationshipSubtype,
    preferredLanguage = preferredLanguage,
    preferredChannel = preferredChannel,
    formalityLevel = formalityLevel,
    communicationStyle = communicationStyle,
    classificationConfidence = classificationConfidence,
    healthScore = healthScore,
    engagementScore = engagementScore,
    interactionFrequencyPerMonth = interactionFrequencyPerMonth,
    lastInteractionDate = lastInteractionDate,
    lastWishedDate = lastWishedDate,
    consecutiveYearsWished = consecutiveYearsWished,
    lastRevivalAttemptMs = lastRevivalAttemptMs,
    automationMode = automationMode,
    giftBudgetInr = giftBudgetInr,
    annualBudgetInr = annualBudgetInr,
    skipAutoWish = skipAutoWish,
    customSendTimeHour = customSendTimeHour,
    customSendTimeMinute = customSendTimeMinute,
    interestsJson = interestsJson,
    hobbiesJson = hobbiesJson,
    sharedHistoryJson = sharedHistoryJson,
    favoritesJson = favoritesJson,
    relationsJson = relationsJson,
    notesText = notesText,
    typicalMoodWhenContacted = typicalMoodWhenContacted,
    sensitiveTopicsJson = sensitiveTopicsJson,
    currentLifePhaseJson = currentLifePhaseJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isArchived = isArchived,
    isDeleted = isDeleted,
)

internal fun BackupContactDto.toEntity() = ContactEntity(
    id = id,
    googleContactId = googleContactId,
    name = name,
    nickname = nickname,
    birthdayDay = birthdayDay,
    birthdayMonth = birthdayMonth,
    birthdayYear = birthdayYear,
    anniversaryDay = anniversaryDay,
    anniversaryMonth = anniversaryMonth,
    anniversaryYear = anniversaryYear,
    workStartDay = workStartDay,
    workStartMonth = workStartMonth,
    workStartYear = workStartYear,
    primaryPhone = primaryPhone,
    secondaryPhone = secondaryPhone,
    primaryEmail = primaryEmail,
    company = company,
    jobTitle = jobTitle,
    address = address,
    profilePhotoUri = profilePhotoUri,
    contactGroup = contactGroup,
    relationshipType = relationshipType,
    relationshipSubtype = relationshipSubtype,
    preferredLanguage = preferredLanguage,
    preferredChannel = preferredChannel,
    formalityLevel = formalityLevel,
    communicationStyle = communicationStyle,
    classificationConfidence = classificationConfidence,
    healthScore = healthScore,
    engagementScore = engagementScore,
    interactionFrequencyPerMonth = interactionFrequencyPerMonth,
    lastInteractionDate = lastInteractionDate,
    lastWishedDate = lastWishedDate,
    consecutiveYearsWished = consecutiveYearsWished,
    lastRevivalAttemptMs = lastRevivalAttemptMs,
    automationMode = automationMode,
    giftBudgetInr = giftBudgetInr,
    annualBudgetInr = annualBudgetInr,
    skipAutoWish = skipAutoWish,
    customSendTimeHour = customSendTimeHour,
    customSendTimeMinute = customSendTimeMinute,
    interestsJson = interestsJson,
    hobbiesJson = hobbiesJson,
    sharedHistoryJson = sharedHistoryJson,
    favoritesJson = favoritesJson,
    relationsJson = relationsJson,
    notesText = notesText,
    typicalMoodWhenContacted = typicalMoodWhenContacted,
    sensitiveTopicsJson = sensitiveTopicsJson,
    currentLifePhaseJson = currentLifePhaseJson,
    createdAt = createdAt,
    updatedAt = updatedAt,
    isArchived = isArchived,
    isDeleted = isDeleted,
)

internal fun Occasion.toBackupDto() = BackupEventDto(
    id = id.value,
    contactId = contactId.value,
    type = type.raw,
    label = label,
    dayOfMonth = date.dayOfMonth,
    month = date.month,
    year = date.year,
    nextOccurrenceMs = nextOccurrenceMs,
    isActive = isActive,
    notifyDaysBefore = notifyDaysBefore,
    source = source,
    confidenceScore = confidenceScore,
    isVerified = isVerified,
)

internal fun BackupEventDto.toOccasion() = Occasion(
    id = OccasionId(id),
    contactId = ContactId(contactId),
    type = OccasionType.fromRaw(type),
    label = label,
    date = OccasionDate(
        dayOfMonth = dayOfMonth,
        month = month,
        year = year,
    ),
    nextOccurrenceMs = nextOccurrenceMs,
    isActive = isActive,
    notifyDaysBefore = notifyDaysBefore,
    source = source,
    confidenceScore = confidenceScore,
    isVerified = isVerified,
)

// Preserve raw legacy type strings in backup import/export while Room still stores events.
internal fun EventEntity.toBackupDto() = toOccasion().toBackupDto().copy(type = type)

internal fun BackupEventDto.toEntity() = toOccasion().toEventEntity().copy(type = type)

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
