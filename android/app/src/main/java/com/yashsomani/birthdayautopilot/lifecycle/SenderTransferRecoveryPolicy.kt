package com.yashsomani.birthdayautopilot.lifecycle

internal object SenderTransferRecoveryPolicy {
  fun requiresAuthoritativeRegistration(state: String): Boolean = state == "remote-pending"
}
