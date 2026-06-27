package com.example.domain.dispatch

import com.example.domain.model.MessageChannel
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.DispatchAttemptId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.dispatch.MessageDispatchDraft
import com.example.domain.model.dispatch.MessageDispatchRecipient
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageDispatchRequestMappersTest {
    @Test
    fun `pure dispatch inputs build dispatcher request`() {
        val request = buildMessageDispatchRequest(
            message = MessageDispatchDraft(
                id = MessageDraftId("msg_1"),
                occasionReference = OccasionId("event_1"),
                preferredChannel = MessageChannel.EMAIL,
                messageText = "Happy birthday",
            ),
            recipient = MessageDispatchRecipient(
                id = ContactId("contact_1"),
                displayName = "Asha",
                primaryPhone = "+15551234567",
                primaryEmail = "asha@example.com",
            ),
            dispatchAttemptId = "attempt_1",
        )

        assertEquals(MessageDraftId("msg_1"), request.messageId)
        assertEquals(ContactId("contact_1"), request.contactId)
        assertEquals(OccasionId("event_1"), request.occasionReference)
        assertEquals(MessageChannel.EMAIL, request.preferredChannel)
        assertEquals("Happy birthday", request.messageText)
        assertEquals("Asha", request.contactDisplayName)
        assertEquals("+15551234567", request.primaryPhone)
        assertEquals("asha@example.com", request.primaryEmail)
        assertEquals(DispatchAttemptId("attempt_1"), request.dispatchAttemptId)
    }
}
