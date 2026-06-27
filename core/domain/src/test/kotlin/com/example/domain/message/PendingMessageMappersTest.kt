package com.example.domain.message

import com.example.core.db.entities.PendingMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingMessageMappersTest {
    @Test
    fun `pending message maps edited text to dispatch draft`() {
        val draft = pendingMessage().copy(
            editedByUser = true,
            userEditedText = "Edited final wish",
            selectedVariantText = "Original selected wish",
        ).toMessageDispatchDraft()

        assertEquals(MessageDraftId("pm_1"), draft.id)
        assertEquals(OccasionId("event_1"), draft.occasionReference)
        assertEquals(MessageChannel.SMS, draft.preferredChannel)
        assertEquals("Edited final wish", draft.messageText)
    }

    @Test
    fun `pending message dispatch draft falls back to selected variant when selected text is blank`() {
        val draft = pendingMessage().copy(
            selectedVariant = "funny",
            selectedVariantText = "",
            funnyVariant = "Funny birthday wish",
            channel = " email ",
        ).toMessageDispatchDraft()

        assertEquals(MessageChannel.EMAIL, draft.preferredChannel)
        assertEquals("Funny birthday wish", draft.messageText)
    }

    @Test
    fun `pending message maps to dispatch state with typed draft and dispatch text`() {
        val state = pendingMessage().copy(
            selectedVariantText = "Dispatch this wish",
        ).toMessageDispatchState()

        assertEquals(MessageDraftId("pm_1"), state.id)
        assertEquals(OccasionId("event_1"), state.occasionId)
        assertEquals(MessageChannel.SMS, state.channel)
        assertEquals("Dispatch this wish", state.dispatchDraft.messageText)
    }

    private fun pendingMessage(): PendingMessageEntity {
        return PendingMessageEntity(
            id = "pm_1",
            contactId = "contact_1",
            eventId = "event_1",
            shortVariant = "Short wish",
            standardVariant = "Standard wish",
            longVariant = "Long wish",
            formalVariant = "Formal wish",
            funnyVariant = "Funny wish",
            emotionalVariant = "Emotional wish",
            selectedVariant = "standard",
            selectedVariantText = "Standard wish",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = 1_800_000_000_000,
            approvalMode = "ALWAYS_ASK",
        )
    }
}
