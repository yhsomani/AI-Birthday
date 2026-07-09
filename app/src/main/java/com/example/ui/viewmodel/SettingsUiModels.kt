package com.example.ui.viewmodel

import com.example.domain.model.ApprovalMode
import com.example.ui.feedback.FeedbackEvent

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
    val geminiApiKey: String = "",
    val geminiApiKeySaved: Boolean = false,
    val senderEmail: String = "",
    val senderEmailPassword: String = "",
    val senderEmailSaved: Boolean = false,
    val automationMode: ApprovalMode = ApprovalMode.ALWAYS_ASK,
    val quietHoursStart: String = "22",
    val quietHoursEnd: String = "8",
    val biometricLockEnabled: Boolean = false,
    val channelBlackoutSms: Boolean = false,
    val channelBlackoutWhatsApp: Boolean = false,
    val channelBlackoutEmail: Boolean = false,
    val syncError: String? = null,
    val feedbackEvent: FeedbackEvent? = null,
    val showLegacyDbNotice: Boolean = false,
    val showSecurePrefsRecoveryNotice: Boolean = false,
)
