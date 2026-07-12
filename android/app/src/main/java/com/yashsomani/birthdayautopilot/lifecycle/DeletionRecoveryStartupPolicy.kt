package com.yashsomani.birthdayautopilot.lifecycle

/**
 * Fail-closed process-start boundary for deletion recovery.
 *
 * Firebase Auth can restore a user after a process dies between recovery reauthentication and the
 * deletion replay. Durable deletion evidence, rather than that SDK session, decides whether the
 * session may be used by ordinary account, setup, or automation paths.
 */
internal object DeletionRecoveryStartupPolicy {
  fun requiresIdentitySessionClear(
    receiptLookup: DeletionReceiptLookup,
    journalStatus: LifecycleJournalStatus,
    operation: DurablePrivacyOperation?,
  ): Boolean {
    if (journalStatus == LifecycleJournalStatus.UNREADABLE) return true
    if (receiptLookup == DeletionReceiptLookup.Unavailable) return true
    if (
      receiptLookup is DeletionReceiptLookup.Present &&
      receiptLookup.receipt.state == DurableDeletionReceipt.State.PENDING
    ) return true

    // Covers a crash after the local-erased operation write when the independent receipt slot is
    // missing, stale, or already completed but operation reconciliation has not run yet.
    return operation?.let {
      it.action == "delete-account" &&
        it.state !in setOf("complete", "failed") &&
        it.localDataErased
    } == true
  }
}

/** Testable coordinator for the SDK side effect; durable evidence always gates ordinary use. */
internal class DeletionRecoveryIdentitySessionGuard(
  private val boundaryRequired: () -> Boolean,
  private val clearSession: () -> Boolean,
) {
  @Synchronized
  fun clearIfRequired(): Boolean = !boundaryRequired() || clearSession()

  fun ordinaryIdentityUseAllowed(): Boolean = !boundaryRequired()
}
