package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.db.dao.ContactDao
import com.example.core.db.dao.EventDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.db.entities.SentMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostEventFollowUpWorkerTest {

    private lateinit var context: Context
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val eventDao: EventDao = mockk(relaxed = true)
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val geminiClient: GeminiClient = mockk(relaxed = true)
    private val prefs: SecurePrefs = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(FirebaseAuth::class)
        mockkObject(RateLimiter)
        mockkObject(DailyScheduler)
        mockkObject(NotificationHelper)

        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser

        every { prefs.getGeminiApiKey() } returns "test_api_key"
        every { prefs.isAiWishGenerationEnabled() } returns true
        every { prefs.getGlobalAutomationMode() } returns "SMART_APPROVE"
        every { prefs.getQuietHoursStart() } returns 0
        every { prefs.getQuietHoursEnd() } returns 0
        every { prefs.getBlackoutDates() } returns "[]"

        coEvery { RateLimiter.waitIfNeeded() } returns Unit
        coEvery { sentMessageDao.getByContact(any()) } returns emptyList()
        every { DailyScheduler.scheduleExactSend(any(), any()) } just Runs
        every { NotificationHelper.showApprovalNotification(any(), any(), any(), any(), any()) } just Runs
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork creates fully auto follow-up without review notification`() = runTest {
        val sent = sentMessage(id = "sent1", contactId = "c1", eventType = "event1")
        val contact = ContactEntity(
            id = "c1",
            name = "Amit",
            relationshipType = "FRIEND",
            preferredChannel = "SMS",
            automationMode = "FULLY_AUTO",
        )
        val event = EventEntity(id = "event1", contactId = "c1", type = "BIRTHDAY", label = "Birthday", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { sentMessageDao.getPostEventFollowUpCandidates(any(), any(), any()) } returns listOf(sent)
        coEvery { pendingMessageDao.getByEventId("FOLLOWUP_sent1") } returns null
        coEvery { contactDao.getById("c1") } returns contact
        coEvery { eventDao.getById("event1") } returns event
        coEvery { geminiClient.generate(any()) } returns "Hey Amit, hope your birthday dinner was fun. Did you try that new place?"

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("FOLLOWUP_sent1", pendingSlot.captured.eventId)
        assertEquals("FULLY_AUTO", pendingSlot.captured.approvalMode)
        assertEquals("APPROVED", pendingSlot.captured.status)
        assertEquals(100, pendingSlot.captured.qualityScore)
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify(exactly = 0) { NotificationHelper.showApprovalNotification(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `doWork downgrades fallback fully auto follow-up to smart approve`() = runTest {
        val sent = sentMessage(id = "sent1", contactId = "c1", eventType = "event1")
        val contact = ContactEntity(
            id = "c1",
            name = "Amit",
            relationshipType = "FRIEND",
            preferredChannel = "SMS",
            automationMode = "FULLY_AUTO",
        )
        val pendingSlot = slot<PendingMessageEntity>()

        coEvery { sentMessageDao.getPostEventFollowUpCandidates(any(), any(), any()) } returns listOf(sent)
        coEvery { pendingMessageDao.getByEventId("FOLLOWUP_sent1") } returns null
        coEvery { contactDao.getById("c1") } returns contact
        coEvery { eventDao.getById("event1") } returns null
        coEvery { geminiClient.generate(any()) } returns ""

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("SMART_APPROVE", pendingSlot.captured.approvalMode)
        assertEquals("PENDING", pendingSlot.captured.status)
        assertEquals(35, pendingSlot.captured.qualityScore)
        assertEquals(true, pendingSlot.captured.isUsingFallback)
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify { NotificationHelper.showApprovalNotification(any(), contact, any(), any(), pendingSlot.captured.id) }
    }

    @Test
    fun `doWork skips candidate when follow-up already exists`() = runTest {
        val sent = sentMessage(id = "sent1", contactId = "c1", eventType = "event1")
        val existing = PendingMessageEntity(
            id = "pending1",
            contactId = "c1",
            eventId = "FOLLOWUP_sent1",
            shortVariant = "Already queued",
            standardVariant = "Already queued",
            longVariant = "Already queued",
            formalVariant = "Already queued",
            funnyVariant = "Already queued",
            emotionalVariant = "Already queued",
            channel = "SMS",
            scheduledForMs = 1000L,
            approvalMode = "SMART_APPROVE",
        )

        coEvery { sentMessageDao.getPostEventFollowUpCandidates(any(), any(), any()) } returns listOf(sent)
        coEvery { pendingMessageDao.getByEventId("FOLLOWUP_sent1") } returns existing

        val result = worker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { geminiClient.generate(any()) }
        coVerify(exactly = 0) { pendingMessageDao.insert(any()) }
    }

    private fun worker(): PostEventFollowUpWorker {
        return TestListenableWorkerBuilder<PostEventFollowUpWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return PostEventFollowUpWorker(
                        appContext,
                        workerParameters,
                        contactDao,
                        eventDao,
                        pendingMessageDao,
                        sentMessageDao,
                        geminiClient,
                        prefs,
                    )
                }
            })
            .build()
    }

    private fun sentMessage(id: String, contactId: String, eventType: String): SentMessageEntity {
        return SentMessageEntity(
            id = id,
            contactId = contactId,
            eventType = eventType,
            eventYear = 2026,
            messageText = "Happy birthday Amit, hope this year brings more cricket and good coffee.",
            channel = "SMS",
            sentAtMs = System.currentTimeMillis() - 36L * 60 * 60 * 1000L,
            deliveryStatus = "SENT",
            aiGenerated = true,
        )
    }
}
