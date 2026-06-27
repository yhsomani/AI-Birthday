package com.example.domain.dispatch

import com.example.core.db.entities.DispatchAttemptEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.DispatchAttempt
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DispatchAttemptMappersTest {
    @Test
    fun `entity maps to pure dispatch attempt`() {
        val entity = DispatchAttemptEntity(
            id = "attempt_1",
            messageDraftId = "draft_1",
            contactId = "contact_1",
            occasionId = "event_1",
            channel = "sms",
            routeRank = 2,
            eligibilityDecision = "send_now",
            blockOrDeferReason = null,
            requestedAtMs = 1_700_000_000_000,
            attemptedAtMs = 1_700_000_000_100,
            resolvedAtMs = 1_700_000_000_200,
            result = "delivered",
            deliveryStatus = "delivered",
            providerMessageId = "provider_1",
            errorType = null,
            errorCode = null,
            redactedErrorMessage = null,
            retryCount = 0,
            nextRetryAtMs = null,
            deadLetteredAtMs = null,
            createdBy = "worker",
            metadataJson = """{"route":"primary"}""",
        )

        val model = entity.toDispatchAttempt()

        assertEquals(DispatchAttemptId("attempt_1"), model.id)
        assertEquals(MessageDraftId("draft_1"), model.messageDraftId)
        assertEquals(ContactId("contact_1"), model.contactId)
        assertEquals(OccasionId("event_1"), model.occasionId)
        assertEquals(MessageChannel.SMS, model.channel)
        assertEquals(DispatchEligibilityRecord.SEND_NOW, model.eligibilityDecision)
        assertEquals(DispatchAttemptResult.DELIVERED, model.result)
        assertEquals(MessageDeliveryStatus.DELIVERED, model.deliveryStatus)
        assertEquals(DispatchAttemptCreator.WORKER, model.createdBy)
    }

    @Test
    fun `unknown raw entity values map to unknown enums`() {
        val model = DispatchAttemptEntity(
            id = "attempt_1",
            messageDraftId = "draft_1",
            channel = "telegram",
            eligibilityDecision = "waiting",
            requestedAtMs = 1_700_000_000_000,
            result = "bounced",
            deliveryStatus = "accepted",
            createdBy = "scheduler",
        ).toDispatchAttempt()

        assertEquals(MessageChannel.UNKNOWN, model.channel)
        assertEquals(DispatchEligibilityRecord.UNKNOWN, model.eligibilityDecision)
        assertEquals(DispatchAttemptResult.UNKNOWN, model.result)
        assertEquals(MessageDeliveryStatus.UNKNOWN, model.deliveryStatus)
        assertEquals(DispatchAttemptCreator.UNKNOWN, model.createdBy)
        assertNull(model.contactId)
        assertNull(model.occasionId)
    }

    @Test
    fun `pure dispatch attempt maps to entity raw values`() {
        val model = DispatchAttempt(
            id = DispatchAttemptId("attempt_1"),
            messageDraftId = MessageDraftId("draft_1"),
            contactId = ContactId("contact_1"),
            occasionId = OccasionId("event_1"),
            channel = MessageChannel.WHATSAPP,
            routeRank = 1,
            eligibilityDecision = DispatchEligibilityRecord.NEEDS_APPROVAL,
            blockOrDeferReason = "mode_requires_review",
            requestedAtMs = 1_700_000_000_000,
            attemptedAtMs = null,
            resolvedAtMs = null,
            result = DispatchAttemptResult.NEEDS_APPROVAL,
            deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY,
            providerMessageId = null,
            errorType = null,
            errorCode = null,
            redactedErrorMessage = null,
            retryCount = 0,
            nextRetryAtMs = null,
            deadLetteredAtMs = null,
            createdBy = DispatchAttemptCreator.USER,
            metadataJson = "{}",
        )

        val entity = model.toEntity()

        assertEquals("attempt_1", entity.id)
        assertEquals("draft_1", entity.messageDraftId)
        assertEquals("contact_1", entity.contactId)
        assertEquals("event_1", entity.occasionId)
        assertEquals(MessageChannel.WHATSAPP.raw, entity.channel)
        assertEquals(DispatchEligibilityRecord.NEEDS_APPROVAL.raw, entity.eligibilityDecision)
        assertEquals(DispatchAttemptResult.NEEDS_APPROVAL.raw, entity.result)
        assertEquals(DispatchAttemptCreator.USER.raw, entity.createdBy)
    }
}
