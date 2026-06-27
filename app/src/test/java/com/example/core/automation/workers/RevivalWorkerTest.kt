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
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.RateLimiter
import com.example.core.prefs.SecurePrefs
import com.example.domain.automation.RevivalCadencePolicy
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageStatus
import com.example.domain.model.occasion.OccasionType
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
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
class RevivalWorkerTest {

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
        mockkObject(NotificationHelper)
        mockkObject(DailyScheduler)

        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser

        every { prefs.getGeminiApiKey() } returns "test_api_key"
        every { prefs.isAiWishGenerationEnabled() } returns true
        every { prefs.getGlobalApprovalMode() } returns ApprovalMode.SMART_APPROVE
        every { prefs.getQuietHoursStart() } returns 0
        every { prefs.getQuietHoursEnd() } returns 0
        every { prefs.getBlackoutDates() } returns "[]"

        coEvery { RateLimiter.waitIfNeeded() } returns Unit
        coEvery { sentMessageDao.getByContact(any()) } returns emptyList()
        every { NotificationHelper.showRevivalNotification(any(), any(), any(), any(), any()) } just Runs
        every { DailyScheduler.scheduleExactSend(any(), any()) } just Runs
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork with revival contacts generates reconnect message`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Priya",
            primaryPhone = "+15551234567",
            healthScore = 20,
            lastInteractionDate = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000L,
        )
        val pendingSlot = slot<PendingMessageEntity>()
        coEvery { contactDao.getContactsForRevival(any()) } returns listOf(contact)
        coEvery { geminiClient.generate(any()) } returns "Hey Priya, it's been a while!"

        val worker = TestListenableWorkerBuilder<RevivalWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return RevivalWorker(appContext, workerParameters, contactDao, eventDao, pendingMessageDao, sentMessageDao, geminiClient, prefs)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("SMART_APPROVE", pendingSlot.captured.approvalMode)
        assertEquals(MessageStatus.PENDING.raw, pendingSlot.captured.status)
        coVerify {
            eventDao.upsert(match {
                it.id == RevivalCadencePolicy.eventId("c1") &&
                    it.type == OccasionType.REVIVAL.raw &&
                    it.label == "Revival" &&
                    it.source == "AI_INFERRED"
            })
        }
        coVerify { contactDao.updateLastRevivalAttempt("c1", any()) }
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify { NotificationHelper.showRevivalNotification(any(), "Priya", any(), any(), "c1") }
    }

    @Test
    fun `doWork fully auto revival schedules without review notification`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Priya",
            primaryPhone = "+15551234567",
            healthScore = 20,
            automationMode = "FULLY_AUTO",
            lastInteractionDate = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000L,
        )
        val pendingSlot = slot<PendingMessageEntity>()
        coEvery { contactDao.getContactsForRevival(any()) } returns listOf(contact)
        coEvery { geminiClient.generate(any()) } returns "Hey Priya, it's been a while!"

        val worker = TestListenableWorkerBuilder<RevivalWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return RevivalWorker(appContext, workerParameters, contactDao, eventDao, pendingMessageDao, sentMessageDao, geminiClient, prefs)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("FULLY_AUTO", pendingSlot.captured.approvalMode)
        assertEquals(MessageStatus.APPROVED.raw, pendingSlot.captured.status)
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify(exactly = 0) { NotificationHelper.showRevivalNotification(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `doWork forces review and skips scheduling revival when no route is available`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Priya",
            healthScore = 20,
            automationMode = "FULLY_AUTO",
            lastInteractionDate = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000L,
        )
        val pendingSlot = slot<PendingMessageEntity>()
        coEvery { contactDao.getContactsForRevival(any()) } returns listOf(contact)
        coEvery { geminiClient.generate(any()) } returns "Hey Priya, it's been a while. Want to catch up this weekend?"

        val worker = TestListenableWorkerBuilder<RevivalWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return RevivalWorker(appContext, workerParameters, contactDao, eventDao, pendingMessageDao, sentMessageDao, geminiClient, prefs)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("ALWAYS_ASK", pendingSlot.captured.approvalMode)
        assertEquals(MessageStatus.PENDING.raw, pendingSlot.captured.status)
        assertEquals(MessageChannel.SMS.raw, pendingSlot.captured.channel)
        verify(exactly = 0) { DailyScheduler.scheduleExactSend(any(), any()) }
        verify { NotificationHelper.showRevivalNotification(any(), "Priya", any(), pendingSlot.captured.selectedVariantText, "c1") }
    }

    @Test
    fun `doWork downgrades fallback fully auto revival to smart approve`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Priya",
            primaryPhone = "+15551234567",
            healthScore = 20,
            automationMode = "FULLY_AUTO",
            lastInteractionDate = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000L,
        )
        val pendingSlot = slot<PendingMessageEntity>()
        coEvery { contactDao.getContactsForRevival(any()) } returns listOf(contact)
        coEvery { geminiClient.generate(any()) } returns ""

        val worker = TestListenableWorkerBuilder<RevivalWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return RevivalWorker(appContext, workerParameters, contactDao, eventDao, pendingMessageDao, sentMessageDao, geminiClient, prefs)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("SMART_APPROVE", pendingSlot.captured.approvalMode)
        assertEquals(MessageStatus.PENDING.raw, pendingSlot.captured.status)
        assertEquals(35, pendingSlot.captured.qualityScore)
        assertEquals(true, pendingSlot.captured.isUsingFallback)
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify { NotificationHelper.showRevivalNotification(any(), "Priya", any(), pendingSlot.captured.selectedVariantText, "c1") }
    }

    @Test
    fun `doWork skips contact with active same-year revival draft`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Priya",
            healthScore = 20,
            lastInteractionDate = System.currentTimeMillis() - 40L * 24 * 60 * 60 * 1000L,
        )
        coEvery { contactDao.getContactsForRevival(any()) } returns listOf(contact)
        coEvery { pendingMessageDao.getPendingMessage(eq("c1"), eq(RevivalCadencePolicy.eventId("c1")), any()) } returns pendingRevival(
            status = MessageStatus.PENDING.raw,
        )

        val worker = TestListenableWorkerBuilder<RevivalWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return RevivalWorker(appContext, workerParameters, contactDao, eventDao, pendingMessageDao, sentMessageDao, geminiClient, prefs)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { geminiClient.generate(any()) }
        coVerify(exactly = 0) { eventDao.upsert(any()) }
        coVerify(exactly = 0) { pendingMessageDao.insert(any()) }
        coVerify(exactly = 0) { contactDao.updateLastRevivalAttempt(any(), any()) }
        verify(exactly = 0) { DailyScheduler.scheduleExactSend(any(), any()) }
    }

    @Test
    fun `doWork skips professional contact before cadence elapses`() = runTest {
        val contact = ContactEntity(
            id = "c1",
            name = "Priya",
            relationshipType = "COLLEAGUE",
            healthScore = 40,
            lastRevivalAttemptMs = System.currentTimeMillis() - 45L * 24 * 60 * 60 * 1000L,
            lastInteractionDate = System.currentTimeMillis() - 120L * 24 * 60 * 60 * 1000L,
        )
        coEvery { contactDao.getContactsForRevival(any()) } returns listOf(contact)
        coEvery { pendingMessageDao.getPendingMessage(eq("c1"), eq(RevivalCadencePolicy.eventId("c1")), any()) } returns null

        val worker = TestListenableWorkerBuilder<RevivalWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return RevivalWorker(appContext, workerParameters, contactDao, eventDao, pendingMessageDao, sentMessageDao, geminiClient, prefs)
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { geminiClient.generate(any()) }
        coVerify(exactly = 0) { eventDao.upsert(any()) }
        coVerify(exactly = 0) { pendingMessageDao.insert(any()) }
        coVerify(exactly = 0) { contactDao.updateLastRevivalAttempt(any(), any()) }
        verify(exactly = 0) { DailyScheduler.scheduleExactSend(any(), any()) }
    }

    private fun pendingRevival(status: String): PendingMessageEntity {
        return PendingMessageEntity(
            id = "p1",
            contactId = "c1",
            eventId = RevivalCadencePolicy.eventId("c1"),
            shortVariant = "Hi",
            standardVariant = "Hi",
            longVariant = "Hi",
            formalVariant = "Hi",
            funnyVariant = "Hi",
            emotionalVariant = "Hi",
            channel = MessageChannel.SMS.raw,
            scheduledForMs = System.currentTimeMillis(),
            approvalMode = "SMART_APPROVE",
            status = status,
            scheduledYear = 2026,
        )
    }
}
