package com.example.data.repository

import com.example.core.db.dao.MessageFeedbackDao
import com.example.core.db.entities.MessageFeedbackEntity
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.MessageFeedbackId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.message.MessageFeedbackRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class MessageFeedbackRepositoryImplTest {
    private val messageFeedbackDao: MessageFeedbackDao = mockk(relaxed = true)
    private val repository = MessageFeedbackRepositoryImpl(messageFeedbackDao)

    @Test
    fun getRecent_mapsRoomRowsToPureRecords() = runTest {
        every { messageFeedbackDao.getRecent(5) } returns MutableStateFlow(
            listOf(
                MessageFeedbackEntity(
                    id = "feedback_1",
                    pendingMessageId = "draft_1",
                    contactId = "contact_1",
                    eventId = "event_1",
                    reasonKey = "too_generic",
                    instruction = "Make it more personal.",
                    draftText = "Happy birthday",
                    appliedToRegeneration = true,
                    createdAtMs = 1_700_000_000_000L,
                )
            )
        )

        val records = repository.getRecent(5).first()

        assertEquals(1, records.size)
        assertEquals(MessageFeedbackId("feedback_1"), records.single().id)
        assertEquals(MessageDraftId("draft_1"), records.single().pendingMessageId)
        assertEquals(ContactId("contact_1"), records.single().contactId)
        assertEquals(OccasionId("event_1"), records.single().occasionId)
        assertEquals("too_generic", records.single().reasonKey)
        assertEquals(true, records.single().appliedToRegeneration)
    }

    @Test
    fun getLatestForPendingMessage_usesPureDraftIdAndMapsRecord() = runTest {
        coEvery { messageFeedbackDao.getLatestForPendingMessage("draft_1") } returns MessageFeedbackEntity(
            id = "feedback_1",
            pendingMessageId = "draft_1",
            contactId = "contact_1",
            eventId = "event_1",
            reasonKey = "too_long",
            instruction = "Make it shorter.",
            draftText = "A long draft",
            createdAtMs = 1_700_000_000_000L,
        )

        val record = repository.getLatestForPendingMessage(MessageDraftId("draft_1"))

        assertEquals(MessageFeedbackId("feedback_1"), record?.id)
        assertEquals("too_long", record?.reasonKey)
        coVerify { messageFeedbackDao.getLatestForPendingMessage("draft_1") }
    }

    @Test
    fun record_mapsPureRecordToRoomEntity() = runTest {
        val record = MessageFeedbackRecord(
            id = MessageFeedbackId("feedback_1"),
            pendingMessageId = MessageDraftId("draft_1"),
            contactId = ContactId("contact_1"),
            occasionId = OccasionId("event_1"),
            reasonKey = "wrong_language",
            instruction = "Use Hindi.",
            draftText = "Happy birthday",
            appliedToRegeneration = false,
            createdAtMs = 1_700_000_000_000L,
        )

        repository.record(record)

        coVerify {
            messageFeedbackDao.insert(
                match {
                    it.id == "feedback_1" &&
                        it.pendingMessageId == "draft_1" &&
                        it.contactId == "contact_1" &&
                        it.eventId == "event_1" &&
                        it.reasonKey == "wrong_language" &&
                        it.instruction == "Use Hindi." &&
                        it.draftText == "Happy birthday" &&
                        !it.appliedToRegeneration &&
                        it.createdAtMs == 1_700_000_000_000L
                }
            )
        }
    }

    @Test
    fun markApplied_usesPureFeedbackId() = runTest {
        repository.markApplied(MessageFeedbackId("feedback_1"))

        coVerify { messageFeedbackDao.markApplied("feedback_1") }
    }
}
