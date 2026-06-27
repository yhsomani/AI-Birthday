package com.example.data.repository

import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.MessageStatus
import com.example.domain.model.common.ContactId
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.common.OccasionId
import com.example.domain.model.common.SentMessageId
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageRepositoryImplTest {
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val repository = MessageRepositoryImpl(pendingMessageDao, sentMessageDao)

    @Test
    fun getPendingListItems_mapsRoomMessagesToPureListItems() = runTest {
        every { pendingMessageDao.getAll() } returns flowOf(
            listOf(
                pendingMessage(
                    channel = " email ",
                    approvalMode = "smart_approve",
                    status = "approved",
                    isUsingFallback = true,
                ).copy(
                    editedByUser = true,
                    userEditedText = "Edited draft",
                ),
            ),
        )

        val items = repository.getPendingListItems().first()

        assertEquals(MessageDraftId("pm_1"), items.single().id)
        assertEquals(ContactId("contact_1"), items.single().contactId)
        assertEquals(OccasionId("event_1"), items.single().occasionId)
        assertEquals("Standard draft", items.single().selectedVariantText)
        assertEquals("Standard draft", items.single().standardVariant)
        assertEquals(MessageChannel.EMAIL, items.single().channel)
        assertEquals(ApprovalMode.SMART_APPROVE, items.single().approvalMode)
        assertEquals(MessageStatus.APPROVED, items.single().status)
        assertEquals(true, items.single().editedByUser)
        assertEquals("Edited draft", items.single().userEditedText)
    }

    @Test
    fun getWishPreviewDraftById_mapsRoomMessageToPurePreviewDraft() = runTest {
        coEvery { pendingMessageDao.getById("pm_1") } returns pendingMessage(
            channel = " email ",
            approvalMode = "smart_approve",
            status = "pending",
            isUsingFallback = true,
        )

        val draft = repository.getWishPreviewDraftById("pm_1")

        assertEquals(MessageDraftId("pm_1"), draft?.id)
        assertEquals(ContactId("contact_1"), draft?.contactId)
        assertEquals(OccasionId("event_1"), draft?.occasionId)
        assertEquals("Funny draft", draft?.variantText("funny"))
        assertEquals("Standard draft", draft?.selectedVariantText)
        assertEquals(MessageChannel.EMAIL, draft?.channel)
        assertEquals(ApprovalMode.SMART_APPROVE, draft?.approvalMode)
        assertEquals(MessageStatus.PENDING, draft?.status)
        assertEquals(true, draft?.isUsingFallback)
    }

    @Test
    fun getWishPreviewDraftByEventId_mapsRoomMessageToPurePreviewDraft() = runTest {
        coEvery { pendingMessageDao.getByEventId("event_1") } returns pendingMessage()

        val draft = repository.getWishPreviewDraftByEventId("event_1")

        assertEquals(MessageDraftId("pm_1"), draft?.id)
        assertEquals(OccasionId("event_1"), draft?.occasionId)
    }

    @Test
    fun getWishPreviewReviewQueue_mapsRoomMessagesToPureReviewItems() = runTest {
        every { pendingMessageDao.getAll() } returns flowOf(
            listOf(
                pendingMessage(id = "pm_1", contactId = "contact_1", status = " pending "),
                pendingMessage(id = "pm_2", contactId = "contact_2", status = "APPROVED"),
            ),
        )

        val queue = repository.getWishPreviewReviewQueue().first()

        assertEquals(2, queue.size)
        assertEquals(MessageDraftId("pm_1"), queue[0].id)
        assertEquals(ContactId("contact_1"), queue[0].contactId)
        assertEquals(MessageStatus.PENDING, queue[0].status)
        assertEquals(MessageStatus.APPROVED, queue[1].status)
    }

    @Test
    fun getSentListItems_mapsRoomMessagesToPureListItems() = runTest {
        every { sentMessageDao.getAll() } returns flowOf(
            listOf(
                SentMessageEntity(
                    id = "sent_1",
                    contactId = "contact_1",
                    eventType = "BIRTHDAY",
                    occasionType = "ANNIVERSARY",
                    eventYear = 2026,
                    messageText = "Happy anniversary",
                    channel = " whatsapp ",
                    sentAtMs = 1_700_000_100_000L,
                    deliveryStatus = " delivered ",
                ),
            ),
        )

        val items = repository.getSentListItems().first()

        assertEquals(SentMessageId("sent_1"), items.single().id)
        assertEquals(ContactId("contact_1"), items.single().contactId)
        assertEquals("ANNIVERSARY", items.single().occasionType)
        assertEquals("Happy anniversary", items.single().messageText)
        assertEquals(MessageChannel.WHATSAPP, items.single().channel)
        assertEquals(1_700_000_100_000L, items.single().sentAtMs)
        assertEquals(MessageDeliveryStatus.DELIVERED, items.single().deliveryStatus)
    }

    @Test
    fun getSentAnalyticsRecordsSince_mapsRoomMessagesToPureAnalyticsModel() = runTest {
        coEvery { sentMessageDao.getSentSinceYearStart(1_700_000_000_000L) } returns listOf(
            SentMessageEntity(
                id = "sent_1",
                contactId = "contact_1",
                eventType = "BIRTHDAY",
                eventYear = 2026,
                messageText = "Happy birthday",
                channel = MessageChannel.SMS.raw,
                sentAtMs = 1_700_000_100_000L,
                deliveryStatus = " delivered ",
                replyReceived = true,
            ),
        )

        val records = repository.getSentAnalyticsRecordsSince(1_700_000_000_000L)

        assertEquals(1, records.size)
        assertEquals(1_700_000_100_000L, records.single().sentAtMs)
        assertEquals(MessageDeliveryStatus.DELIVERED, records.single().deliveryStatus)
        assertTrue(records.single().replyReceived)
        assertTrue(records.single().countsAsNonFailedDelivery)
    }

    private fun pendingMessage(
        id: String = "pm_1",
        contactId: String = "contact_1",
        eventId: String = "event_1",
        channel: String = MessageChannel.SMS.raw,
        approvalMode: String = ApprovalMode.VIP_APPROVE.raw,
        status: String = MessageStatus.PENDING.raw,
        isUsingFallback: Boolean = false,
    ) = PendingMessageEntity(
        id = id,
        contactId = contactId,
        eventId = eventId,
        shortVariant = "Short draft",
        standardVariant = "Standard draft",
        longVariant = "Long draft",
        formalVariant = "Formal draft",
        funnyVariant = "Funny draft",
        emotionalVariant = "Emotional draft",
        selectedVariant = "standard",
        selectedVariantText = "Standard draft",
        channel = channel,
        scheduledForMs = 1_800_000_000_000L,
        approvalMode = approvalMode,
        status = status,
        isUsingFallback = isUsingFallback,
    )
}
