package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.auth.AuthManager
import com.example.core.prefs.SecurePrefs
import com.example.R
import com.example.domain.model.ApprovalMode
import com.example.domain.model.MessageChannel
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
    val automationMode: ApprovalMode = ApprovalMode.SMART_APPROVE,
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
            automationMode = securePrefs.getGlobalApprovalMode(),
            lastSyncTimestamp = appContext.getString(R.string.settings_last_sync_never),
            lastBackupTimestamp = formatLastBackupTimestamp(securePrefs.getLastBackupMs()),
            quietHoursStart = securePrefs.getQuietHoursStart().toString(),
            quietHoursEnd = securePrefs.getQuietHoursEnd().toString(),
            biometricLockEnabled = securePrefs.isBiometricLockEnabled(),
            birthdayReminders = securePrefs.isBirthdayRemindersEnabled(),
            aiWishGeneration = securePrefs.isAiWishGenerationEnabled(),
            channelBlackoutSms = securePrefs.isChannelBlacklisted(MessageChannel.SMS),
            channelBlackoutWhatsApp = securePrefs.isChannelBlacklisted(MessageChannel.WHATSAPP),
            channelBlackoutEmail = securePrefs.isChannelBlacklisted(MessageChannel.EMAIL),
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

    fun setAutomationMode(mode: ApprovalMode) {
        securePrefs.setGlobalApprovalMode(mode)
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

    fun toggleChannelBlackout(channel: MessageChannel, disabled: Boolean) {
        if (channel == MessageChannel.UNKNOWN) return

        val next = securePrefs.getChannelBlackout().toMutableChannelSet().apply {
            if (disabled) add(channel) else remove(channel)
        }
        securePrefs.setChannelBlackout(next.toJsonArray())
        _uiState.value = _uiState.value.copy(
            channelBlackoutSms = MessageChannel.SMS in next,
            channelBlackoutWhatsApp = MessageChannel.WHATSAPP in next,
            channelBlackoutEmail = MessageChannel.EMAIL in next,
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
                val outcome = syncContactsUseCase(forceRefresh = true)
                val permissionMessage = appContext.getString(R.string.settings_sync_contacts_device_permission_missing)
                _uiState.value = _uiState.value.copy(
                    isSyncing = false,
                    lastSyncTimestamp = appContext.getString(R.string.settings_last_sync_just_now),
                    syncError = if (outcome.deviceContactsPermissionDenied) permissionMessage else null,
                    feedbackEvent = FeedbackEvent(
                        message = if (outcome.deviceContactsPermissionDenied) {
                            UiText.Dynamic(permissionMessage)
                        } else {
                            UiText.Resource(R.string.settings_sync_contacts_success)
                        },
                        type = if (outcome.deviceContactsPermissionDenied) FeedbackType.ERROR else FeedbackType.SUCCESS,
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

    fun signOut() {
        authManager.signOut()
    }

    private fun SecurePrefs.isChannelBlacklisted(channel: MessageChannel): Boolean {
        return channel in getChannelBlackout().toMutableChannelSet()
    }

    private fun String.toMutableChannelSet(): MutableSet<MessageChannel> {
        return CHANNEL_TOKEN_PATTERN.findAll(this)
            .map { match -> MessageChannel.fromRaw(match.groupValues[1]) }
            .filter { it != MessageChannel.UNKNOWN }
            .toMutableSet()
    }

    private fun Set<MessageChannel>.toJsonArray(): String {
        return map { it.raw }
            .sorted()
            .joinToString(separator = ",", prefix = "[", postfix = "]") { channel -> "\"$channel\"" }
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

    private companion object {
        val CHANNEL_TOKEN_PATTERN = Regex("\"([A-Za-z_]+)\"")
    }
}
