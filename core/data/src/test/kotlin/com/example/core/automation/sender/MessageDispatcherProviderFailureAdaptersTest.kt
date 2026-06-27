package com.example.core.automation.sender

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
