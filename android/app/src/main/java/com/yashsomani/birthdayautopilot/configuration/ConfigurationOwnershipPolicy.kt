package com.yashsomani.birthdayautopilot.configuration

import com.yashsomani.birthdayautopilot.core.model.AccountMode

/** Content configuration belongs only to the current Android sender installation. */
internal object ConfigurationOwnershipPolicy {
  fun blockedReason(mode: AccountMode): String? = when (mode) {
    AccountMode.STANDBY -> "active-sender-other-device"
    AccountMode.TRANSFER_PENDING -> "transfer-pending"
    AccountMode.DELETING -> "firebase-account-deleting"
    AccountMode.TEST_ONLY,
    AccountMode.PAUSED_REPAIR,
    AccountMode.AUTOMATION_ACTIVE,
    -> null
  }
}
