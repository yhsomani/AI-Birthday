package com.example.domain.service

import com.example.domain.model.ApprovalMode
import kotlinx.coroutines.flow.Flow

interface PreferencesRepository {
    fun observeChanges(): Flow<Unit>

    fun setGoogleOAuthToken(token: String)
    fun getGoogleOAuthToken(): String

    fun setGeminiApiKey(key: String)
    fun getGeminiApiKey(): String

    fun setSenderEmail(email: String)
    fun getSenderEmail(): String

    fun setSenderEmailPassword(pw: String)
    fun getSenderEmailPassword(): String

    fun setLastSuccessfulEmailTest(senderEmail: String, timestampMs: Long)
    fun getLastSuccessfulEmailTestSender(): String
    fun getLastSuccessfulEmailTestMs(): Long

    fun setGlobalAutomationMode(mode: ApprovalMode)
    fun getGlobalAutomationMode(): ApprovalMode

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

    fun setLastSyncError(error: String?)
    fun getLastSyncError(): String?

    fun getLastBackupMs(): Long

    fun clearAll()
}
