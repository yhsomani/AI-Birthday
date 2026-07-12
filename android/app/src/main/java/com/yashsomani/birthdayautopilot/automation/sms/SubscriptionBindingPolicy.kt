package com.yashsomani.birthdayautopilot.automation.sms

import android.telephony.SubscriptionManager

/** Pure fail-closed policy shared by Arm, barrier, and the sole SmsManager boundary. */
internal object SubscriptionBindingPolicy {
  const val SYSTEM_DEFAULT = "SYSTEM_DEFAULT"

  fun matches(
    policyKind: String,
    approvedSubscriptionId: Int,
    currentDefaultSubscriptionId: Int?,
    approvedSubscriptionActive: Boolean,
  ): Boolean =
    policyKind == SYSTEM_DEFAULT &&
      approvedSubscriptionId >= 0 &&
      currentDefaultSubscriptionId == approvedSubscriptionId &&
      approvedSubscriptionActive
}

internal fun currentDefaultSmsSubscriptionIdOrNull(): Int? = try {
  SubscriptionManager.getDefaultSmsSubscriptionId()
    .takeIf(SubscriptionManager::isValidSubscriptionId)
} catch (_: RuntimeException) {
  null
} catch (_: LinkageError) {
  null
}
