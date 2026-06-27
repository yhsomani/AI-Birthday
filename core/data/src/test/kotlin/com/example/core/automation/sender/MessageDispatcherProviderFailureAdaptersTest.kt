package com.example.core.automation.sender

import com.example.core.accessibility.WhatsAppSendFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessageDispatcherProviderFailureAdaptersTest {

    @Test
    fun messageDispatchProviderFailureSelection_startsWithoutSelectedFailure() {
        assertNull(messageDispatchProviderFailureSelection().selectedFailure)
    }

    @Test
    fun select_usesFirstCandidateWhenNoFailureWasSelected() {
        val selected = messageDispatchProviderFailureSelection()
            .select(DispatchProviderRetryPolicy.whatsAppAutomationUnavailable())

        assertEquals(
            DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE,
            selected.selectedFailure?.errorType,
        )
    }

    @Test
    fun select_prefersRetryableCandidateOverFinalCurrentFailure() {
        val selected = messageDispatchProviderFailureSelection()
            .select(DispatchProviderRetryPolicy.whatsAppAutomationUnavailable())
            .select(DispatchProviderRetryPolicy.smsProviderException(RuntimeException("radio unavailable")))

        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            selected.selectedFailure?.errorType,
        )
    }

    @Test
    fun select_keepsRetryableCurrentFailureWhenFinalCandidateArrivesLater() {
        val selected = messageDispatchProviderFailureSelection()
            .select(DispatchProviderRetryPolicy.smsProviderException(RuntimeException("radio unavailable")))
            .select(DispatchProviderRetryPolicy.whatsAppAutomationUnavailable())

        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE,
            selected.selectedFailure?.errorType,
        )
    }

    @Test
    fun select_replacesCurrentFailureWhenBothFailuresHaveSameRetryability() {
        val selected = messageDispatchProviderFailureSelection()
            .select(DispatchProviderRetryPolicy.whatsAppAutomationUnavailable())
            .select(DispatchProviderRetryPolicy.smsPermissionDenied())

        assertEquals(
            DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED,
            selected.selectedFailure?.errorType,
        )
    }

    @Test
    fun failureOrDispatchFailure_returnsDefaultFailureWhenNoProviderFailureWasSelected() {
        val failure = messageDispatchProviderFailureSelection().failureOrDispatchFailure()

        assertEquals(DispatchProviderRetryPolicy.ERROR_DISPATCH_FAILURE, failure.errorType)
        assertEquals("All automatic delivery routes failed.", failure.redactedErrorMessage)
    }

    @Test
    fun whatsAppAutomationFailure_mapsServiceDisabledToUnavailableProviderFailure() {
        val failure = DispatchProviderRetryPolicy.whatsAppAutomationFailure(
            WhatsAppSendFailureReason.SERVICE_DISABLED
        )

        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_UNAVAILABLE, failure.errorType)
        assertEquals("ACCESSIBILITY_AUTOMATION_UNAVAILABLE", failure.errorCode)
        assertEquals(
            "WhatsApp Accessibility service is disabled; setup must be reviewed before retry.",
            failure.redactedErrorMessage,
        )
    }

    @Test
    fun whatsAppConsentRequired_mapsMissingConsentToFinalProviderFailure() {
        val failure = DispatchProviderRetryPolicy.whatsAppConsentRequired()

        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_CONSENT_REQUIRED, failure.errorType)
        assertEquals("APP_CONSENT_NOT_GRANTED", failure.errorCode)
        assertEquals(
            "WhatsApp automation consent has not been confirmed in AI Doctor.",
            failure.redactedErrorMessage,
        )
    }

    @Test
    fun whatsAppAutomationFailure_mapsSpecificAutomationReasonToProviderFailure() {
        val failure = DispatchProviderRetryPolicy.whatsAppAutomationFailure(
            WhatsAppSendFailureReason.SEND_CONFIRMATION_TIMEOUT
        )

        assertEquals(DispatchProviderRetryPolicy.ERROR_WHATSAPP_AUTOMATION_FAILURE, failure.errorType)
        assertEquals("SEND_CONFIRMATION_TIMEOUT", failure.errorCode)
        assertEquals(
            "WhatsApp send confirmation timed out before delivery handoff.",
            failure.redactedErrorMessage,
        )
    }

    @Test
    fun smsDispatchExceptionFailure_mapsSecurityExceptionToPermissionFailure() {
        val failure = smsDispatchExceptionFailure(SecurityException("missing permission"))

        assertEquals(DispatchProviderRetryPolicy.ERROR_SMS_PERMISSION_DENIED, failure.errorType)
        assertEquals("ANDROID_SEND_SMS_PERMISSION", failure.errorCode)
        assertEquals(
            "SMS permission is missing; automatic SMS cannot be sent.",
            failure.redactedErrorMessage,
        )
        assertEquals(true, failure.requiresSmsPermissionSetupNotification())
    }

    @Test
    fun smsDispatchExceptionFailure_mapsRuntimeExceptionToRetryableProviderFailure() {
        val failure = smsDispatchExceptionFailure(RuntimeException("radio unavailable"))

        assertEquals(DispatchProviderRetryPolicy.ERROR_SMS_TRANSIENT_PROVIDER_FAILURE, failure.errorType)
        assertEquals("RuntimeException", failure.errorCode)
        assertEquals(
            "SMS provider failed before delivery confirmation; retry is allowed.",
            failure.redactedErrorMessage,
        )
        assertEquals(false, failure.requiresSmsPermissionSetupNotification())
    }
}
