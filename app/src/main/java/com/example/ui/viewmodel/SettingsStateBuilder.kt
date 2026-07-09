package com.example.ui.viewmodel

import android.content.Context
import com.example.R
import com.example.domain.model.MessageChannel
import com.example.domain.model.MessageChannelSetCodec
import com.example.domain.service.PreferencesRepository
import com.example.domain.usecase.EnableFullAutomationUseCase
import com.example.ui.feedback.UiText
import java.util.concurrent.TimeUnit

internal fun SettingsUiState.withPersistedSettings(
    preferencesRepository: PreferencesRepository,
    appContext: Context,
    preserveLastSyncTimestamp: Boolean,
): SettingsUiState {
    return copy(
        geminiApiKey = preferencesRepository.getGeminiApiKey(),
        senderEmail = preferencesRepository.getSenderEmail(),
        senderEmailPassword = preferencesRepository.getSenderEmailPassword(),
        automationMode = preferencesRepository.getGlobalAutomationMode(),
        lastSyncTimestamp = if (preserveLastSyncTimestamp) {
            lastSyncTimestamp
        } else {
            appContext.getString(R.string.settings_last_sync_never)
        },
        lastBackupTimestamp = formatLastBackupTimestamp(
            timestampMs = preferencesRepository.getLastBackupMs(),
            appContext = appContext,
        ),
        quietHoursStart = preferencesRepository.getQuietHoursStart().toString(),
        quietHoursEnd = preferencesRepository.getQuietHoursEnd().toString(),
        biometricLockEnabled = preferencesRepository.isBiometricLockEnabled(),
        birthdayReminders = preferencesRepository.isBirthdayRemindersEnabled(),
        aiWishGeneration = preferencesRepository.isAiWishGenerationEnabled(),
        channelBlackoutSms = preferencesRepository.isChannelBlacklisted(MessageChannel.SMS),
        channelBlackoutWhatsApp = preferencesRepository.isChannelBlacklisted(MessageChannel.WHATSAPP),
        channelBlackoutEmail = preferencesRepository.isChannelBlacklisted(MessageChannel.EMAIL),
        showLegacyDbNotice = preferencesRepository.wasLegacyUnencryptedDbQuarantined(),
        showSecurePrefsRecoveryNotice = preferencesRepository.isSecurePrefsRebuiltNoticePending(),
    )
}

internal fun PreferencesRepository.isChannelBlacklisted(channel: MessageChannel): Boolean {
    return channel in getChannelBlackout().toMutableChannelSet()
}

internal fun String.toMutableChannelSet(): MutableSet<MessageChannel> {
    return MessageChannelSetCodec.parse(this).toMutableSet()
}

internal fun Set<MessageChannel>.toJsonArray(): String {
    return MessageChannelSetCodec.toJsonArray(this)
}

internal fun settingsFullAutomationMessage(outcome: EnableFullAutomationUseCase.Outcome): UiText {
    return when {
        outcome.skippedWithoutRoute > 0 && outcome.skippedNeedsReview > 0 -> UiText.Resource(
            R.string.settings_full_automation_enabled_route_and_review_blockers,
            listOf(
                outcome.updatedContacts,
                outcome.promotedMessages,
                outcome.skippedWithoutRoute,
                outcome.skippedNeedsReview,
            ),
        )
        outcome.skippedWithoutRoute > 0 -> UiText.Resource(
            R.string.settings_full_automation_enabled_route_blockers,
            listOf(
                outcome.updatedContacts,
                outcome.promotedMessages,
                outcome.skippedWithoutRoute,
            ),
        )
        outcome.skippedNeedsReview > 0 -> UiText.Resource(
            R.string.settings_full_automation_enabled_review_blockers,
            listOf(
                outcome.updatedContacts,
                outcome.promotedMessages,
                outcome.skippedNeedsReview,
            ),
        )
        else -> UiText.Resource(
            R.string.settings_full_automation_enabled,
            listOf(outcome.updatedContacts, outcome.promotedMessages),
        )
    }
}

internal fun settingsFullAutomationFailureMessage(error: Throwable): UiText {
    val reason = error.message?.takeUnless(String::isBlank)
    return if (reason == null) {
        UiText.Resource(R.string.settings_full_automation_failed)
    } else {
        UiText.Resource(R.string.settings_full_automation_failed_with_reason, listOf(reason))
    }
}

private fun formatLastBackupTimestamp(timestampMs: Long, appContext: Context): String {
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
