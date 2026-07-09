package com.example.core.backup

import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.MessageStatus
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
