package com.example.core.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.DispatchAttemptDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.GiftHistoryDao
import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ActivityLogEntity
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.DispatchAttemptEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.GiftHistoryEntity
import com.example.core.db.entities.MemoryNoteEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageDeliveryStatus
import com.example.domain.model.dispatch.DispatchAttemptCreator
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.DispatchEligibilityRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private lateinit var db: AppDatabase
    private lateinit var contactDao: ContactDao
    private lateinit var eventDao: EventDao
    private lateinit var giftHistoryDao: GiftHistoryDao
    private lateinit var memoryNoteDao: MemoryNoteDao
    private lateinit var pendingMessageDao: PendingMessageDao
    private lateinit var sentMessageDao: SentMessageDao
    private lateinit var activityLogDao: com.example.core.db.dao.ActivityLogDao
    private lateinit var dispatchAttemptDao: DispatchAttemptDao

    @Before
    fun createDb() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).build()
        contactDao = db.contactDao()
        eventDao = db.eventDao()
        giftHistoryDao = db.giftHistoryDao()
        memoryNoteDao = db.memoryNoteDao()
        pendingMessageDao = db.pendingMessageDao()
        sentMessageDao = db.sentMessageDao()
        activityLogDao = db.activityLogDao()
        dispatchAttemptDao = db.dispatchAttemptDao()

        contactDao.upsert(ContactEntity(id = "c1", name = "Alice"))
        eventDao.upsert(EventEntity(
            id = "e1",
            contactId = "c1",
            type = "BIRTHDAY",
            dayOfMonth = 1,
            month = 1,
            nextOccurrenceMs = System.currentTimeMillis()
        ))
        eventDao.upsert(EventEntity(
            id = "e2",
            contactId = "c1",
            type = "ANNIVERSARY",
            dayOfMonth = 2,
            month = 1,
            nextOccurrenceMs = System.currentTimeMillis()
        ))
    }

    @After
    fun closeDb() { db.close() }

    private fun testContact(id: String = "c1", name: String = "Alice") = ContactEntity(id = id, name = name)
    private fun testEvent(id: String = "e1", contactId: String = "c1") = EventEntity(
        id = id,
        contactId = contactId,
        type = "BIRTHDAY",
        dayOfMonth = 1,
        month = 1,
        nextOccurrenceMs = System.currentTimeMillis()
    )
    private fun testMemoryNote(
        id: String = "m1",
        contactId: String = "c1",
        category: String = "GENERAL",
    ) = MemoryNoteEntity(
        id = id,
        contactId = contactId,
        noteText = "Remember this",
        category = category,
    )
    private fun testGift(
        id: String = "g1",
        contactId: String = "c1",
    ) = GiftHistoryEntity(
        id = id,
        contactId = contactId,
        giftName = "Book",
        giftCategory = "Books",
        occasionType = "BIRTHDAY",
        year = 2025,
        approxCostInr = 1200,
    )
    private fun testPending(id: String = "p1", eventId: String = "e1") = PendingMessageEntity(
        id = id,
        contactId = "c1",
        eventId = eventId,
        shortVariant = "Hi",
        standardVariant = "Hello",
        longVariant = "Dear friend, hope you have a great day!",
        formalVariant = "Respected Sir/Madam",
        funnyVariant = "LOL happy birthday!",
        emotionalVariant = "You mean so much to me",
        channel = MessageChannel.SMS.raw,
        scheduledForMs = System.currentTimeMillis(),
        approvalMode = "SMART_APPROVE"
    )
    private fun testSent(id: String = "s1", contactId: String = "c1") = SentMessageEntity(
        id = id,
        contactId = contactId,
        eventType = "BIRTHDAY",
        eventYear = 2025,
        messageText = "Happy Birthday!",
        channel = MessageChannel.SMS.raw,
        sentAtMs = System.currentTimeMillis(),
        deliveryStatus = "SENT"
    )
    private fun testDispatchAttempt(id: String = "da1", messageDraftId: String = "p1") = DispatchAttemptEntity(
        id = id,
        messageDraftId = messageDraftId,
        contactId = "c1",
        occasionId = "e1",
        channel = MessageChannel.SMS.raw,
        eligibilityDecision = DispatchEligibilityRecord.SEND_NOW.raw,
        requestedAtMs = 1_700_000_000_000,
        result = DispatchAttemptResult.QUEUED.raw,
        deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
        createdBy = DispatchAttemptCreator.WORKER.raw,
    )

    @Test
    fun contactDao_insertAndGetAll() = runTest {
        contactDao.upsert(testContact("c1", "Alice"))
        contactDao.upsert(testContact("c2", "Bob"))
        val contacts = contactDao.getAll().first()
        assertEquals(2, contacts.size)
        assertTrue(contacts.any { it.name == "Alice" })
        assertTrue(contacts.any { it.name == "Bob" })
    }

    @Test
    fun contactDao_getById() = runTest {
        contactDao.upsert(testContact("c1", "Alice"))
        val result = contactDao.getById("c1")
        assertNotNull(result)
        assertEquals("Alice", result!!.name)
    }

    @Test
    fun contactDao_delete() = runTest {
        contactDao.upsert(testContact("c1", "Alice"))
        contactDao.delete(testContact("c1", "Alice"))
        val contacts = contactDao.getAll().first()
        assertTrue(contacts.isEmpty())
    }

    @Test
    fun contactDao_countAll() = runTest {
        contactDao.upsert(testContact("c1"))
        contactDao.upsert(testContact("c2"))
        val count = contactDao.countAll().first()
        assertEquals(2, count)
    }

    @Test
    fun contactDao_updateHealthScore() = runTest {
        contactDao.upsert(testContact("c1"))
        contactDao.updateHealthScore("c1", 85)
        val contact = contactDao.getById("c1")
        assertEquals(85, contact!!.healthScore)
    }

    @Test
    fun eventDao_insertAndGetAll() = runTest {
        eventDao.upsert(testEvent("e1", "c1"))
        eventDao.upsert(testEvent("e2", "c1"))
        val events = eventDao.getAll().first()
        assertEquals(2, events.size)
    }

    @Test
    fun eventDao_countUpcomingMatchesUpcomingWindow() = runTest {
        val now = 1_700_000_000_000L
        eventDao.deleteAll()
        eventDao.upsert(testEvent("soon", "c1").copy(nextOccurrenceMs = now + 2L * 86_400_000L))
        eventDao.upsert(testEvent("later", "c1").copy(nextOccurrenceMs = now + 40L * 86_400_000L))
        eventDao.upsert(testEvent("inactive", "c1").copy(nextOccurrenceMs = now + 1L * 86_400_000L, isActive = false))

        assertEquals(1, eventDao.countUpcoming(days = 30, nowMs = now))
    }

    @Test
    fun eventDao_getNextUpcomingForContactReturnsEarliestActiveContactEvent() = runTest {
        val now = 1_700_000_000_000L
        eventDao.deleteAll()
        contactDao.upsert(testContact("c2", "Bob"))
        eventDao.upsert(testEvent("c1_later", "c1").copy(nextOccurrenceMs = now + 10L * 86_400_000L))
        eventDao.upsert(testEvent("c1_soon", "c1").copy(nextOccurrenceMs = now + 2L * 86_400_000L))
        eventDao.upsert(testEvent("c2_soonest", "c2").copy(nextOccurrenceMs = now + 1L * 86_400_000L))
        eventDao.upsert(testEvent("c1_inactive", "c1").copy(nextOccurrenceMs = now + 1L * 86_400_000L, isActive = false))
        eventDao.upsert(testEvent("c1_past", "c1").copy(nextOccurrenceMs = now - 1L * 86_400_000L))

        val event = eventDao.getNextUpcomingForContact(contactId = "c1", days = 30, nowMs = now)

        assertEquals("c1_soon", event?.id)
    }

    @Test
    fun eventDao_getTypeByIdReturnsRawTypeForSingleEvent() = runTest {
        eventDao.upsert(testEvent("event_type", "c1").copy(type = "ANNIVERSARY"))

        assertEquals("ANNIVERSARY", eventDao.getTypeById("event_type"))
        assertEquals(null, eventDao.getTypeById("missing"))
    }

    @Test
    fun eventDao_delete() = runTest {
        eventDao.upsert(testEvent("e1"))
        eventDao.delete(testEvent("e1"))
        val events = eventDao.getAll().first()
        assertTrue(events.none { it.id == "e1" })
    }

    @Test
    fun memoryNoteDao_getCategoryCountsForContactGroupsByCategory() = runTest {
        contactDao.upsert(testContact("c2", "Bob"))
        memoryNoteDao.upsert(testMemoryNote("m1", "c1", "GENERAL"))
        memoryNoteDao.upsert(testMemoryNote("m2", "c1", "GIFT"))
        memoryNoteDao.upsert(testMemoryNote("m3", "c1", "GENERAL"))
        memoryNoteDao.upsert(testMemoryNote("m4", "c2", "GENERAL"))

        val counts = memoryNoteDao.getCategoryCountsForContact("c1")

        assertEquals(2, counts.size)
        assertEquals("GENERAL", counts[0].category)
        assertEquals(2L, counts[0].count)
        assertEquals("GIFT", counts[1].category)
        assertEquals(1L, counts[1].count)
    }

    @Test
    fun memoryAndGiftDaos_countByContactScopesToContact() = runTest {
        contactDao.upsert(testContact("c2", "Bob"))
        memoryNoteDao.upsert(testMemoryNote("m1", "c1", "GENERAL"))
        memoryNoteDao.upsert(testMemoryNote("m2", "c1", "GIFT"))
        memoryNoteDao.upsert(testMemoryNote("m3", "c2", "GENERAL"))
        giftHistoryDao.upsert(testGift("g1", "c1"))
        giftHistoryDao.upsert(testGift("g2", "c1"))
        giftHistoryDao.upsert(testGift("g3", "c2"))

        assertEquals(2, memoryNoteDao.countByContact("c1"))
        assertEquals(1, memoryNoteDao.countByContact("c2"))
        assertEquals(2, giftHistoryDao.countByContact("c1"))
        assertEquals(1, giftHistoryDao.countByContact("c2"))
    }

    @Test
    fun pendingMessageDao_insertAndCount() = runTest {
        pendingMessageDao.insert(testPending("p1", "e1"))
        pendingMessageDao.insert(testPending("p2", "e2"))
        val count = pendingMessageDao.countPending().first()
        assertEquals(2, count)
    }

    @Test
    fun pendingMessageDao_updateStatus() = runTest {
        pendingMessageDao.insert(testPending("p1"))
        pendingMessageDao.updateStatus("p1", "APPROVED")
        val pending = pendingMessageDao.getAll().first()
        assertEquals("APPROVED", pending.first().status)
    }

    @Test
    fun pendingMessageDao_updateStatusIfCurrent_claimsOnlyExpectedState() = runTest {
        pendingMessageDao.insert(testPending("p1").copy(status = "APPROVED"))

        val firstClaim = pendingMessageDao.updateStatusIfCurrent(
            id = "p1",
            expectedStatus = "APPROVED",
            newStatus = "DISPATCHING",
        )
        val secondClaim = pendingMessageDao.updateStatusIfCurrent(
            id = "p1",
            expectedStatus = "APPROVED",
            newStatus = "DISPATCHING",
        )
        val pending = pendingMessageDao.getAll().first().single { it.id == "p1" }

        assertEquals(1, firstClaim)
        assertEquals(0, secondClaim)
        assertEquals("DISPATCHING", pending.status)
    }

    @Test
    fun pendingMessageDao_markSmsHandoffSentIfAwaitingCallback_preservesFailedRows() = runTest {
        pendingMessageDao.insert(testPending("dispatching", "event_dispatching").copy(status = "DISPATCHING"))
        pendingMessageDao.insert(testPending("failed", "event_failed").copy(status = "FAILED"))
        pendingMessageDao.insert(testPending("rejected", "event_rejected").copy(status = "REJECTED"))

        val dispatchingUpdated = pendingMessageDao.markSmsHandoffSentIfAwaitingCallback("dispatching")
        val failedUpdated = pendingMessageDao.markSmsHandoffSentIfAwaitingCallback("failed")
        val rejectedUpdated = pendingMessageDao.markSmsHandoffSentIfAwaitingCallback("rejected")
        val byId = pendingMessageDao.getAll().first().associateBy { it.id }

        assertEquals(1, dispatchingUpdated)
        assertEquals(0, failedUpdated)
        assertEquals(0, rejectedUpdated)
        assertEquals("SENT", byId["dispatching"]?.status)
        assertEquals("FAILED", byId["failed"]?.status)
        assertEquals("REJECTED", byId["rejected"]?.status)
    }

    @Test
    fun pendingMessageDao_markSmsCallbackFailed_marksOnlySendAttemptRowsFailed() = runTest {
        pendingMessageDao.insert(testPending("sent", "event_sent").copy(status = "SENT"))
        pendingMessageDao.insert(testPending("dispatching", "event_dispatching").copy(status = "DISPATCHING"))
        pendingMessageDao.insert(testPending("approved", "event_approved").copy(status = "APPROVED"))
        pendingMessageDao.insert(testPending("rejected", "event_rejected").copy(status = "REJECTED"))

        val sentUpdated = pendingMessageDao.markSmsCallbackFailed("sent")
        val dispatchingUpdated = pendingMessageDao.markSmsCallbackFailed("dispatching")
        val approvedUpdated = pendingMessageDao.markSmsCallbackFailed("approved")
        val rejectedUpdated = pendingMessageDao.markSmsCallbackFailed("rejected")
        val byId = pendingMessageDao.getAll().first().associateBy { it.id }

        assertEquals(1, sentUpdated)
        assertEquals(1, dispatchingUpdated)
        assertEquals(1, approvedUpdated)
        assertEquals(0, rejectedUpdated)
        assertEquals("FAILED", byId["sent"]?.status)
        assertEquals("FAILED", byId["dispatching"]?.status)
        assertEquals("FAILED", byId["approved"]?.status)
        assertEquals("REJECTED", byId["rejected"]?.status)
    }

    @Test
    fun pendingMessageDao_getBootRecoverableAutoSends_includesApprovedAndPendingSmartApprove() = runTest {
        pendingMessageDao.insert(
            testPending("approved_full_auto", "event_approved").copy(
                approvalMode = "FULLY_AUTO",
                status = "APPROVED",
            )
        )
        pendingMessageDao.insert(
            testPending("pending_smart", "event_smart").copy(
                approvalMode = "SMART_APPROVE",
                status = "PENDING",
            )
        )
        pendingMessageDao.insert(
            testPending("pending_vip", "event_vip").copy(
                approvalMode = "VIP_APPROVE",
                status = "PENDING",
            )
        )
        pendingMessageDao.insert(
            testPending("pending_always_ask", "event_always").copy(
                approvalMode = "ALWAYS_ASK",
                status = "PENDING",
            )
        )
        pendingMessageDao.insert(
            testPending("sent_smart", "event_sent").copy(
                approvalMode = "SMART_APPROVE",
                status = "SENT",
            )
        )

        val ids = pendingMessageDao.getBootRecoverableAutoSends().map { it.id }.toSet()

        assertEquals(setOf("approved_full_auto", "pending_smart"), ids)
    }

    @Test
    fun pendingMessageDao_getDispatchingMessages_returnsOnlyDispatching() = runTest {
        pendingMessageDao.insert(testPending("dispatching", "event_dispatching").copy(status = "DISPATCHING"))
        pendingMessageDao.insert(testPending("approved", "event_approved").copy(status = "APPROVED"))
        pendingMessageDao.insert(testPending("pending", "event_pending").copy(status = "PENDING"))

        val ids = pendingMessageDao.getDispatchingMessages().map { it.id }

        assertEquals(listOf("dispatching"), ids)
    }

    @Test
    fun pendingMessageDao_updateStatusAndScheduledForIfCurrent_updatesOnlyExpectedState() = runTest {
        pendingMessageDao.insert(testPending("p1").copy(status = "DISPATCHING", scheduledForMs = 100L))

        val firstUpdate = pendingMessageDao.updateStatusAndScheduledForIfCurrent(
            id = "p1",
            expectedStatus = "DISPATCHING",
            newStatus = "APPROVED",
            scheduledForMs = 200L,
        )
        val secondUpdate = pendingMessageDao.updateStatusAndScheduledForIfCurrent(
            id = "p1",
            expectedStatus = "DISPATCHING",
            newStatus = "FAILED",
            scheduledForMs = 300L,
        )
        val pending = pendingMessageDao.getById("p1")

        assertEquals(1, firstUpdate)
        assertEquals(0, secondUpdate)
        assertEquals("APPROVED", pending?.status)
        assertEquals(200L, pending?.scheduledForMs)
    }

    @Test
    fun sentMessageDao_insertAndCount() = runTest {
        sentMessageDao.insert(testSent("s1"))
        sentMessageDao.insert(testSent("s2"))
        val count = sentMessageDao.countAll().first()
        assertEquals(2, count)
    }

    @Test
    fun sentMessageDao_markStalePendingSmsDeliveryStatus_updatesOnlyOldPendingSmsRows() = runTest {
        sentMessageDao.insert(
            testSent("old_pending_sms").copy(
                channel = MessageChannel.SMS.raw,
                sentAtMs = 100L,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
            )
        )
        sentMessageDao.insert(
            testSent("fresh_pending_sms").copy(
                channel = MessageChannel.SMS.raw,
                sentAtMs = 300L,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
            )
        )
        sentMessageDao.insert(
            testSent("old_sent_sms").copy(
                channel = MessageChannel.SMS.raw,
                sentAtMs = 100L,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
            )
        )
        sentMessageDao.insert(
            testSent("old_pending_email").copy(
                channel = MessageChannel.EMAIL.raw,
                sentAtMs = 100L,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
            )
        )

        val updated = sentMessageDao.markStalePendingSmsDeliveryStatus(
            cutoffMs = 200L,
            status = MessageDeliveryStatus.UNKNOWN.raw,
        )
        val byId = sentMessageDao.getAll().first().associateBy { it.id }

        assertEquals(1, updated)
        assertEquals(MessageDeliveryStatus.UNKNOWN.raw, byId["old_pending_sms"]?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY.raw, byId["fresh_pending_sms"]?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.SENT.raw, byId["old_sent_sms"]?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY.raw, byId["old_pending_email"]?.deliveryStatus)
    }

    @Test
    fun sentMessageDao_updateSmsCallbackDeliveryStatus_preservesStrongestCallbackState() = runTest {
        sentMessageDao.insert(
            testSent("failed_sms").copy(
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
            )
        )
        sentMessageDao.insert(
            testSent("delivered_sms").copy(
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.DELIVERED.raw,
            )
        )
        sentMessageDao.insert(
            testSent("sent_sms").copy(
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
            )
        )
        sentMessageDao.insert(
            testSent("delivered_then_failed_sms").copy(
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.DELIVERED.raw,
            )
        )

        sentMessageDao.updateSmsCallbackDeliveryStatus(
            "failed_sms",
            MessageDeliveryStatus.SENT.raw,
        )
        sentMessageDao.updateSmsCallbackDeliveryStatus(
            "delivered_sms",
            MessageDeliveryStatus.SENT.raw,
        )
        sentMessageDao.updateSmsCallbackDeliveryStatus(
            "sent_sms",
            MessageDeliveryStatus.DELIVERED.raw,
        )
        sentMessageDao.updateSmsCallbackDeliveryStatus(
            "delivered_then_failed_sms",
            MessageDeliveryStatus.FAILED.raw,
        )
        val byId = sentMessageDao.getAll().first().associateBy { it.id }

        assertEquals(MessageDeliveryStatus.FAILED.raw, byId["failed_sms"]?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.DELIVERED.raw, byId["delivered_sms"]?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.DELIVERED.raw, byId["sent_sms"]?.deliveryStatus)
        assertEquals(MessageDeliveryStatus.FAILED.raw, byId["delivered_then_failed_sms"]?.deliveryStatus)
    }

    @Test
    fun dispatchAttemptDao_recordsOutcomeAndRecoveryState() = runTest {
        pendingMessageDao.insert(testPending("p1", "e1"))
        dispatchAttemptDao.upsert(testDispatchAttempt("da1", "p1"))

        assertEquals(
            listOf("da1"),
            dispatchAttemptDao.getByMessageDraft("p1").first().map { it.id },
        )
        assertEquals(0, dispatchAttemptDao.countDeadLettered().first())
        assertEquals(0, dispatchAttemptDao.countFailureRecoveryQueue().first())

        dispatchAttemptDao.updateOutcome(
            id = "da1",
            attemptedAtMs = 1_700_000_000_100,
            resolvedAtMs = 1_700_000_000_200,
            result = DispatchAttemptResult.FAILED_RETRYABLE.raw,
            channel = MessageChannel.EMAIL.raw,
            deliveryStatus = MessageDeliveryStatus.FAILED.raw,
            providerMessageId = null,
            errorType = "NETWORK",
            errorCode = "TIMEOUT",
            redactedErrorMessage = "Timed out",
            retryCount = 1,
            nextRetryAtMs = 1_700_000_060_000,
            deadLetteredAtMs = 1_700_000_120_000,
        )

        val updated = dispatchAttemptDao.getById("da1")
        assertEquals(DispatchAttemptResult.FAILED_RETRYABLE.raw, updated?.result)
        assertEquals(MessageChannel.EMAIL.raw, updated?.channel)
        assertEquals(MessageDeliveryStatus.FAILED.raw, updated?.deliveryStatus)
        assertEquals("NETWORK", updated?.errorType)
        assertEquals(1, dispatchAttemptDao.countDeadLettered().first())
        assertEquals(1, dispatchAttemptDao.countFailureRecoveryQueue().first())
        assertEquals(listOf("da1"), dispatchAttemptDao.getFailureRecoveryQueue().map { it.id })
        assertEquals("da1", dispatchAttemptDao.getLatestFailureForMessageDraft("p1")?.id)
        assertEquals(1, dispatchAttemptDao.getMaxRetryCountForMessageDraft("p1"))

        dispatchAttemptDao.updateOutcome(
            id = "da1",
            attemptedAtMs = 1_700_000_000_100,
            resolvedAtMs = 1_700_000_000_300,
            result = DispatchAttemptResult.RETRY_QUEUED.raw,
            channel = MessageChannel.EMAIL.raw,
            deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
            providerMessageId = null,
            errorType = "NETWORK",
            errorCode = "TIMEOUT",
            redactedErrorMessage = "Timed out",
            retryCount = 2,
            nextRetryAtMs = 1_700_000_180_000,
            deadLetteredAtMs = null,
        )

        assertEquals(0, dispatchAttemptDao.countDeadLettered().first())
        assertEquals(0, dispatchAttemptDao.countFailureRecoveryQueue().first())
        assertEquals(null, dispatchAttemptDao.getLatestFailureForMessageDraft("p1"))
        assertEquals(2, dispatchAttemptDao.getMaxRetryCountForMessageDraft("p1"))
    }

    @Test
    fun dispatchAttemptDao_getLatestForMessageDraft_returnsNewestRequestedAt() = runTest {
        pendingMessageDao.insert(testPending("p1", "e1"))
        dispatchAttemptDao.upsert(testDispatchAttempt("old", "p1").copy(requestedAtMs = 100L))
        dispatchAttemptDao.upsert(testDispatchAttempt("new", "p1").copy(requestedAtMs = 200L))

        assertEquals("new", dispatchAttemptDao.getLatestForMessageDraft("p1")?.id)
    }

    @Test
    fun dispatchAttemptDao_getSuccessfulChannelsSince_returnsRecentSuccessfulChannelsOnly() = runTest {
        pendingMessageDao.insert(testPending("p1", "e1"))
        pendingMessageDao.insert(testPending("p2", "e2"))
        dispatchAttemptDao.upsert(
            testDispatchAttempt("sms_success", "p1").copy(
                channel = MessageChannel.SMS.raw,
                requestedAtMs = 2_000L,
                resolvedAtMs = 2_500L,
                result = DispatchAttemptResult.PENDING_DELIVERY.raw,
            ),
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("email_success", "p1").copy(
                channel = MessageChannel.EMAIL.raw,
                requestedAtMs = 3_000L,
                resolvedAtMs = 3_500L,
                result = DispatchAttemptResult.SENT.raw,
            ),
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("old_whatsapp_success", "p2").copy(
                channel = MessageChannel.WHATSAPP.raw,
                requestedAtMs = 500L,
                resolvedAtMs = 700L,
                result = DispatchAttemptResult.SENT.raw,
            ),
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("failed_sms", "p2").copy(
                channel = MessageChannel.SMS.raw,
                requestedAtMs = 4_000L,
                resolvedAtMs = 4_500L,
                result = DispatchAttemptResult.FAILED_FINAL.raw,
            ),
        )

        val channels = dispatchAttemptDao.getSuccessfulChannelsSince(1_000L)

        assertEquals(setOf(MessageChannel.SMS.raw, MessageChannel.EMAIL.raw), channels.toSet())
    }

    @Test
    fun dispatchAttemptDao_initialSmsHandoffUpdateDoesNotOverwriteCallbackOutcome() = runTest {
        pendingMessageDao.insert(testPending("p1", "e1"))
        dispatchAttemptDao.upsert(
            testDispatchAttempt("da_pending", "p1").copy(
                resolvedAtMs = 1_800L,
            ),
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("da_sent", "p1").copy(
                result = DispatchAttemptResult.SENT.raw,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                providerMessageId = "sent_sms",
                resolvedAtMs = 1_900L,
            ),
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("da_failed", "p1").copy(
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                errorType = "SMS_SENT_CALLBACK_FAILED",
                errorCode = "5",
                redactedErrorMessage = "Android SMS sent callback reported failure after send handoff.",
                deadLetteredAtMs = 1_950L,
                resolvedAtMs = 1_950L,
            ),
        )

        listOf("da_pending", "da_sent", "da_failed").forEach { attemptId ->
            dispatchAttemptDao.updateInitialSmsHandoffOutcomeIfAwaitingCallback(
                id = attemptId,
                attemptedAtMs = 2_000L,
                resolvedAtMs = 2_100L,
                result = DispatchAttemptResult.PENDING_DELIVERY.raw,
                channel = MessageChannel.SMS.raw,
                deliveryStatus = MessageDeliveryStatus.PENDING_DELIVERY.raw,
                providerMessageId = null,
                errorType = null,
                errorCode = null,
                redactedErrorMessage = null,
                retryCount = 0,
                nextRetryAtMs = null,
                deadLetteredAtMs = null,
            )
        }

        val pending = dispatchAttemptDao.getById("da_pending")
        val sent = dispatchAttemptDao.getById("da_sent")
        val failed = dispatchAttemptDao.getById("da_failed")

        assertEquals(DispatchAttemptResult.PENDING_DELIVERY.raw, pending?.result)
        assertEquals(MessageDeliveryStatus.PENDING_DELIVERY.raw, pending?.deliveryStatus)
        assertEquals(2_100L, pending?.resolvedAtMs)

        assertEquals(DispatchAttemptResult.SENT.raw, sent?.result)
        assertEquals(MessageDeliveryStatus.SENT.raw, sent?.deliveryStatus)
        assertEquals("sent_sms", sent?.providerMessageId)
        assertEquals(1_900L, sent?.resolvedAtMs)

        assertEquals(DispatchAttemptResult.FAILED_FINAL.raw, failed?.result)
        assertEquals(MessageDeliveryStatus.FAILED.raw, failed?.deliveryStatus)
        assertEquals("SMS_SENT_CALLBACK_FAILED", failed?.errorType)
        assertEquals("5", failed?.errorCode)
        assertEquals(1_950L, failed?.deadLetteredAtMs)
    }

    @Test
    fun dispatchAttemptDao_smsCallbackOutcome_preservesStrongestCallbackState() = runTest {
        pendingMessageDao.insert(testPending("p1", "e1"))
        dispatchAttemptDao.upsert(
            testDispatchAttempt("da_failed", "p1").copy(
                result = DispatchAttemptResult.FAILED_FINAL.raw,
                deliveryStatus = MessageDeliveryStatus.FAILED.raw,
                providerMessageId = "failed_sms",
                errorType = "SMS_DELIVERY_CALLBACK_FAILED",
                errorCode = "5",
                redactedErrorMessage = "Delivery failed",
                deadLetteredAtMs = 1_900L,
                resolvedAtMs = 1_900L,
            )
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("da_delivered", "p1").copy(
                result = DispatchAttemptResult.DELIVERED.raw,
                deliveryStatus = MessageDeliveryStatus.DELIVERED.raw,
                providerMessageId = "delivered_sms",
                resolvedAtMs = 1_800L,
            )
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("da_sent", "p1").copy(
                result = DispatchAttemptResult.SENT.raw,
                deliveryStatus = MessageDeliveryStatus.SENT.raw,
                providerMessageId = "sent_sms",
                resolvedAtMs = 1_700L,
            )
        )
        dispatchAttemptDao.upsert(
            testDispatchAttempt("da_delivered_then_failed", "p1").copy(
                result = DispatchAttemptResult.DELIVERED.raw,
                deliveryStatus = MessageDeliveryStatus.DELIVERED.raw,
                providerMessageId = "delivered_then_failed_sms",
                resolvedAtMs = 1_600L,
            )
        )

        dispatchAttemptDao.updateSmsCallbackOutcome(
            id = "da_failed",
            resolvedAtMs = 2_000L,
            result = DispatchAttemptResult.SENT.raw,
            channel = MessageChannel.SMS.raw,
            deliveryStatus = MessageDeliveryStatus.SENT.raw,
            providerMessageId = "late_sent_sms",
            errorType = null,
            errorCode = null,
            redactedErrorMessage = null,
            deadLetteredAtMs = null,
        )
        dispatchAttemptDao.updateSmsCallbackOutcome(
            id = "da_delivered",
            resolvedAtMs = 2_100L,
            result = DispatchAttemptResult.SENT.raw,
            channel = MessageChannel.SMS.raw,
            deliveryStatus = MessageDeliveryStatus.SENT.raw,
            providerMessageId = "late_sent_sms",
            errorType = null,
            errorCode = null,
            redactedErrorMessage = null,
            deadLetteredAtMs = null,
        )
        dispatchAttemptDao.updateSmsCallbackOutcome(
            id = "da_sent",
            resolvedAtMs = 2_200L,
            result = DispatchAttemptResult.DELIVERED.raw,
            channel = MessageChannel.SMS.raw,
            deliveryStatus = MessageDeliveryStatus.DELIVERED.raw,
            providerMessageId = "delivered_sms",
            errorType = null,
            errorCode = null,
            redactedErrorMessage = null,
            deadLetteredAtMs = null,
        )
        dispatchAttemptDao.updateSmsCallbackOutcome(
            id = "da_delivered_then_failed",
            resolvedAtMs = 2_300L,
            result = DispatchAttemptResult.FAILED_FINAL.raw,
            channel = MessageChannel.SMS.raw,
            deliveryStatus = MessageDeliveryStatus.FAILED.raw,
            providerMessageId = "failed_sms",
            errorType = "SMS_DELIVERY_CALLBACK_FAILED",
            errorCode = "5",
            redactedErrorMessage = "Delivery failed",
            deadLetteredAtMs = 2_300L,
        )

        val failed = dispatchAttemptDao.getById("da_failed")
        val delivered = dispatchAttemptDao.getById("da_delivered")
        val sent = dispatchAttemptDao.getById("da_sent")
        val deliveredThenFailed = dispatchAttemptDao.getById("da_delivered_then_failed")

        assertEquals(DispatchAttemptResult.FAILED_FINAL.raw, failed?.result)
        assertEquals(MessageDeliveryStatus.FAILED.raw, failed?.deliveryStatus)
        assertEquals("failed_sms", failed?.providerMessageId)
        assertEquals(1_900L, failed?.resolvedAtMs)

        assertEquals(DispatchAttemptResult.DELIVERED.raw, delivered?.result)
        assertEquals(MessageDeliveryStatus.DELIVERED.raw, delivered?.deliveryStatus)
        assertEquals("delivered_sms", delivered?.providerMessageId)
        assertEquals(1_800L, delivered?.resolvedAtMs)

        assertEquals(DispatchAttemptResult.DELIVERED.raw, sent?.result)
        assertEquals(MessageDeliveryStatus.DELIVERED.raw, sent?.deliveryStatus)
        assertEquals("delivered_sms", sent?.providerMessageId)
        assertEquals(2_200L, sent?.resolvedAtMs)

        assertEquals(DispatchAttemptResult.FAILED_FINAL.raw, deliveredThenFailed?.result)
        assertEquals(MessageDeliveryStatus.FAILED.raw, deliveredThenFailed?.deliveryStatus)
        assertEquals("failed_sms", deliveredThenFailed?.providerMessageId)
        assertEquals(2_300L, deliveredThenFailed?.deadLetteredAtMs)
    }

    @Test
    fun activityLogDao_filtersByTypeAndDeletesOldEntries() = runTest {
        activityLogDao.insert(
            ActivityLogEntity(
                id = "a1",
                type = "MESSAGE",
                title = "Message approved",
                detail = "A message was approved.",
                messageId = "p1",
                createdAtMs = 100L,
            )
        )
        activityLogDao.insert(
            ActivityLogEntity(
                id = "a2",
                type = "SYNC",
                title = "Contacts synced",
                detail = "Contacts were refreshed.",
                createdAtMs = 200L,
            )
        )

        assertEquals(listOf("a2", "a1"), activityLogDao.getRecent(10).first().map { it.id })
        assertEquals(listOf("a1"), activityLogDao.getByType("MESSAGE", 10).first().map { it.id })

        activityLogDao.deleteOlderThan(150L)

        assertEquals(listOf("a2"), activityLogDao.getRecent(10).first().map { it.id })
    }
}
