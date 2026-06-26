package com.example.core.automation.workers

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.example.core.automation.notifications.NotificationHelper
import com.example.core.db.entities.EventEntity
import com.example.domain.model.ApprovalMode
import com.example.domain.repository.EventRepository
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.GenerateMessageUseCase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.Runs
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
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class MessageGenerationWorkerTest {

    private lateinit var context: Context
    private val eventRepository: EventRepository = mockk(relaxed = true)
    private val generateMessageUseCase: GenerateMessageUseCase = mockk(relaxed = true)
    private val preferencesRepository: PreferencesRepository = mockk(relaxed = true)
    private val firebaseAuth: FirebaseAuth = mockk()
    private val firebaseUser: FirebaseUser = mockk()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        mockkStatic(FirebaseAuth::class)
        mockkObject(NotificationHelper)

        every { FirebaseAuth.getInstance() } returns firebaseAuth
        every { firebaseAuth.currentUser } returns firebaseUser
        every { preferencesRepository.isAiWishGenerationEnabled() } returns true
        every { preferencesRepository.getGeminiApiKey() } returns "mock_key"
        every { NotificationHelper.showSetupNotification(any(), any(), any()) } just Runs
        coEvery { eventRepository.getEventsBefore(any()) } returns emptyList()
        coEvery {
            generateMessageUseCase(any<GenerateMessageUseCase.Request>())
        } returns GenerateMessageUseCase.GenerationOutcome.Generated("pending_1", ApprovalMode.SMART_APPROVE, 0)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `doWork skips generation when AI wishes are disabled`() = runTest {
        every { preferencesRepository.isAiWishGenerationEnabled() } returns false

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 0) { eventRepository.getEventsBefore(any()) }
        coVerify(exactly = 0) { generateMessageUseCase(any<GenerateMessageUseCase.Request>()) }
    }

    @Test
    fun `doWork fails with setup notification when no AI provider is configured`() = runTest {
        every { preferencesRepository.getGeminiApiKey() } returns ""
        every { firebaseAuth.currentUser } returns null

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        verify { NotificationHelper.showSetupNotification(any(), any(), any()) }
        coVerify(exactly = 0) { eventRepository.getEventsBefore(any()) }
        coVerify(exactly = 0) { generateMessageUseCase(any<GenerateMessageUseCase.Request>()) }
    }

    @Test
    fun `doWork proceeds with Firebase auth when API key is missing`() = runTest {
        every { preferencesRepository.getGeminiApiKey() } returns ""
        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event("e1"))

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify {
            generateMessageUseCase(
                GenerateMessageUseCase.Request(
                    eventId = "e1",
                    regenerateFailedOccurrence = true
                )
            )
        }
    }

    @Test
    fun `doWork looks ahead seven days and delegates each event to use case`() = runTest {
        val events = listOf(event("e1"), event("e2", contactId = "c2"))
        coEvery { eventRepository.getEventsBefore(any()) } returns events

        val beforeMs = System.currentTimeMillis()
        val result = buildWorker().doWork()
        val afterMs = System.currentTimeMillis()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify {
            eventRepository.getEventsBefore(match { cutoffMs ->
                cutoffMs in (beforeMs + SEVEN_DAYS_MS)..(afterMs + SEVEN_DAYS_MS)
            })
        }
        coVerify {
            generateMessageUseCase(
                GenerateMessageUseCase.Request(
                    eventId = "e1",
                    regenerateFailedOccurrence = true
                )
            )
        }
        coVerify {
            generateMessageUseCase(
                GenerateMessageUseCase.Request(
                    eventId = "e2",
                    regenerateFailedOccurrence = true
                )
            )
        }
    }

    @Test
    fun `doWork continues across non-generated use case outcomes`() = runTest {
        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(
            event("exists"),
            event("missing_contact"),
            event("missing_event"),
            event("disabled")
        )
        coEvery {
            generateMessageUseCase(GenerateMessageUseCase.Request("exists", regenerateFailedOccurrence = true))
        } returns GenerateMessageUseCase.GenerationOutcome.AlreadyExists
        coEvery {
            generateMessageUseCase(GenerateMessageUseCase.Request("missing_contact", regenerateFailedOccurrence = true))
        } returns GenerateMessageUseCase.GenerationOutcome.ContactNotFound
        coEvery {
            generateMessageUseCase(GenerateMessageUseCase.Request("missing_event", regenerateFailedOccurrence = true))
        } returns GenerateMessageUseCase.GenerationOutcome.EventNotFound
        coEvery {
            generateMessageUseCase(GenerateMessageUseCase.Request("disabled", regenerateFailedOccurrence = true))
        } returns GenerateMessageUseCase.GenerationOutcome.AiDisabled

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify(exactly = 4) { generateMessageUseCase(any<GenerateMessageUseCase.Request>()) }
    }

    @Test
    fun `doWork keeps processing remaining events when one generation fails`() = runTest {
        coEvery { eventRepository.getEventsBefore(any()) } returns listOf(event("e1"), event("e2"))
        coEvery {
            generateMessageUseCase(GenerateMessageUseCase.Request("e1", regenerateFailedOccurrence = true))
        } throws RuntimeException("generation failed")
        coEvery {
            generateMessageUseCase(GenerateMessageUseCase.Request("e2", regenerateFailedOccurrence = true))
        } returns GenerateMessageUseCase.GenerationOutcome.Generated("pending_2", ApprovalMode.SMART_APPROVE, 0)

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        coVerify {
            generateMessageUseCase(GenerateMessageUseCase.Request("e1", regenerateFailedOccurrence = true))
        }
        coVerify {
            generateMessageUseCase(GenerateMessageUseCase.Request("e2", regenerateFailedOccurrence = true))
        }
    }

    @Test
    fun `doWork retries when event lookup fails`() = runTest {
        coEvery { eventRepository.getEventsBefore(any()) } throws RuntimeException("database unavailable")

        val result = buildWorker().doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    private fun buildWorker(): MessageGenerationWorker {
        return TestListenableWorkerBuilder<MessageGenerationWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters
                ): ListenableWorker {
                    return MessageGenerationWorker(
                        appContext,
                        workerParameters,
                        eventRepository,
                        generateMessageUseCase,
                        preferencesRepository
                    )
                }
            })
            .build()
    }

    private fun event(id: String, contactId: String = "c1"): EventEntity {
        return EventEntity(
            id = id,
            contactId = contactId,
            type = "BIRTHDAY",
            label = "Birthday",
            dayOfMonth = 1,
            month = 1,
            nextOccurrenceMs = 1000L
        )
    }

    private companion object {
        const val SEVEN_DAYS_MS = 7L * 24L * 60L * 60L * 1000L
    }
}
