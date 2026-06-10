package com.example

enum class BiometricLockRequirement {
    UNLOCKED,
    LOCKED,
    UNAVAILABLE,
}

object BiometricLockPolicy {
    fun resolve(
        isEnabled: Boolean,
        isAuthenticatorAvailable: Boolean,
        isSessionUnlocked: Boolean,
    ): BiometricLockRequirement {
        return when {
            !isEnabled -> BiometricLockRequirement.UNLOCKED
            isSessionUnlocked -> BiometricLockRequirement.UNLOCKED
            !isAuthenticatorAvailable -> BiometricLockRequirement.UNAVAILABLE
            else -> BiometricLockRequirement.LOCKED
        }
    }
}
