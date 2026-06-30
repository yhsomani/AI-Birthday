package com.example.core.prefs

import com.example.domain.model.ApprovalMode
import com.example.domain.service.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val securePrefs: SecurePrefs
) : PreferencesRepository {

    override fun observeChanges(): Flow<Unit> = securePrefs.observeChanges()

    override fun setGoogleOAuthToken(token: String) = securePrefs.setGoogleOAuthToken(token)
    override fun getGoogleOAuthToken(): String = securePrefs.getGoogleOAuthToken()

    override fun setGeminiApiKey(key: String) = securePrefs.setGeminiApiKey(key)
    override fun getGeminiApiKey(): String = securePrefs.getGeminiApiKey()

    override fun setSenderEmail(email: String) = securePrefs.setSenderEmail(email)
    override fun getSenderEmail(): String = securePrefs.getSenderEmail()

    override fun setSenderEmailPassword(pw: String) = securePrefs.setSenderEmailPassword(pw)
    override fun getSenderEmailPassword(): String = securePrefs.getSenderEmailPassword()

    override fun setLastSuccessfulEmailTest(senderEmail: String, timestampMs: Long) =
        securePrefs.setLastSuccessfulEmailTest(senderEmail, timestampMs)
    override fun getLastSuccessfulEmailTestSender(): String =
        securePrefs.getLastSuccessfulEmailTestSender()
    override fun getLastSuccessfulEmailTestMs(): Long =
        securePrefs.getLastSuccessfulEmailTestMs()

    override fun setGlobalAutomationMode(mode: ApprovalMode) = securePrefs.setGlobalApprovalMode(mode)
    override fun getGlobalAutomationMode(): ApprovalMode = securePrefs.getGlobalApprovalMode()

    override fun setThemeMode(mode: String) = securePrefs.setThemeMode(mode)
    override fun getThemeMode(): String = securePrefs.getThemeMode()

    override fun setBlackoutDates(datesJson: String) = securePrefs.setBlackoutDates(datesJson)
    override fun getBlackoutDates(): String = securePrefs.getBlackoutDates()

    override fun setQuietHoursStart(hour: Int) = securePrefs.setQuietHoursStart(hour)
    override fun getQuietHoursStart(): Int = securePrefs.getQuietHoursStart()

    override fun setQuietHoursEnd(hour: Int) = securePrefs.setQuietHoursEnd(hour)
    override fun getQuietHoursEnd(): Int = securePrefs.getQuietHoursEnd()

    override fun setChannelBlackout(channelsJson: String) = securePrefs.setChannelBlackout(channelsJson)
    override fun getChannelBlackout(): String = securePrefs.getChannelBlackout()

    override fun setBiometricLockEnabled(enabled: Boolean) = securePrefs.setBiometricLockEnabled(enabled)
    override fun isBiometricLockEnabled(): Boolean = securePrefs.isBiometricLockEnabled()

    override fun setBirthdayRemindersEnabled(enabled: Boolean) =
        securePrefs.setBirthdayRemindersEnabled(enabled)
    override fun isBirthdayRemindersEnabled(): Boolean =
        securePrefs.isBirthdayRemindersEnabled()

    override fun setAiWishGenerationEnabled(enabled: Boolean) =
        securePrefs.setAiWishGenerationEnabled(enabled)
    override fun isAiWishGenerationEnabled(): Boolean =
        securePrefs.isAiWishGenerationEnabled()

    override fun isSecureStorageAvailable(): Boolean = securePrefs.isSecureStorageAvailable()

    override fun setSyncToken(token: String) = securePrefs.setSyncToken(token)
    override fun getSyncToken(): String = securePrefs.getSyncToken()

    override fun setOnboardingComplete(complete: Boolean) = securePrefs.setOnboardingComplete(complete)
    override fun isOnboardingComplete(): Boolean = securePrefs.isOnboardingComplete()

    override fun setLastSyncError(error: String?) = securePrefs.setLastSyncError(error)
    override fun getLastSyncError(): String? = securePrefs.getLastSyncError()

    override fun getLastBackupMs(): Long = securePrefs.getLastBackupMs()

    override fun clearAll() = securePrefs.clearAll()
}
