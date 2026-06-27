package com.example.core.automation.sender

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDispatcherRouteOutcomeAdaptersTest {

    @Test
    fun messageDispatchRouteLoopState_startsWithoutSuccessOrSelectedFailure() {
        val state = messageDispatchRouteLoopState()

        assertFalse(state.success)
        assertFalse(state.successfulSentMessageInserted)
        assertNull(state.providerFailureSelection.selectedFailure)
    }

    @Test
    fun toMessageDispatchRouteOutcome_mapsWhatsAppResult() {
        val outcome = WhatsAppDispatchRouteResult(
            sent = false,
            failure = DispatchProviderRetryPolicy.whatsAppAutomationUnavailable(),
        ).toMessageDispatchRouteOutcome()

        assertFalse(outcome.sent)
        assertFalse(outcome.successfulSentMessageInserted)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE,
            outcome.failure?.errorType,
        )
    }

    @Test
    fun toMessageDispatchRouteOutcome_mapsSmsResultWithInsertedSentMessage() {
        val outcome = SmsRouteDispatchOutcome(
            sent = true,
            successfulSentMessageInserted = true,
            failure = null,
        ).toMessageDispatchRouteOutcome()

        assertTrue(outcome.sent)
        assertTrue(outcome.successfulSentMessageInserted)
        assertNull(outcome.failure)
    }

    @Test
    fun toMessageDispatchRouteOutcome_mapsEmailResult() {
        val failure = DispatchProviderRetryPolicy.emailProviderException(
            javax.mail.MessagingException("smtp timeout")
        )
        val outcome = EmailDispatchRouteResult(
            sent = false,
            failure = failure,
            cause = null,
        ).toMessageDispatchRouteOutcome()

        assertFalse(outcome.sent)
        assertFalse(outcome.successfulSentMessageInserted)
        assertEquals(failure, outcome.failure)
    }

    @Test
    fun applyRouteOutcome_appliesSuccessAndPreservesInsertedSentMessageFlag() {
        val state = messageDispatchRouteLoopState()
            .applyRouteOutcome(
                MessageDispatchRouteOutcome(
                    sent = false,
                    successfulSentMessageInserted = true,
                    failure = DispatchProviderRetryPolicy.whatsAppAutomationUnavailable(),
                )
            )
            .applyRouteOutcome(
                MessageDispatchRouteOutcome(
                    sent = true,
                    successfulSentMessageInserted = false,
                    failure = null,
                )
            )

        assertTrue(state.success)
        assertTrue(state.successfulSentMessageInserted)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE,
            state.providerFailureSelection.selectedFailure?.errorType,
        )
    }

    @Test
    fun applyRouteOutcome_selectsPreferredProviderFailureAcrossRoutes() {
        val state = messageDispatchRouteLoopState()
            .applyRouteOutcome(
                MessageDispatchRouteOutcome(
                    sent = false,
                    successfulSentMessageInserted = false,
                    failure = DispatchProviderRetryPolicy.whatsAppAutomationUnavailable(),
                )
            )
            .applyRouteOutcome(
                MessageDispatchRouteOutcome(
                    sent = false,
                    successfulSentMessageInserted = false,
                    failure = DispatchProviderRetryPolicy.smsProviderException(
                        RuntimeException("radio unavailable")
                    ),
                )
            )

        assertFalse(state.success)
        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            state.providerFailureSelection.selectedFailure?.errorType,
        )
    }
}
