package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class BiometricLockPolicyTest {
    @Test
    fun `resolve unlocks when biometric setting is disabled`() {
        val result = BiometricLockPolicy.resolve(
            isEnabled = false,
            isAuthenticatorAvailable = false,
            isSessionUnlocked = false,
        )

        assertEquals(BiometricLockRequirement.UNLOCKED, result)
    }

    @Test
    fun `resolve unlocks when session already authenticated`() {
        val result = BiometricLockPolicy.resolve(
            isEnabled = true,
            isAuthenticatorAvailable = true,
            isSessionUnlocked = true,
        )

        assertEquals(BiometricLockRequirement.UNLOCKED, result)
    }

    @Test
    fun `resolve locks when setting is enabled and authenticator is available`() {
        val result = BiometricLockPolicy.resolve(
            isEnabled = true,
            isAuthenticatorAvailable = true,
            isSessionUnlocked = false,
        )

        assertEquals(BiometricLockRequirement.LOCKED, result)
    }

    @Test
    fun `resolve reports unavailable when setting is enabled without authenticator`() {
        val result = BiometricLockPolicy.resolve(
            isEnabled = true,
            isAuthenticatorAvailable = false,
            isSessionUnlocked = false,
        )

        assertEquals(BiometricLockRequirement.UNAVAILABLE, result)
    }
}
