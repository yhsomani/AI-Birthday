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
import com.example.core.db.dao.GiftHistoryDao
import com.example.core.db.dao.MemoryNoteDao
import com.example.core.db.dao.PendingMessageDao
import com.example.core.db.dao.SentMessageDao
import com.example.core.db.dao.StyleProfileDao
import com.example.core.db.entities.ContactEntity
import com.example.core.db.entities.EventEntity
import com.example.core.db.entities.PendingMessageEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.RateLimiter
import com.example.core.gemini.ResponseParser
import com.example.core.gemini.MessageVariants
import com.example.core.prefs.SecurePrefs
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
class MessageGenerationWorkerTest {

    private lateinit var context: Context
    private val contactDao: ContactDao = mockk(relaxed = true)
    private val eventDao: EventDao = mockk(relaxed = true)
    private val pendingMessageDao: PendingMessageDao = mockk(relaxed = true)
    private val sentMessageDao: SentMessageDao = mockk(relaxed = true)
    private val styleProfileDao: StyleProfileDao = mockk(relaxed = true)
    private val memoryNoteDao: MemoryNoteDao = mockk(relaxed = true)
    private val giftHistoryDao: GiftHistoryDao = mockk(relaxed = true)
    private val geminiClient: GeminiClient = mockk(relaxed = true)
    private val prefs: SecurePrefs = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(FirebaseAuth::class)
        mockkObject(RateLimiter)
        mockkObject(ResponseParser)
        mockkObject(DailyScheduler)
        mockkObject(NotificationHelper)

        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser

