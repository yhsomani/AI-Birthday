package com.example.domain.dispatch

import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import com.example.domain.model.message.MessageDraft
import org.junit.Assert.assertEquals
import org.junit.Test

class DispatchAttemptFactoryTest {
    @Test
    fun `message draft creates dispatch attempt without room entity input`() {
        val draft = MessageDraft(
            id = MessageDraftId("msg_1"),
            contactId = ContactId("contact_1"),
            occasionId = OccasionId("event_1"),
            scheduledForMs = 1_700_000_000_000,
            approvalMode = ApprovalMode.ALWAYS_ASK,
            status = MessageStatus.APPROVED,
            channel = MessageChannel.WHATSAPP,
            scheduledYear = 2026,
            qualityScore = 88,
            isUsingFallback = false,
        )

        val attempt = draft.newDispatchAttempt(
            eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
            result = DispatchAttemptResult.QUEUED,
            createdBy = DispatchAttemptCreator.USER,
            requestedAtMs = 1_700_000_000_100,
        )

        assertEquals(MessageDraftId("msg_1"), attempt.messageDraftId)
        assertEquals(ContactId("contact_1"), attempt.contactId)
        assertEquals(OccasionId("event_1"), attempt.occasionId)
        assertEquals(MessageChannel.WHATSAPP, attempt.channel)
        assertEquals(DispatchEligibilityRecord.SEND_NOW, attempt.eligibilityDecision)
        assertEquals(DispatchAttemptResult.QUEUED, attempt.result)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY, attempt.deliveryStatus)
        assertEquals(DispatchAttemptCreator.USER, attempt.createdBy)
    }

    @Test
    fun `unknown draft channel falls back to sms for legacy dispatch attempts`() {
        val draft = MessageDraft(
            id = MessageDraftId("msg_1"),
            contactId = ContactId("contact_1"),
            occasionId = OccasionId("event_1"),
            scheduledForMs = 1_700_000_000_000,
            approvalMode = ApprovalMode.ALWAYS_ASK,
            status = MessageStatus.APPROVED,
            channel = MessageChannel.UNKNOWN,
            scheduledYear = 2026,
            qualityScore = 88,
            isUsingFallback = false,
        )

        val attempt = draft.newDispatchAttempt(
            eligibilityDecision = DispatchEligibilityRecord.SEND_NOW,
            result = DispatchAttemptResult.QUEUED,
            createdBy = DispatchAttemptCreator.USER,
        )

        assertEquals(MessageChannel.SMS, attempt.channel)
    }
}
