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
import com.example.domain.model.ActivityLogSeverity
import com.example.domain.model.ActivityLogStatus
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.model.occasion.Occasion
import com.example.domain.model.occasion.OccasionDate
import com.example.domain.model.occasion.OccasionType
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupDtosTest {

    @Test
    fun backupDtoMappers_preserveAllRestorableRoomFields() {
        val contact = ContactEntity(
            id = "contact_1",
            googleContactId = "google_1",
            name = "Alice",
            nickname = "Al",
            birthdayDay = 1,
            birthdayMonth = 2,
            birthdayYear = 1990,
            anniversaryDay = 3,
            anniversaryMonth = 4,
            anniversaryYear = 2018,
            workStartDay = 5,
            workStartMonth = 6,
            workStartYear = 2020,
            primaryPhone = "+911234567890",
            secondaryPhone = "+919876543210",
            primaryEmail = "alice@example.com",
            company = "Example Co",
            jobTitle = "Designer",
            address = "Mumbai",
            profilePhotoUri = "content://photo",
            contactGroup = "Friends",
            relationshipType = "FRIEND",
            relationshipSubtype = "college",
            preferredLanguage = "hi",
            preferredChannel = MessageChannel.WHATSAPP.raw,
            formalityLevel = "CASUAL",
            communicationStyle = "FUNNY",
            classificationConfidence = 0.87,
            healthScore = 72,
            engagementScore = 65,
            interactionFrequencyPerMonth = 2.5f,
            lastInteractionDate = 1_700_000_000_000,
            lastWishedDate = 1_700_000_000_001,
            consecutiveYearsWished = 3,
            lastRevivalAttemptMs = 1_700_000_000_002,
            automationMode = ApprovalMode.SMART_APPROVE.raw,
            giftBudgetInr = 1500,
            annualBudgetInr = 5000,
            skipAutoWish = true,
            customSendTimeHour = 9,
            customSendTimeMinute = 30,
            interestsJson = """["travel"]""",
            hobbiesJson = """["music"]""",
            sharedHistoryJson = """["college"]""",
            favoritesJson = """{"food":"biryani"}""",
            relationsJson = """[{"person":"Bob"}]""",
            notesText = "Close friend",
            typicalMoodWhenContacted = "HAPPY",
            sensitiveTopicsJson = """["work"]""",
            currentLifePhaseJson = """{"phase":"new_job"}""",
            createdAt = 1_600_000_000_000,
            updatedAt = 1_700_000_000_003,
            isArchived = true,
            isDeleted = false,
        )
        val event = EventEntity(
            id = "event_1",
            contactId = "contact_1",
            type = "BIRTHDAY",
            label = "Birthday",
            dayOfMonth = 1,
            month = 2,
            year = 1990,
            nextOccurrenceMs = 1_800_000_000_000,
            isActive = true,
            notifyDaysBefore = 3,
            source = "MANUAL",
            confidenceScore = 90,
            isVerified = false,
        )
        val pendingMessage = PendingMessageEntity(
            id = "pending_1",
            contactId = "contact_1",
            eventId = "event_1",
            shortVariant = "Short",
            standardVariant = "Standard",
            longVariant = "Long",
            formalVariant = "Formal",
            funnyVariant = "Funny",
            emotionalVariant = "Emotional",
            selectedVariant = "funny",
            selectedVariantText = "Funny",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = 1_800_000_000_001,
            approvalMode = ApprovalMode.ALWAYS_ASK.raw,
            status = MessageStatus.APPROVED.raw,
            aiModel = "flash",
            generatedAtMs = 1_700_000_000_004,
            editedByUser = true,
            userEditedText = "Edited",
            qualityScore = 88,
            tone = "WARM",
            length = "LONG",
            includeEmoji = false,
            scheduledYear = 2026,
            isUsingFallback = true,
        )
        val sentMessage = SentMessageEntity(
            id = "sent_1",
            contactId = "contact_1",
            eventType = "BIRTHDAY",
            eventId = "event_1",
            occasionType = "BIRTHDAY",
            occasionLabel = "Birthday",
            eventYear = 2026,
            messageText = "Happy birthday",
            channel = MessageChannel.SMS.raw,
            sentAtMs = 1_800_000_000_002,
            deliveryStatus = MessageDeliveryStatus.SENT.raw,
            aiGenerated = false,
            geminiModel = "flash",
            variantUsed = "standard",
            replyReceived = true,
            replyAtMs = 1_800_000_000_003,
            isContactDeleted = true,
        )
        val styleProfile = StyleProfileEntity(
            id = 1,
            sampleMessagesJson = """["hey"]""",
            usesEmoji = false,
            avgMessageLength = 42,
            commonPhrasesJson = """["yaar"]""",
            commonGreetingsJson = """["hi"]""",
            formalityLevel = "CASUAL",
            preferredLanguage = "hi",
            emojiSetJson = """["🙂"]""",
            avoidPhrasesJson = """["dear"]""",
            toneDescriptors = """["warm"]""",
            sampleCount = 5,
            updatedAtMs = 1_700_000_000_005,
        )
        val memoryNote = MemoryNoteEntity(
            id = "note_1",
            contactId = "contact_1",
            noteText = "Likes coffee",
            category = "PREFERENCE",
            dateMs = 1_700_000_000_006,
            isPinned = true,
        )
        val giftHistory = GiftHistoryEntity(
            id = "gift_1",
            contactId = "contact_1",
            giftName = "Book",
            giftCategory = "Books",
            occasionType = "BIRTHDAY",
            year = 2025,
            approxCostInr = 900,
            receivedWell = true,
            notes = "Loved it",
        )
        val activityLog = ActivityLogEntity(
            id = "log_1",
            type = "MESSAGE",
            title = "Sent",
            detail = "Delivered",
            contactId = "contact_1",
            eventId = "event_1",
            messageId = "pending_1",
            severity = ActivityLogSeverity.WARNING.raw,
            status = ActivityLogStatus.RESOLVED.raw,
            actionRoute = "messages",
            metadataJson = """{"route":"sms"}""",
            createdAtMs = 1_700_000_000_007,
        )
        val feedback = MessageFeedbackEntity(
            id = "feedback_1",
            pendingMessageId = "pending_1",
            contactId = "contact_1",
            eventId = "event_1",
            reasonKey = "tone",
            instruction = "Warmer",
            draftText = "Original",
            appliedToRegeneration = true,
            createdAtMs = 1_700_000_000_008,
        )
        val dispatchAttempt = DispatchAttemptEntity(
            id = "attempt_1",
            messageDraftId = "pending_1",
            contactId = "contact_1",
            occasionId = "event_1",
            channel = MessageChannel.SMS.raw,
            routeRank = 2,
            eligibilityDecision = DispatchEligibilityRecord.SEND_NOW.raw,
            blockOrDeferReason = "none",
            requestedAtMs = 1_700_000_000_009,
            attemptedAtMs = 1_700_000_000_010,
            resolvedAtMs = 1_700_000_000_011,
            result = DispatchAttemptResult.SENT.raw,
            deliveryStatus = MessageDeliveryStatus.SENT.raw,
            providerMessageId = "provider_1",
            errorType = "none",
            errorCode = "0",
            redactedErrorMessage = "redacted",
            retryCount = 1,
            nextRetryAtMs = 1_700_000_000_012,
            deadLetteredAtMs = 1_700_000_000_013,
            createdBy = DispatchAttemptCreator.USER.raw,
            metadataJson = """{"attempt":1}""",
        )

        assertEquals(contact, contact.toBackupDto().toEntity())
        assertEquals(event, event.toBackupDto().toEntity())
        assertEquals(pendingMessage, pendingMessage.toBackupDto().toEntity())
        assertEquals(sentMessage, sentMessage.toBackupDto().toEntity())
        assertEquals(styleProfile, styleProfile.toBackupDto().toEntity())
        assertEquals(memoryNote, memoryNote.toBackupDto().toEntity())
        assertEquals(giftHistory, giftHistory.toBackupDto().toEntity())
        assertEquals(activityLog, activityLog.toBackupDto().toEntity())
        assertEquals(feedback, feedback.toBackupDto().toEntity())
        assertEquals(dispatchAttempt, dispatchAttempt.toBackupDto().toEntity())
        val legacyTypedEvent = event.copy(type = "LEGACY_CUSTOM")
        assertEquals(legacyTypedEvent, legacyTypedEvent.toBackupDto().toEntity())
    }

    @Test
    fun backupEventDtoMappers_roundTripPureOccasionFields() {
        val occasion = Occasion(
            id = OccasionId("holiday_1"),
            contactId = ContactId("contact_1"),
            type = OccasionType.HOLIDAY,
            label = "Diwali",
            date = OccasionDate(
                dayOfMonth = 12,
                month = 11,
                year = 2026,
            ),
            nextOccurrenceMs = 1_800_000_000_000,
            isActive = true,
            notifyDaysBefore = 0,
            source = "AI_INFERRED",
            confidenceScore = 82,
            isVerified = false,
        )

        assertEquals(occasion, occasion.toBackupDto().toOccasion())
    }

    @Test
    fun backupRecordSnapshot_countsEveryDtoCategory() {
        val snapshot = BackupRecordSnapshotDto(
            contacts = listOf(BackupContactDto(id = "contact_1", name = "Alice")),
            events = listOf(
                BackupEventDto(
                    id = "event_1",
                    contactId = "contact_1",
                    type = "BIRTHDAY",
                    dayOfMonth = 1,
                    month = 2,
                    nextOccurrenceMs = 1_800_000_000_000,
                )
            ),
            pendingMessages = listOf(minimalPendingMessageDto()),
            sentMessages = listOf(minimalSentMessageDto()),
            styleProfile = BackupStyleProfileDto(id = 1),
            memoryNotes = listOf(BackupMemoryNoteDto(id = "note_1", contactId = "contact_1", noteText = "Note")),
            giftHistory = listOf(minimalGiftHistoryDto()),
            activityLogs = listOf(
                BackupActivityLogDto(
                    id = "log_1",
                    type = "MESSAGE",
                    title = "Title",
                    detail = "Detail",
                )
            ),
            messageFeedback = listOf(minimalMessageFeedbackDto()),
            dispatchAttempts = listOf(minimalDispatchAttemptDto()),
            preferences = BackupPreferencesDto.defaults(),
        )

        val counts = snapshot.counts()

        assertEquals(1, counts.contacts)
        assertEquals(1, counts.events)
        assertEquals(1, counts.pendingMessages)
        assertEquals(1, counts.sentMessages)
        assertEquals(1, counts.styleProfiles)
        assertEquals(1, counts.memoryNotes)
        assertEquals(1, counts.giftHistory)
        assertEquals(1, counts.activityLogs)
        assertEquals(1, counts.messageFeedback)
        assertEquals(1, counts.dispatchAttempts)
        assertEquals(1, counts.preferences)
        assertEquals(11, counts.totalRecords)
    }

    private fun minimalPendingMessageDto() = BackupPendingMessageDto(
        id = "pending_1",
        contactId = "contact_1",
        eventId = "event_1",
        shortVariant = "Short",
        standardVariant = "Standard",
        longVariant = "Long",
        formalVariant = "Formal",
        funnyVariant = "Funny",
        emotionalVariant = "Emotional",
        channel = MessageChannel.SMS.raw,
        scheduledForMs = 1_800_000_000_000,
        approvalMode = ApprovalMode.SMART_APPROVE.raw,
    )

    private fun minimalSentMessageDto() = BackupSentMessageDto(
        id = "sent_1",
        contactId = "contact_1",
        eventType = "BIRTHDAY",
        eventYear = 2026,
        messageText = "Sent",
        channel = MessageChannel.SMS.raw,
        sentAtMs = 1_800_000_000_000,
        deliveryStatus = MessageDeliveryStatus.SENT.raw,
    )

    private fun minimalGiftHistoryDto() = BackupGiftHistoryDto(
        id = "gift_1",
        contactId = "contact_1",
        giftName = "Book",
        giftCategory = "Books",
        occasionType = "BIRTHDAY",
        year = 2026,
        approxCostInr = 500,
    )

    private fun minimalMessageFeedbackDto() = BackupMessageFeedbackDto(
        id = "feedback_1",
        pendingMessageId = "pending_1",
        contactId = "contact_1",
        eventId = "event_1",
        reasonKey = "tone",
        instruction = "Warmer",
        draftText = "Draft",
    )

    private fun minimalDispatchAttemptDto() = BackupDispatchAttemptDto(
        id = "attempt_1",
        messageDraftId = "pending_1",
        channel = MessageChannel.SMS.raw,
        eligibilityDecision = DispatchEligibilityRecord.SEND_NOW.raw,
        requestedAtMs = 1_800_000_000_000,
        result = DispatchAttemptResult.QUEUED.raw,
        deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
        createdBy = DispatchAttemptCreator.WORKER.raw,
    )
}