        coEvery { RateLimiter.waitIfNeeded() } returns Unit
        every { DailyScheduler.scheduleExactSend(any(), any()) } just Runs
        every { NotificationHelper.showApprovalNotification(any(), any(), any(), any(), any()) } just Runs
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
        every { NotificationHelper.showSystemAlert(any(), any(), any()) } just Runs
        every { prefs.isAiWishGenerationEnabled() } returns true
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork with valid key and event generates message`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(id = "c1", name = "John", relationshipType = "FRIEND", preferredChannel = "SMS", automationMode = "FULLY_AUTO")
        val variants = MessageVariants("sh", "std", "lg", "fr", "fn", "em", "standard")

        every { prefs.getGeminiApiKey() } returns "mock_key"
        coEvery { eventDao.getEventsBefore(any()) } returns listOf(event)
        coEvery { pendingMessageDao.getPendingMessage("c1", "e1", any()) } returns null
        coEvery { contactDao.getById("c1") } returns contact
        coEvery { styleProfileDao.get() } returns null
        coEvery { sentMessageDao.getByContact("c1") } returns emptyList()
        coEvery { memoryNoteDao.getByContact("c1") } returns emptyList()
        coEvery { giftHistoryDao.getByContact("c1") } returns emptyList()
        coEvery { geminiClient.generate(any()) } returns "mock_response"
        every { ResponseParser.parseMessageVariants(any(), any()) } returns variants
        every { prefs.getGlobalAutomationMode() } returns "MANUAL"

        val worker = TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext, workerParameters,
                        contactDao, eventDao, pendingMessageDao, sentMessageDao, styleProfileDao,
                        memoryNoteDao, giftHistoryDao, geminiClient, prefs
                    )
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(any()) }
        verify { DailyScheduler.scheduleExactSend(any(), any()) }
    }

    @Test
    fun `doWork looks ahead seven days for weekly AI draft preparation`() = runTest {
        every { prefs.getGeminiApiKey() } returns "mock_key"
        coEvery { eventDao.getEventsBefore(any()) } returns emptyList()

        val beforeMs = System.currentTimeMillis()
        val worker = TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext, workerParameters,
                        contactDao, eventDao, pendingMessageDao, sentMessageDao, styleProfileDao,
                        memoryNoteDao, giftHistoryDao, geminiClient, prefs
                    )
                }
            })
            .build()

        val result = worker.doWork()
        val afterMs = System.currentTimeMillis()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify {
            eventDao.getEventsBefore(match { cutoffMs ->
                cutoffMs in (beforeMs + 7L * 24L * 60L * 60L * 1000L)..(afterMs + 7L * 24L * 60L * 60L * 1000L)
            })
        }
    }

    @Test
    fun `doWork schedules SMART_APPROVE messages for automatic due-time dispatch`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(id = "c1", name = "John", relationshipType = "FRIEND", preferredChannel = "SMS", automationMode = "DEFAULT")
        val variants = MessageVariants("sh", "std", "lg", "fr", "fn", "em", "standard")
        val pendingSlot = slot<PendingMessageEntity>()

        every { prefs.getGeminiApiKey() } returns "mock_key"
        every { prefs.getGlobalAutomationMode() } returns "SMART_APPROVE"
        coEvery { eventDao.getEventsBefore(any()) } returns listOf(event)
        coEvery { pendingMessageDao.getPendingMessage("c1", "e1", any()) } returns null
        coEvery { contactDao.getById("c1") } returns contact
        coEvery { styleProfileDao.get() } returns null
        coEvery { sentMessageDao.getByContact("c1") } returns emptyList()
        coEvery { memoryNoteDao.getByContact("c1") } returns emptyList()
        coEvery { giftHistoryDao.getByContact("c1") } returns emptyList()
        coEvery { geminiClient.generate(any()) } returns "mock_response"
        every { ResponseParser.parseMessageVariants(any(), any()) } returns variants

        val worker = TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext, workerParameters,
                        contactDao, eventDao, pendingMessageDao, sentMessageDao, styleProfileDao,
                        memoryNoteDao, giftHistoryDao, geminiClient, prefs
                    )
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("SMART_APPROVE", pendingSlot.captured.approvalMode)
        assertEquals("PENDING", pendingSlot.captured.status)
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify { NotificationHelper.showApprovalNotification(any(), contact, event, variants, pendingSlot.captured.id) }
    }

    @Test
    fun `doWork downgrades fallback fully auto draft to smart approve`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(id = "c1", name = "John", relationshipType = "FRIEND", preferredChannel = "SMS", automationMode = "FULLY_AUTO")
        val variants = MessageVariants.fromFallback("Wishing you a very happy birthday! Hope you have a wonderful day!")
        val pendingSlot = slot<PendingMessageEntity>()

        every { prefs.getGeminiApiKey() } returns "mock_key"
        every { prefs.getGlobalAutomationMode() } returns "FULLY_AUTO"
        coEvery { eventDao.getEventsBefore(any()) } returns listOf(event)
        coEvery { pendingMessageDao.getPendingMessage("c1", "e1", any()) } returns null
        coEvery { contactDao.getById("c1") } returns contact
        coEvery { styleProfileDao.get() } returns null
        coEvery { sentMessageDao.getByContact("c1") } returns emptyList()
        coEvery { memoryNoteDao.getByContact("c1") } returns emptyList()
        coEvery { giftHistoryDao.getByContact("c1") } returns emptyList()
        coEvery { geminiClient.generate(any()) } returns "mock_response"
        every { ResponseParser.parseMessageVariants(any(), any()) } returns variants

        val worker = TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext, workerParameters,
                        contactDao, eventDao, pendingMessageDao, sentMessageDao, styleProfileDao,
                        memoryNoteDao, giftHistoryDao, geminiClient, prefs
                    )
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify { pendingMessageDao.insert(capture(pendingSlot)) }
        assertEquals("SMART_APPROVE", pendingSlot.captured.approvalMode)
        assertEquals("PENDING", pendingSlot.captured.status)
        assertEquals(35, pendingSlot.captured.qualityScore)
        verify { DailyScheduler.scheduleExactSend(any(), pendingSlot.captured.id) }
        verify { NotificationHelper.showApprovalNotification(any(), contact, event, variants, pendingSlot.captured.id) }
    }

    @Test
    fun `doWork skips automatic generation when contact opted out`() = runTest {
        val event = EventEntity(id = "e1", contactId = "c1", type = "BIRTHDAY", label = "Test", dayOfMonth = 1, month = 1, nextOccurrenceMs = 1000L)
        val contact = ContactEntity(
            id = "c1",
            name = "John",
            relationshipType = "FRIEND",
            preferredChannel = "SMS",
            automationMode = "FULLY_AUTO",
            skipAutoWish = true,
        )

        every { prefs.getGeminiApiKey() } returns "mock_key"
        coEvery { eventDao.getEventsBefore(any()) } returns listOf(event)
        coEvery { pendingMessageDao.getPendingMessage("c1", "e1", any()) } returns null
        coEvery { contactDao.getById("c1") } returns contact

        val worker = TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext, workerParameters,
                        contactDao, eventDao, pendingMessageDao, sentMessageDao, styleProfileDao,
                        memoryNoteDao, giftHistoryDao, geminiClient, prefs
                    )
                }
            })
            .build()

        val result = worker.doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { geminiClient.generate(any()) }
        coVerify(exactly = 0) { pendingMessageDao.insert(any()) }
    }
}
