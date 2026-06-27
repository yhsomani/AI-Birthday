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
    fun sentMessageDao_insertAndCount() = runTest {
        sentMessageDao.insert(testSent("s1"))
        sentMessageDao.insert(testSent("s2"))
        val count = sentMessageDao.countAll().first()
        assertEquals(2, count)
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
