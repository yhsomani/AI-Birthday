package com.example.core.db.entities

import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingMessageEntityTest {
    @Test
    fun `default status uses MessageStatus raw value`() {
        val pending = PendingMessageEntity(
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
            scheduledForMs = 1_700_000_000_000L,
            approvalMode = "SMART_APPROVE",
        )

        assertEquals(MessageStatus.PENDING.raw, pending.status)
    }
}
