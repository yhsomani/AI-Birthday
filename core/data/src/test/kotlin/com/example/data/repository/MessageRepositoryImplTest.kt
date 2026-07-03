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
import com.example.domain.model.message.MessageApprovalState
import com.example.domain.model.message.MessageStatusUpdate
import com.example.domain.model.message.PendingMessageRecord
import com.example.domain.model.message.RetryQueuedMessageDraft
import com.example.domain.model.message.SentMessageRecord
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    fun getAllPending_mapsRoomMessagesToPureRecords() = runTest {
        every { pendingMessageDao.getAll() } returns flowOf(
            listOf(
                pendingMessage(
                    channel = " whatsapp ",
                    approvalMode = " fully_auto ",
                    status = " approved ",
                    qualityScore = 94,
                ),
            ),
        )

        val records = repository.getAllPending().first()

        assertEquals(MessageDraftId("pm_1"), records.single().id)
        assertEquals(ContactId("contact_1"), records.single().contactId)
        assertEquals(OccasionId("event_1"), records.single().occasionId)
        assertEquals(MessageChannel.WHATSAPP, records.single().channel)
        assertEquals(ApprovalMode.FULLY_AUTO, records.single().approvalMode)
        assertEquals(MessageStatus.APPROVED, records.single().status)
        assertEquals(94, records.single().qualityScore)
    }

    @Test
    fun insertPending_mapsPureRecordToRoomMessage() = runTest {
        val saved = slot<PendingMessageEntity>()

        repository.insertPending(
            PendingMessageRecord(
                id = MessageDraftId("pm_new"),
                contactId = ContactId("contact_new"),
                occasionId = OccasionId("event_new"),
                shortVariant = "Short",
                standardVariant = "Standard",
                longVariant = "Long",
                formalVariant = "Formal",
                funnyVariant = "Funny",
                emotionalVariant = "Emotional",
                selectedVariant = "funny",
                selectedVariantText = "Funny",
                channel = MessageChannel.EMAIL,
                scheduledForMs = 1_900_000_000_000L,
                approvalMode = ApprovalMode.SMART_APPROVE,
                status = MessageStatus.APPROVED,
                generatedAtMs = 1_800_000_000_000L,
                qualityScore = 88,
                scheduledYear = 2027,
                isUsingFallback = true,
            )
        )

        coVerify { pendingMessageDao.insert(capture(saved)) }
        assertEquals("pm_new", saved.captured.id)
        assertEquals("contact_new", saved.captured.contactId)
        assertEquals("event_new", saved.captured.eventId)
        assertEquals(MessageChannel.EMAIL.raw, saved.captured.channel)
        assertEquals(ApprovalMode.SMART_APPROVE.raw, saved.captured.approvalMode)
        assertEquals(MessageStatus.APPROVED.raw, saved.captured.status)
        assertEquals(1_800_000_000_000L, saved.captured.generatedAtMs)
        assertEquals(88, saved.captured.qualityScore)
        assertEquals(2027, saved.captured.scheduledYear)
        assertEquals(true, saved.captured.isUsingFallback)
    }

    @Test
    fun getPendingListItems_mapsRoomMessagesToPureListItems() = runTest {
        every { pendingMessageDao.getAll() } returns flowOf(
            listOf(
                pendingMessage(
                    channel = " email ",
                    approvalMode = "smart_approve",
                    status = "approved",
                    qualityScore = 35,
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
        assertEquals(35, items.single().qualityScore)
        assertEquals(true, items.single().isUsingFallback)
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
    fun getMessageApprovalStateById_mapsRoomMessageToPureApprovalState() = runTest {
        coEvery { pendingMessageDao.getById("pm_1") } returns pendingMessage(
            approvalMode = " smart_approve ",
            status = " approved ",
        ).copy(
            selectedVariantText = "Edited draft",
            editedByUser = true,
            userEditedText = "Edited draft",
        )

        val state = repository.getMessageApprovalStateById("pm_1")

        assertEquals(MessageDraftId("pm_1"), state?.id)
        assertEquals("Edited draft", state?.selectedVariantText)
        assertEquals(ApprovalMode.SMART_APPROVE, state?.approvalMode)
        assertEquals(MessageStatus.APPROVED, state?.status)
        assertEquals(true, state?.editedByUser)
        assertEquals("Edited draft", state?.userEditedText)
    }

    @Test
    fun saveMessageApprovalState_updatesApprovalColumns() = runTest {
        val state = MessageApprovalState(
            id = MessageDraftId("pm_1"),
            selectedVariantText = "Approved edit",
            approvalMode = ApprovalMode.SMART_APPROVE,
            status = MessageStatus.APPROVED,
            editedByUser = true,
            userEditedText = "Approved edit",
        )

        repository.saveMessageApprovalState(state)

        coVerify {
            pendingMessageDao.updateApprovalState(
                id = "pm_1",
                status = MessageStatus.APPROVED.raw,
                selectedVariantText = "Approved edit",
                editedByUser = true,
                userEditedText = "Approved edit",
            )
        }
    }

    @Test
    fun getRetryableMessageDraftById_mapsRoomMessageToPureRetryDraft() = runTest {
        coEvery { pendingMessageDao.getById("pm_1") } returns pendingMessage(
            channel = " whatsapp ",
            status = " failed ",
        )

        val draft = repository.getRetryableMessageDraftById("pm_1")

        assertEquals(MessageDraftId("pm_1"), draft?.id)
        assertEquals(ContactId("contact_1"), draft?.contactId)
        assertEquals(OccasionId("event_1"), draft?.occasionId)
        assertEquals(MessageChannel.WHATSAPP, draft?.channel)
        assertEquals(MessageStatus.FAILED, draft?.status)
        assertEquals(1_800_000_000_000L, draft?.scheduledForMs)
    }

    @Test
    fun saveRetryQueuedMessageDraft_updatesRetryColumns() = runTest {
        val state = RetryQueuedMessageDraft(
            id = MessageDraftId("pm_1"),
            status = MessageStatus.APPROVED,
            scheduledForMs = 1_900_000_000_000L,
        )

        repository.saveRetryQueuedMessageDraft(state)

        coVerify {
            pendingMessageDao.updateRetryState(
                id = "pm_1",
                status = MessageStatus.APPROVED.raw,
                scheduledForMs = 1_900_000_000_000L,
            )
        }
    }

    @Test
    fun saveMessageStatusUpdate_updatesStatusColumn() = runTest {
        repository.saveMessageStatusUpdate(
            MessageStatusUpdate(
                id = MessageDraftId("pm_1"),
                status = MessageStatus.EXPIRED,
            )
        )

        coVerify {
            pendingMessageDao.updateStatus(
                id = "pm_1",
                status = MessageStatus.EXPIRED.raw,
            )
        }
    }

    @Test
    fun getMessageDispatchStateById_mapsRoomMessageToPureDispatchState() = runTest {
        coEvery { pendingMessageDao.getById("pm_1") } returns pendingMessage(
            channel = " email ",
            approvalMode = " smart_approve ",
            status = " approved ",
        ).copy(
            editedByUser = true,
            userEditedText = "Edited dispatch text",
        )

        val state = repository.getMessageDispatchStateById("pm_1")

        assertEquals(MessageDraftId("pm_1"), state?.id)
        assertEquals(ContactId("contact_1"), state?.contactId)
        assertEquals(OccasionId("event_1"), state?.occasionId)
        assertEquals(MessageChannel.EMAIL, state?.channel)
        assertEquals(ApprovalMode.SMART_APPROVE, state?.draft?.approvalMode)
        assertEquals(MessageStatus.APPROVED, state?.status)
        assertEquals("Edited dispatch text", state?.dispatchDraft?.messageText)
    }

    @Test
    fun getMessageDispatchStateByEventId_mapsRoomMessageToPureDispatchState() = runTest {
        coEvery { pendingMessageDao.getByEventId("event_1") } returns pendingMessage(
            id = "pm_event",
            eventId = "event_1",
            status = " pending ",
        )

        val state = repository.getMessageDispatchStateByEventId("event_1")

        assertEquals(MessageDraftId("pm_event"), state?.id)
        assertEquals(OccasionId("event_1"), state?.occasionId)
        assertEquals(MessageStatus.PENDING, state?.status)
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
    fun getAllSent_mapsRoomMessagesToPureRecords() = runTest {
        every { sentMessageDao.getAll() } returns flowOf(
            listOf(
                SentMessageEntity(
                    id = "sent_1",
                    contactId = "contact_1",
                    eventType = "BIRTHDAY",
                    eventId = "event_1",
                    occasionType = "ANNIVERSARY",
                    occasionLabel = "Wedding",
                    eventYear = 2026,
                    messageText = "Happy anniversary",
                    channel = " whatsapp ",
                    sentAtMs = 1_700_000_100_000L,
                    deliveryStatus = " delivered ",
                    replyReceived = true,
                ),
            ),
        )

        val records = repository.getAllSent().first()

        assertEquals(SentMessageId("sent_1"), records.single().id)
        assertEquals(ContactId("contact_1"), records.single().contactId)
        assertEquals(OccasionId("event_1"), records.single().occasionId)
        assertEquals("ANNIVERSARY", records.single().occasionType)
        assertEquals("Wedding", records.single().occasionLabel)
        assertEquals(MessageChannel.WHATSAPP, records.single().channel)
        assertEquals(MessageDeliveryStatus.DELIVERED, records.single().deliveryStatus)
        assertEquals(true, records.single().replyReceived)
    }

    @Test
    fun insertSent_mapsPureRecordToRoomMessage() = runTest {
        val saved = slot<SentMessageEntity>()

        repository.insertSent(
            SentMessageRecord(
                id = SentMessageId("sent_new"),
                contactId = ContactId("contact_new"),
                eventType = "BIRTHDAY",
                occasionId = OccasionId("event_new"),
                occasionType = "WORK_ANNIVERSARY",
                occasionLabel = "Work date",
                eventYear = 2027,
                messageText = "Congratulations",
                channel = MessageChannel.EMAIL,
                sentAtMs = 1_900_000_000_000L,
                deliveryStatus = MessageDeliveryStatus.DELIVERED,
                aiGenerated = false,
                geminiModel = "manual",
                variantUsed = "custom",
                replyReceived = true,
                replyAtMs = 1_900_000_010_000L,
                isContactDeleted = true,
            )
        )

        coVerify { sentMessageDao.insert(capture(saved)) }
        assertEquals("sent_new", saved.captured.id)
        assertEquals("contact_new", saved.captured.contactId)
        assertEquals("event_new", saved.captured.eventId)
        assertEquals("WORK_ANNIVERSARY", saved.captured.occasionType)
        assertEquals("Work date", saved.captured.occasionLabel)
        assertEquals(MessageChannel.EMAIL.raw, saved.captured.channel)
        assertEquals(MessageDeliveryStatus.DELIVERED.raw, saved.captured.deliveryStatus)
        assertEquals(false, saved.captured.aiGenerated)
        assertEquals("manual", saved.captured.geminiModel)
        assertEquals("custom", saved.captured.variantUsed)
        assertEquals(true, saved.captured.replyReceived)
        assertEquals(1_900_000_010_000L, saved.captured.replyAtMs)
        assertEquals(true, saved.captured.isContactDeleted)
    }

    @Test
    fun getGenerationHistoryByContact_mapsRoomMessagesToPureGenerationHistory() = runTest {
        coEvery { sentMessageDao.getByContact("contact_1", 10) } returns listOf(
            SentMessageEntity(
                id = "sent_1",
                contactId = "contact_1",
                eventType = "BIRTHDAY",
                eventYear = 2026,
                messageText = "Happy birthday",
                channel = " email ",
                sentAtMs = 1_700_000_100_000L,
                deliveryStatus = " delivered ",
            ),
            SentMessageEntity(
                id = "sent_2",
                contactId = "contact_1",
                eventType = "BIRTHDAY",
                eventYear = 2025,
                messageText = "Have a great year",
                channel = MessageChannel.SMS.raw,
                sentAtMs = 1_600_000_100_000L,
                deliveryStatus = "failed",
            ),
        )

        val history = repository.getGenerationHistoryByContact("contact_1", 10)

        assertEquals(listOf("Happy birthday", "Have a great year"), history.previousWishes)
        assertEquals(MessageChannel.EMAIL, history.routeHistory[0].channel)
        assertEquals(MessageDeliveryStatus.DELIVERED, history.routeHistory[0].deliveryStatus)
        assertEquals(MessageChannel.SMS, history.routeHistory[1].channel)
        assertEquals(MessageDeliveryStatus.FAILED, history.routeHistory[1].deliveryStatus)
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

    @Test
    fun getSentAnalyticsRecordsSinceFlow_mapsRoomMessagesToPureAnalyticsModel() = runTest {
        every { sentMessageDao.getSentSinceFlow(1_700_000_000_000L) } returns flowOf(
            listOf(
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
            ),
        )

        val records = repository.getSentAnalyticsRecordsSinceFlow(1_700_000_000_000L).first()

        assertEquals(1, records.size)
        assertEquals(1_700_000_100_000L, records.single().sentAtMs)
        assertEquals(MessageDeliveryStatus.DELIVERED, records.single().deliveryStatus)
        assertTrue(records.single().replyReceived)
    }

    private fun pendingMessage(
        id: String = "pm_1",
        contactId: String = "contact_1",
        eventId: String = "event_1",
        channel: String = MessageChannel.SMS.raw,
        approvalMode: String = ApprovalMode.VIP_APPROVE.raw,
        status: String = MessageStatus.PENDING.raw,
        qualityScore: Int = 0,
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
        qualityScore = qualityScore,
        isUsingFallback = isUsingFallback,
    )
}
