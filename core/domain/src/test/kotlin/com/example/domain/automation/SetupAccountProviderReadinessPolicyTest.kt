package com.example.domain.automation

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupAccountProviderReadinessPolicyTest {

    @Test
    fun `evaluateGoogleContacts passes when access is available`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGoogleContacts(
            hasGoogleContactsAccess = true,
        )

        assertEquals(GoogleContactsReadinessReason.READY, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(SetupReadinessGroup.REQUIRED, readiness.group)
    }

    @Test
    fun `evaluateGoogleContacts requires action when access is missing`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGoogleContacts(
            hasGoogleContactsAccess = false,
        )

        assertEquals(GoogleContactsReadinessReason.ACCESS_MISSING, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
    }

    @Test
    fun `evaluateGeminiAccess prefers API key readiness`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGeminiAccess(
            hasGeminiApiKey = true,
            hasFirebaseAuth = true,
        )

        assertEquals(GeminiAccessReadinessReason.API_KEY_CONFIGURED, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(SetupReadinessGroup.REQUIRED, readiness.group)
    }

    @Test
    fun `evaluateGeminiAccess accepts Firebase auth without API key`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGeminiAccess(
            hasGeminiApiKey = false,
            hasFirebaseAuth = true,
        )

        assertEquals(GeminiAccessReadinessReason.FIREBASE_AUTH_AVAILABLE, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluateGeminiAccess requires action when no provider access exists`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGeminiAccess(
            hasGeminiApiKey = false,
            hasFirebaseAuth = false,
        )

        assertEquals(GeminiAccessReadinessReason.MISSING_ACCESS, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
    }

    @Test
    fun `evaluateAiWishGeneration passes when enabled`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateAiWishGeneration(
            aiWishGenerationEnabled = true,
        )

        assertEquals(AiWishGenerationReadinessReason.ENABLED, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
    }

    @Test
    fun `evaluateAiWishGeneration requires action when disabled`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateAiWishGeneration(
            aiWishGenerationEnabled = false,
        )

        assertEquals(AiWishGenerationReadinessReason.DISABLED, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
    }

    @Test
    fun `evaluateGeminiCircuit passes when no circuit state has been recorded`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGeminiCircuit(
            circuitState = SetupProviderCircuitState.NONE,
        )

        assertEquals(GeminiCircuitReadinessReason.NO_STATE, readiness.reason)
        assertEquals(SetupReadinessStatus.OK, readiness.status)
        assertEquals(SetupReadinessGroup.RELIABILITY, readiness.group)
    }

    @Test
    fun `evaluateGeminiCircuit warns when provider is half open`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGeminiCircuit(
            circuitState = SetupProviderCircuitState.HALF_OPEN,
        )

        assertEquals(GeminiCircuitReadinessReason.HALF_OPEN, readiness.reason)
        assertEquals(SetupReadinessStatus.WARNING, readiness.status)
        assertEquals(SetupProviderCircuitState.HALF_OPEN, readiness.circuitState)
    }

    @Test
    fun `evaluateGeminiCircuit requires action when provider is open`() {
        val readiness = SetupAccountProviderReadinessPolicy.evaluateGeminiCircuit(
            circuitState = SetupProviderCircuitState.OPEN,
        )

        assertEquals(GeminiCircuitReadinessReason.OPEN, readiness.reason)
        assertEquals(SetupReadinessStatus.ACTION_REQUIRED, readiness.status)
        assertEquals(SetupProviderCircuitState.OPEN, readiness.circuitState)
    }
}
