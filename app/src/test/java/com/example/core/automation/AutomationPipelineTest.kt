package com.example.core.automation

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.core.automation.sender.MessageDispatcher
import com.example.core.automation.workers.EventDiscoveryWorker
import com.example.core.automation.workers.MessageDispatchWorker
import com.example.core.automation.workers.MessageGenerationWorker
import com.example.core.db.AppDatabase
import com.example.core.db.DatabaseKeyDerivation
import com.example.core.db.entities.ContactEntity
import com.example.core.prefs.SecurePrefs
import com.example.data.repository.ContactRepositoryImpl
import com.example.data.repository.EventRepositoryImpl
import com.example.data.repository.GiftHistoryRepositoryImpl
import com.example.data.repository.MemoryNoteRepositoryImpl
import com.example.data.repository.MessageRepositoryImpl
import com.example.data.repository.StyleProfileRepositoryImpl
import com.example.domain.service.AiService
import com.example.domain.service.MessageVariantsResult
import com.example.domain.service.NotificationService
import com.example.domain.service.PreferencesRepository
import com.example.domain.service.SchedulerService
import com.example.domain.model.MessageChannel
import com.example.domain.usecase.GenerateMessageUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
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
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class AutomationPipelineTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private val aiService: AiService = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val schedulerService: SchedulerService = mockk(relaxed = true)
    private val notificationService: NotificationService = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkObject(DatabaseKeyDerivation)
        every { DatabaseKeyDerivation.deriveKey(any()) } returns ByteArray(32)
        every { DatabaseKeyDerivation.warmUpAsync(any()) } returns CompletableDeferred(ByteArray(32))

        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()

        mockkStatic(FirebaseAuth::class)
        mockkConstructor(MessageDispatcher::class)
        mockkConstructor(SecurePrefs::class)

        val mockAuth = mockk<FirebaseAuth>()
        val mockUser = mockk<FirebaseUser>()
        every { FirebaseAuth.getInstance() } returns mockAuth
        every { mockAuth.currentUser } returns mockUser

        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGeminiApiKey() } returns "mock_key"
        every { preferencesRepository.getQuietHoursStart() } returns 0
        every { preferencesRepository.getQuietHoursEnd() } returns 0
        every { preferencesRepository.getBlackoutDates() } returns "[]"
        every { preferencesRepository.getChannelBlackout() } returns "[]"
        every { preferencesRepository.getSenderEmail() } returns ""
        every { preferencesRepository.getSenderEmailPassword() } returns ""
        every { anyConstructed<SecurePrefs>().getQuietHoursStart() } returns 0
        every { anyConstructed<SecurePrefs>().getQuietHoursEnd() } returns 0
        every { anyConstructed<SecurePrefs>().getBlackoutDates() } returns "[]"
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
            primaryPhone = "+15551234567",
            preferredChannel = MessageChannel.SMS.raw,
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
        val draft = "Happy birthday Amit, hope this year brings more cricket nights and relaxed family time."
        val variants = MessageVariantsResult(draft, draft, draft, draft, draft, draft, "standard")
        coEvery { aiService.generateMessage(any(), any(), any(), any(), any(), any()) } returns variants

        val genWorker = TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext, workerParameters,
                        EventRepositoryImpl(db.eventDao()),
                        generateMessageUseCase(),
                        preferencesRepository
                    )
                }
            })
            .build()
        genWorker.doWork()

        val pending = db.pendingMessageDao().getByEventId(event.id)
        assertNotNull(pending)
        assertEquals("APPROVED", pending!!.status)
        db.pendingMessageDao().insert(pending.copy(scheduledForMs = 0L))

        // 4. Dispatch message
        val dispatchWorker = TestListenableWorkerBuilder<MessageDispatchWorker>(context)
            .setInputData(workDataOf("event_id" to event.id))
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker {
                    return MessageDispatchWorker(
                        appContext,
                        workerParameters,
                        db.pendingMessageDao(),
                        db.sentMessageDao(),
                        db.contactDao(),
                        db.eventDao(),
                        preferencesRepository
                    )
                }
            })
            .build()
        dispatchWorker.doWork()

        coVerify { anyConstructed<MessageDispatcher>().dispatch(match { it.id == pending.id }, any()) }
    }

    private fun generateMessageUseCase(): GenerateMessageUseCase {
        return GenerateMessageUseCase(
            ContactRepositoryImpl(db.contactDao()),
            EventRepositoryImpl(db.eventDao()),
            MessageRepositoryImpl(db.pendingMessageDao(), db.sentMessageDao()),
            StyleProfileRepositoryImpl(db.styleProfileDao()),
            MemoryNoteRepositoryImpl(db.memoryNoteDao()),
            GiftHistoryRepositoryImpl(db.giftHistoryDao()),
            aiService,
            preferencesRepository,
            schedulerService,
            notificationService
        )
    }
}
