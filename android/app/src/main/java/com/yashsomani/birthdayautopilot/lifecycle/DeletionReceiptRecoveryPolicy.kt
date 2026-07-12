package com.yashsomani.birthdayautopilot.lifecycle

internal object DeletionReceiptRecoveryPolicy {
  fun interruptedOperationId(
    lookup: DeletionReceiptLookup,
    operation: DurablePrivacyOperation?,
  ): String? = operation?.takeIf {
    lookup == DeletionReceiptLookup.None &&
      it.action == "delete-account" &&
      it.state !in setOf("complete", "failed")
  }?.id
}
