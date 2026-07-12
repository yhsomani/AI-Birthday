package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeletionRecoveryStartupPolicyTest {
  @Test
  fun `pending unreadable and partially reconciled deletion evidence clears restored session`() {
    assertTrue(
      DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
        pendingReceipt(),
        LifecycleJournalStatus.READABLE,
        null,
      ),
    )
    assertTrue(
      DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
        DeletionReceiptLookup.Unavailable,
        LifecycleJournalStatus.READABLE,
        null,
      ),
    )
    assertTrue(
      DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
        DeletionReceiptLookup.None,
        LifecycleJournalStatus.UNREADABLE,
        null,
      ),
    )
    assertTrue(
      DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
        completedReceipt(),
        LifecycleJournalStatus.READABLE,
        operation(state = "remote-pending", localDataErased = true),
      ),
    )
  }

  @Test
  fun `ordinary identity use resumes only after terminal or unrelated evidence`() {
    assertFalse(
      DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
        completedReceipt(),
        LifecycleJournalStatus.READABLE,
        operation(state = "complete", localDataErased = true),
      ),
    )
    assertFalse(
      DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
        DeletionReceiptLookup.None,
        LifecycleJournalStatus.ABSENT,
        operation(state = "remote-pending", localDataErased = false),
      ),
    )
    assertFalse(
      DeletionRecoveryStartupPolicy.requiresIdentitySessionClear(
        DeletionReceiptLookup.None,
        LifecycleJournalStatus.READABLE,
        operation(state = "remote-pending", localDataErased = true)
          .copy(action = "sender-transfer"),
      ),
    )
  }

  @Test
  fun `failed SDK cleanup cannot enable ordinary identity and remains retryable`() {
    var required = true
    var cleanupAttempts = 0
    var cleanupSucceeds = false
    val guard = DeletionRecoveryIdentitySessionGuard(
      boundaryRequired = { required },
      clearSession = {
        cleanupAttempts += 1
        cleanupSucceeds
      },
    )

    assertFalse(guard.clearIfRequired())
    assertFalse(guard.ordinaryIdentityUseAllowed())
    cleanupSucceeds = true
    assertTrue(guard.clearIfRequired())
    assertFalse(guard.ordinaryIdentityUseAllowed())
    assertTrue(cleanupAttempts == 2)

    required = false
    assertTrue(guard.clearIfRequired())
    assertTrue(guard.ordinaryIdentityUseAllowed())
    assertTrue(cleanupAttempts == 2)
  }

  private fun operation(
    state: String,
    localDataErased: Boolean,
  ) = DurablePrivacyOperation(
    id = OPERATION_ID,
    action = "delete-account",
    state = state,
    reason = null,
    updatedAtMillis = 1_000,
    completedAtMillis = 1_000L.takeIf { state == "complete" },
    requestId = REQUEST_ID,
    localDataErased = localDataErased,
    remoteDeletionComplete = state == "complete",
  )

  private fun pendingReceipt() = DeletionReceiptLookup.Present(
    DurableDeletionReceipt(
      operationId = OPERATION_ID,
      receiptId = REQUEST_ID,
      state = DurableDeletionReceipt.State.PENDING,
      updatedAtMillis = 1_000,
      completedAtMillis = null,
      cleanupAtMillis = null,
    ),
  )

  private fun completedReceipt() = DeletionReceiptLookup.Present(
    DurableDeletionReceipt(
      operationId = OPERATION_ID,
      receiptId = REQUEST_ID,
      state = DurableDeletionReceipt.State.COMPLETED,
      updatedAtMillis = 2_000,
      completedAtMillis = 2_000,
      cleanupAtMillis = 2_000 + DeletionReceiptStore.COMPLETED_RETENTION_MILLIS,
    ),
  )

  private companion object {
    const val OPERATION_ID = "privacy_0123456789abcdef0123456789abcdef"
    const val REQUEST_ID = "00000000-0000-4000-8000-000000000001"
  }
}
