package com.example.core.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.automation.scheduler.DailyScheduler
import com.example.core.automation.sender.MessageDispatcher
import com.example.core.automation.workers.EventDiscoveryWorker
import com.example.core.automation.workers.MessageDispatchWorker
import com.example.core.automation.workers.MessageGenerationWorker
import com.example.core.db.AppDatabase
import com.example.core.db.entities.ContactEntity
import com.example.core.gemini.GeminiClient
import com.example.core.gemini.MessageVariants
import com.example.core.gemini.RateLimiter
import com.example.core.gemini.ResponseParser
import com.example.core.prefs.SecurePrefs
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutomationPipelineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val geminiClient: GeminiClient = mockk(relaxed = true)
    private val prefs: SecurePrefs = mockk(relaxed = true)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        mockkStatic(FirebaseAuth::class)
        mockkObject(RateLimiter)
        mockkObject(ResponseParser)
        mockkObject(DailyScheduler)
        mockkObject(NotificationHelper)
        mockkConstructor(MessageDispatcher::class)

        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser

        coEvery { RateLimiter.waitIfNeeded() } returns Unit
        every { DailyScheduler.scheduleExactSend(any(), any()) } just Runs
        every { NotificationHelper.showApprovalNotification(any(), any(), any(), any(), any()) } just Runs
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
        every { prefs.isAiWishGenerationEnabled() } returns true
        coEvery { anyConstructed<MessageDispatcher>().dispatch(any(), any()) } returns Unit
    }

    @After
    fun tearDown() {
        db.close()
        unmockkAll()
    }

    @Test
    fun `run E2E pipeline for contact birthday`() = runTest {
        // 1. Insert contact
        val tomorrowCal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
        }
        val contact = ContactEntity(
            id = "c1",
            name = "Amit",
            birthdayDay = tomorrowCal.get(java.util.Calendar.DAY_OF_MONTH),
            birthdayMonth = tomorrowCal.get(java.util.Calendar.MONTH) + 1,
            birthdayYear = 1990,
            relationshipType = "FRIEND",
            preferredChannel = "SMS",
            automationMode = "FULLY_AUTO"
        )
        db.contactDao().upsert(contact)

        // 2. Discover event
        val discoveryWorker = TestListenableWorkerBuilder<EventDiscoveryWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
                    return EventDiscoveryWorker(appContext, workerParameters, db.contactDao(), db.eventDao())
                }
            })
            .build()
        discoveryWorker.doWork()

        val event = db.eventDao().getAll().first().first { it.contactId == "c1" }
        assertEquals("BIRTHDAY", event.type)

        // 3. Generate message
        val variants = MessageVariants("sh", "std", "lg", "fr", "fn", "em", "standard")
        every { prefs.getGeminiApiKey() } returns "mock_key"
        every { ResponseParser.parseMessageVariants(any(), any()) } returns variants
        every { prefs.getGlobalAutomationMode() } returns "MANUAL"

        val genWorker = TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext, workerParameters,
                        db.contactDao(), db.eventDao(), db.pendingMessageDao(), db.sentMessageDao(), db.styleProfileDao(),
                        db.memoryNoteDao(), db.giftHistoryDao(), geminiClient, prefs
                    )
                }
            })
            .build()
        genWorker.doWork()

        val pending = db.pendingMessageDao().getByEventId(event.id)
        assertNotNull(pending)
        assertEquals("APPROVED", pending!!.status)

        // 4. Dispatch message
        val dispatchWorker = TestListenableWorkerBuilder<MessageDispatchWorker>(context)
            .setInputData(workDataOf("event_id" to event.id))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
                    return MessageDispatchWorker(appContext, workerParameters, db.pendingMessageDao(), db.sentMessageDao(), db.contactDao())
                }
            })
            .build()
        dispatchWorker.doWork()

        coVerify { anyConstructed<MessageDispatcher>().dispatch(match { it.id == pending.id }, any()) }
    }
}
