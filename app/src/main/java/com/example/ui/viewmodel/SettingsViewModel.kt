package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import com.example.core.db.DatabaseKeyDerivation
import com.example.core.prefs.SecurePrefs
import com.example.R
import com.example.domain.repository.ContactRepository
import com.example.domain.usecase.SyncContactsUseCase
import com.example.ui.feedback.FeedbackEvent
import com.example.ui.feedback.FeedbackType
import com.example.ui.feedback.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class SettingsUiState(
    val userName: String = "User",
    val userEmail: String = "",
    val userPhotoUrl: String? = null,
    val birthdayReminders: Boolean = true,
    val aiWishGeneration: Boolean = true,
    val contactSyncEnabled: Boolean = true,
    val isSyncing: Boolean = false,
    val lastSyncTimestamp: String = "Never",
    val lastBackupTimestamp: String = "Never",
    // AI configuration
    val geminiApiKey: String = "",
    val geminiApiKeySaved: Boolean = false,
    val senderEmail: String = "",
    val senderEmailPassword: String = "",
    val senderEmailSaved: Boolean = false,
    val automationMode: String = "SMART_APPROVE",
    val quietHoursStart: String = "22",
    val quietHoursEnd: String = "8",
    val biometricLockEnabled: Boolean = false,
    val channelBlackoutSms: Boolean = false,
    val channelBlackoutWhatsApp: Boolean = false,
    val channelBlackoutEmail: Boolean = false,
    val syncError: String? = null,
    val feedbackEvent: FeedbackEvent? = null,
    val showLegacyDbNotice: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val syncContactsUseCase: SyncContactsUseCase,
    private val contactRepository: ContactRepository,
    private val authManager: AuthManager,
    private val securePrefs: SecurePrefs,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        // Load persisted settings
        _uiState.value = _uiState.value.copy(
            geminiApiKey = securePrefs.getGeminiApiKey(),
            senderEmail = securePrefs.getSenderEmail(),
            senderEmailPassword = securePrefs.getSenderEmailPassword(),
            automationMode = securePrefs.getGlobalAutomationMode(),
            lastSyncTimestamp = appContext.getString(R.string.settings_last_sync_never),
            lastBackupTimestamp = formatLastBackupTimestamp(securePrefs.getLastBackupMs()),
            quietHoursStart = securePrefs.getQuietHoursStart().toString(),
            quietHoursEnd = securePrefs.getQuietHoursEnd().toString(),
            biometricLockEnabled = securePrefs.isBiometricLockEnabled(),
            birthdayReminders = securePrefs.isBirthdayRemindersEnabled(),
            aiWishGeneration = securePrefs.isAiWishGenerationEnabled(),
            channelBlackoutSms = securePrefs.isChannelBlacklisted("SMS"),
            channelBlackoutWhatsApp = securePrefs.isChannelBlacklisted("WHATSAPP"),
            channelBlackoutEmail = securePrefs.isChannelBlacklisted("EMAIL"),
            showLegacyDbNotice = securePrefs.wasLegacyUnencryptedDbQuarantined(),
        )
        viewModelScope.launch {
            authManager.userProfile.collect { profile ->
                _uiState.value = _uiState.value.copy(
                    userName = profile.displayName,
                    userEmail = profile.email,
                    userPhotoUrl = profile.photoUrl,
                )
            }
        }
    }

    fun toggleBirthdayReminders(enabled: Boolean) {
        securePrefs.setBirthdayRemindersEnabled(enabled)
        _uiState.value = _uiState.value.copy(birthdayReminders = enabled)
    }

    fun toggleAiWishGeneration(enabled: Boolean) {
        securePrefs.setAiWishGenerationEnabled(enabled)
        _uiState.value = _uiState.value.copy(aiWishGeneration = enabled)
    }

    fun onGeminiApiKeyChange(key: String) {
        _uiState.value = _uiState.value.copy(geminiApiKey = key, geminiApiKeySaved = false)
    }

    fun saveGeminiApiKey() {
        val key = _uiState.value.geminiApiKey.trim()
        securePrefs.setGeminiApiKey(key)
        _uiState.value = _uiState.value.copy(
            geminiApiKeySaved = true,
            feedbackEvent = FeedbackEvent(
                message = UiText.Resource(R.string.settings_gemini_saved),
                type = FeedbackType.SUCCESS,
            ),
        )
    }

    fun onSenderEmailChange(email: String) {
        _uiState.value = _uiState.value.copy(senderEmail = email, senderEmailSaved = false)
    }

    fun onSenderEmailPasswordChange(password: String) {
        _uiState.value = _uiState.value.copy(senderEmailPassword = password, senderEmailSaved = false)
    }

    fun saveSenderEmailSettings() {
        val email = _uiState.value.senderEmail.trim()
        val password = _uiState.value.senderEmailPassword.trim()
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = _uiState.value.copy(
                feedbackEvent = FeedbackEvent(
                    message = UiText.Resource(R.string.settings_email_setup_required),
                    type = FeedbackType.ERROR,
                )
            )
            return
        }
        securePrefs.setSenderEmail(email)
        securePrefs.setSenderEmailPassword(password)
        _uiState.value = _uiState.value.copy(
            senderEmail = email,
            senderEmailPassword = password,
            senderEmailSaved = true,
            feedbackEvent = FeedbackEvent(
                message = UiText.Resource(R.string.settings_email_saved),
                type = FeedbackType.SUCCESS,
            ),
        )
    }

    fun setAutomationMode(mode: String) {
        securePrefs.setGlobalAutomationMode(mode)
        _uiState.value = _uiState.value.copy(automationMode = mode)
    }

    fun onQuietHoursStartChange(value: String) {
        _uiState.value = _uiState.value.copy(quietHoursStart = value.filter(Char::isDigit).take(2))
    }

    fun onQuietHoursEndChange(value: String) {
        _uiState.value = _uiState.value.copy(quietHoursEnd = value.filter(Char::isDigit).take(2))
    }

    fun saveQuietHours() {
        val start = _uiState.value.quietHoursStart.toIntOrNull()
        val end = _uiState.value.quietHoursEnd.toIntOrNull()
        if (start !in 0..23 || end !in 0..23) {
            _uiState.value = _uiState.value.copy(
                feedbackEvent = FeedbackEvent(
                    message = UiText.Resource(R.string.settings_quiet_hours_invalid),
                    type = FeedbackType.ERROR,
                )
            )
            return
        }
        securePrefs.setQuietHoursStart(start ?: 22)
        securePrefs.setQuietHoursEnd(end ?: 8)
        _uiState.value = _uiState.value.copy(
            feedbackEvent = FeedbackEvent(
                message = UiText.Resource(R.string.settings_quiet_hours_saved),
                type = FeedbackType.SUCCESS,
            )
        )
    }

    fun toggleBiometricLock(enabled: Boolean) {
        securePrefs.setBiometricLockEnabled(enabled)
        _uiState.value = _uiState.value.copy(
            biometricLockEnabled = enabled,
            feedbackEvent = FeedbackEvent(
                message = UiText.Resource(
                    if (enabled) R.string.settings_biometric_enabled else R.string.settings_biometric_disabled
                ),
                type = FeedbackType.SUCCESS,
            ),
        )
    }

    fun toggleChannelBlackout(channel: String, disabled: Boolean) {
        val next = securePrefs.getChannelBlackout().toMutableChannelSet().apply {
            if (disabled) add(channel) else remove(channel)
        }
        securePrefs.setChannelBlackout(next.toJsonArray())
        _uiState.value = _uiState.value.copy(
            channelBlackoutSms = "SMS" in next,
            channelBlackoutWhatsApp = "WHATSAPP" in next,
            channelBlackoutEmail = "EMAIL" in next,
            feedbackEvent = FeedbackEvent(
                message = UiText.Resource(R.string.settings_channel_blackout_saved),
                type = FeedbackType.SUCCESS,
            ),
        )
    }

    fun syncContacts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true, syncError = null)
            try {
                syncContactsUseCase(forceRefresh = true)
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    lastSyncTimestamp = appContext.getString(R.string.settings_last_sync_just_now),
                    syncError = null,
                    feedbackEvent = FeedbackEvent(
                        message = UiText.Resource(R.string.settings_sync_contacts_success),
                        type = FeedbackType.SUCCESS,
                    ),
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    syncError = e.message ?: appContext.getString(R.string.settings_sync_contacts_failed),
                    feedbackEvent = FeedbackEvent(
                        message = UiText.Dynamic(e.message ?: appContext.getString(R.string.settings_sync_contacts_failed)),
                        type = FeedbackType.ERROR,
                    ),
                )
            }
        }
    }

    fun clearSyncError() {
        _uiState.value = _uiState.value.copy(syncError = null)
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(feedbackEvent = null)
    }

    fun dismissLegacyDbNotice() {
        securePrefs.setLegacyUnencryptedDbQuarantined(false)
        _uiState.value = _uiState.value.copy(showLegacyDbNotice = false)
    }

    fun signOut(context: Context) {
        authManager.signOut()
        DatabaseKeyDerivation.clearCachedKey(context)
        securePrefs.clearAll()
        try {
            context.deleteDatabase("relateai.db")
        } catch (e: Exception) {
            android.util.Log.e("SettingsViewModel", "Failed to delete database file", e)
        }
    }

    private fun SecurePrefs.isChannelBlacklisted(channel: String): Boolean {
        return channel in getChannelBlackout().toMutableChannelSet()
    }

    private fun String.toMutableChannelSet(): MutableSet<String> {
        return try {
            val array = org.json.JSONArray(this)
            MutableList(array.length()) { index -> array.optString(index).uppercase() }
                .filter { it in setOf("SMS", "WHATSAPP", "EMAIL") }
                .toMutableSet()
        } catch (_: Exception) {
            mutableSetOf()
        }
    }

    private fun Set<String>.toJsonArray(): String {
        return org.json.JSONArray(toList().sorted()).toString()
    }

    private fun formatLastBackupTimestamp(timestampMs: Long): String {
        if (timestampMs <= 0L) {
            return appContext.getString(R.string.settings_last_sync_never)
        }

        val ageMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
        val ageDays = TimeUnit.MILLISECONDS.toDays(ageMs)
        return when {
            ageDays == 0L -> appContext.getString(R.string.settings_last_backup_today)
            ageDays == 1L -> appContext.getString(R.string.settings_last_backup_yesterday)
            else -> appContext.getString(R.string.settings_last_backup_days_ago, ageDays)
        }
    }
}
