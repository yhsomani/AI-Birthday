package com.example.core.backup

import com.example.core.prefs.SecurePrefs
import com.example.domain.model.ApprovalMode

internal data class BackupPreferencesDto(
    val globalAutomationMode: String,
    val themeMode: String,
    val blackoutDatesJson: String,
    val quietHoursStart: Int,
    val quietHoursEnd: Int,
    val channelBlackoutJson: String,
    val biometricLockEnabled: Boolean,
    val birthdayRemindersEnabled: Boolean,
    val aiWishGenerationEnabled: Boolean,
    val whatsAppAutomationConsentGranted: Boolean = false,
) {
    fun restoreTo(securePrefs: SecurePrefs) {
        securePrefs.setGlobalAutomationMode(globalAutomationMode)
        securePrefs.setThemeMode(themeMode)
        securePrefs.setBlackoutDates(blackoutDatesJson)
        securePrefs.setQuietHoursStart(quietHoursStart)
        securePrefs.setQuietHoursEnd(quietHoursEnd)
        securePrefs.setChannelBlackout(channelBlackoutJson)
        securePrefs.setBiometricLockEnabled(biometricLockEnabled)
        securePrefs.setBirthdayRemindersEnabled(birthdayRemindersEnabled)
        securePrefs.setAiWishGenerationEnabled(aiWishGenerationEnabled)
        securePrefs.setWhatsAppAutomationConsentGranted(whatsAppAutomationConsentGranted)
    }

    companion object {
        fun defaults() = BackupPreferencesDto(
            globalAutomationMode = ApprovalMode.ALWAYS_ASK.raw,
            themeMode = "SYSTEM",
            blackoutDatesJson = "[]",
            quietHoursStart = 22,
            quietHoursEnd = 8,
            channelBlackoutJson = "[]",
            biometricLockEnabled = false,
            birthdayRemindersEnabled = true,
            aiWishGenerationEnabled = true,
            whatsAppAutomationConsentGranted = false,
        )

        fun from(securePrefs: SecurePrefs) = BackupPreferencesDto(
            globalAutomationMode = securePrefs.getGlobalAutomationMode(),
            themeMode = securePrefs.getThemeMode(),
            blackoutDatesJson = securePrefs.getBlackoutDates(),
            quietHoursStart = securePrefs.getQuietHoursStart(),
            quietHoursEnd = securePrefs.getQuietHoursEnd(),
            channelBlackoutJson = securePrefs.getChannelBlackout(),
            biometricLockEnabled = securePrefs.isBiometricLockEnabled(),
            birthdayRemindersEnabled = securePrefs.isBirthdayRemindersEnabled(),
            aiWishGenerationEnabled = securePrefs.isAiWishGenerationEnabled(),
            whatsAppAutomationConsentGranted = securePrefs.isWhatsAppAutomationConsentGranted(),
        )
    }
}
