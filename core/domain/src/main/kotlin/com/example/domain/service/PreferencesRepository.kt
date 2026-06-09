package com.example.domain.service

interface PreferencesRepository {
    fun setGoogleOAuthToken(token: String)
    fun getGoogleOAuthToken(): String

    fun setGeminiApiKey(key: String)
    fun getGeminiApiKey(): String

    fun setSenderEmail(email: String)
    fun getSenderEmail(): String

    fun setSenderEmailPassword(pw: String)
    fun getSenderEmailPassword(): String

    fun setGlobalAutomationMode(mode: String)
    fun getGlobalAutomationMode(): String

    fun setThemeMode(mode: String)
    fun getThemeMode(): String

    fun setBlackoutDates(datesJson: String)
    fun getBlackoutDates(): String

    fun setQuietHoursStart(hour: Int)
    fun getQuietHoursStart(): Int

    fun setQuietHoursEnd(hour: Int)
    fun getQuietHoursEnd(): Int

    fun setChannelBlackout(channelsJson: String)
    fun getChannelBlackout(): String

    fun setBiometricLockEnabled(enabled: Boolean)
    fun isBiometricLockEnabled(): Boolean

    fun setBirthdayRemindersEnabled(enabled: Boolean)
    fun isBirthdayRemindersEnabled(): Boolean

    fun setAiWishGenerationEnabled(enabled: Boolean)
    fun isAiWishGenerationEnabled(): Boolean

    fun isSecureStorageAvailable(): Boolean

    fun setSyncToken(token: String)
    fun getSyncToken(): String

    fun setOnboardingComplete(complete: Boolean)
    fun isOnboardingComplete(): Boolean

    fun setGuestMode(enabled: Boolean)
    fun isGuestMode(): Boolean

    fun setLastSyncError(error: String?)
    fun getLastSyncError(): String?

    fun clearAll()
}
