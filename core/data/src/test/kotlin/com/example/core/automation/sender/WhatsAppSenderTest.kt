package com.example.core.automation.sender

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.core.accessibility.WhatsAppAccessibilityService
import com.example.core.accessibility.WhatsAppSendFailureReason
import com.example.core.accessibility.WhatsAppSendResult
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class WhatsAppSenderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WhatsAppAccessibilityService.instance = null
        WhatsAppAccessibilityService.pendingQueue.clear()
    }

    @After
    fun tearDown() {
        WhatsAppAccessibilityService.instance = null
        WhatsAppAccessibilityService.pendingQueue.clear()
    }

    @Test
    fun sendWithResult_returnsServiceDisabledWhenAccessibilityServiceIsNotConnected() = runTest {
        val result = WhatsAppSender(context).sendWithResult(
            phoneNumber = "+1 (555) 123-4567",
            message = "Selected",
            eventId = "event_1",
        )

        assertEquals(
            WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED),
            result,
        )
    }

    @Test
    fun send_keepsBooleanCompatibilityForUnavailableAccessibilityService() = runTest {
        val sent = WhatsAppSender(context).send(
            phoneNumber = "+1 (555) 123-4567",
            message = "Selected",
            eventId = "event_1",
        )

        assertFalse(sent)
    }

    @Test
    fun sendWithResult_rejectsPhoneNumbersWithNoDigitsBeforeEnqueueing() = runTest {
        val service = mockk<WhatsAppAccessibilityService>()
        every { service.enqueueSend(any()) } just Runs
        every { service.cancelSend(any(), any()) } just Runs

        val result = WhatsAppSender(
            context = context,
            serviceProvider = { service },
        ).sendWithResult(
            phoneNumber = " ext. -- ",
            message = "Selected",
            eventId = "event_invalid",
        )

        assertEquals(
            WhatsAppSendResult.Failed(WhatsAppSendFailureReason.INVALID_PHONE_NUMBER),
            result,
        )
        verify(exactly = 0) { service.enqueueSend(any()) }
        verify(exactly = 0) { service.cancelSend(any(), any()) }
    }

    @Test
    fun accessibilityServiceFailQueuedJobsFailsAndClearsQueuedCallbacks() {
        val results = mutableListOf<WhatsAppSendResult>()
        WhatsAppAccessibilityService.pendingQueue.add(
            WhatsAppAccessibilityService.WhatsAppSendJob(
                phoneNumber = "15551234567",
                message = "One",
                eventId = "event_1",
                onComplete = { results += it },
            )
        )
        WhatsAppAccessibilityService.pendingQueue.add(
            WhatsAppAccessibilityService.WhatsAppSendJob(
                phoneNumber = "15557654321",
                message = "Two",
                eventId = "event_2",
                onComplete = { results += it },
            )
        )

        WhatsAppAccessibilityService.failQueuedJobs(WhatsAppSendFailureReason.SERVICE_DISABLED)

        assertEquals(0, WhatsAppAccessibilityService.pendingQueue.size)
        assertEquals(
            listOf(
                WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED),
                WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED),
            ),
            results,
        )
    }

    @Test
    fun accessibilityServiceRejectsEnqueueWhenServiceReferenceIsStale() {
        val service = WhatsAppAccessibilityService()
        val results = mutableListOf<WhatsAppSendResult>()

        service.enqueueSend(
            WhatsAppAccessibilityService.WhatsAppSendJob(
                phoneNumber = "15551234567",
                message = "Selected",
                eventId = "event_stale",
                onComplete = { results += it },
            )
        )

        assertEquals(0, WhatsAppAccessibilityService.pendingQueue.size)
        assertEquals(
            listOf(WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SERVICE_DISABLED)),
            results,
        )
    }

    @Test
    fun sendWithResult_returnsSentWhenAccessibilityServiceCompletesCallback() = runTest {
        val service = mockk<WhatsAppAccessibilityService>()
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        every { service.enqueueSend(any()) } answers {
            firstArg<WhatsAppAccessibilityService.WhatsAppSendJob>().onComplete(WhatsAppSendResult.Sent)
        }
        every { service.cancelSend(any(), any()) } just Runs

        val resultDeferred = async {
            WhatsAppSender(
                context = context,
                serviceProvider = { service },
                callbackTimeoutMs = 1_000L,
                mainDispatcher = mainDispatcher,
            ).sendWithResult(
                phoneNumber = "+1 (555) 123-4567",
                message = "Selected",
                eventId = "event_1",
            )
        }
        runCurrent()
        val result = resultDeferred.await()

        assertEquals(WhatsAppSendResult.Sent, result)
        verify {
            service.enqueueSend(match {
                it.phoneNumber == "15551234567" &&
                    it.message == "Selected" &&
                    it.eventId == "event_1"
            })
        }
        verify(exactly = 0) { service.cancelSend(any(), any()) }
    }

    @Test
    fun sendWithResult_timesOutAndCancelsWhenAccessibilityServiceNeverCompletesCallback() = runTest {
        val service = mockk<WhatsAppAccessibilityService>()
        val mainDispatcher = StandardTestDispatcher(testScheduler)
        every { service.enqueueSend(any()) } just Runs
        every { service.cancelSend(any(), any()) } just Runs

        val resultDeferred = async {
            WhatsAppSender(
                context = context,
                serviceProvider = { service },
                callbackTimeoutMs = 1L,
                mainDispatcher = mainDispatcher,
            ).sendWithResult(
                phoneNumber = "+1 (555) 123-4567",
                message = "Selected",
                eventId = "event_1",
            )
        }
        runCurrent()
        advanceTimeBy(1L)
        runCurrent()
        val result = resultDeferred.await()

        assertEquals(
            WhatsAppSendResult.Failed(WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT),
            result,
        )
        verify {
            service.cancelSend(
                eventId = "event_1",
                reason = WhatsAppSendFailureReason.SENDER_CALLBACK_TIMEOUT,
            )
        }
    }
}
