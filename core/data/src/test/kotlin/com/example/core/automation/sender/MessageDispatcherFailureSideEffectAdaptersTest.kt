package com.example.core.automation.sender

import com.example.core.resilience.HealthMonitor
import com.example.domain.model.MessageChannel
import com.example.domain.model.common.MessageDraftId
import com.example.domain.model.dispatch.DispatchAttemptResult
import com.example.domain.model.dispatch.MessageDispatchFailureHealthReport
import com.example.domain.model.dispatch.MessageDispatchFailureSideEffects
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MessageDispatcherFailureSideEffectAdaptersTest {
    @Before
    fun setUp() {
        HealthMonitor.clearForTests()
    }

    @After
    fun tearDown() {
        HealthMonitor.clearForTests()
    }

    @Test
    fun messageDispatchFailureSideEffects_buildsHealthReportForFinalFailure() {
        val sideEffects = messageDispatchFailureSideEffects(
            messageId = MessageDraftId("pending_1"),
            preferredChannel = MessageChannel.SMS,
            failure = ProviderDispatchFailure(
                result = DispatchAttemptResult.FAILED_FINAL,
                errorType = DispatchProviderRetryPolicy.ERROR_NO_DELIVERY_ROUTE,
                errorCode = null,
                redactedErrorMessage = "All automatic delivery routes failed.",
                nextRetryDelayMs = null,
            ),
        )

        assertEquals(
            MessageDispatchFailureSideEffects(
                healthReport = MessageDispatchFailureHealthReport(
                    context = "MessageDispatcher.dispatch",
                    errorMessage = "Failed to send pending_1 via SMS: NO_DELIVERY_ROUTE",
                ),
            ),
            sideEffects,
        )
    }

    @Test
    fun messageDispatchFailureSideEffects_skipsDeadLetterForRetryableFailure() {
        val sideEffects = messageDispatchFailureSideEffects(
            messageId = MessageDraftId("pending_1"),
            preferredChannel = MessageChannel.SMS,
            failure = ProviderDispatchFailure(
                result = DispatchAttemptResult.FAILED_RETRYABLE,
                errorType = DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
                errorCode = "RuntimeException",
                redactedErrorMessage = "SMS provider failed before delivery confirmation; retry is allowed.",
                nextRetryDelayMs = DispatchProviderRetryPolicy.DEFAULT_RETRY_DELAY_MS,
            ),
        )

        assertEquals(
            MessageDispatchFailureHealthReport(
                context = "MessageDispatcher.dispatch",
                errorMessage = "Failed to send pending_1 via SMS: SMS_TRANSIENT_PROVIDER_FAILURE",
            ),
            sideEffects.healthReport,
        )
    }

    @Test
    fun recordMessageDispatchFailureSideEffects_recordsHealthReportForFinalFailure() {
        recordMessageDispatchFailureSideEffects(
            MessageDispatchFailureSideEffects(
                healthReport = MessageDispatchFailureHealthReport(
                    context = "MessageDispatcher.dispatch",
                    errorMessage = "Failed to send pending_1 via SMS: NO_DELIVERY_ROUTE",
                ),
            )
        )

        assertTrue(
            HealthMonitor.snapshot().recentErrors.any {
                it.contains("[MessageDispatcher.dispatch] Failed to send pending_1 via SMS: NO_DELIVERY_ROUTE")
            }
        )
    }

    @Test
    fun recordMessageDispatchFailureSideEffects_recordsHealthReportWithoutDeadLetter() {
        recordMessageDispatchFailureSideEffects(
            MessageDispatchFailureSideEffects(
                healthReport = MessageDispatchFailureHealthReport(
                    context = "MessageDispatcher.dispatch",
                    errorMessage = "Failed to send pending_2 via SMS: SMS_TRANSIENT_PROVIDER_FAILURE",
                ),
            )
        )

        assertTrue(
            HealthMonitor.snapshot().recentErrors.any {
                it.contains("[MessageDispatcher.dispatch] Failed to send pending_2 via SMS: SMS_TRANSIENT_PROVIDER_FAILURE")
            }
        )
    }
}
