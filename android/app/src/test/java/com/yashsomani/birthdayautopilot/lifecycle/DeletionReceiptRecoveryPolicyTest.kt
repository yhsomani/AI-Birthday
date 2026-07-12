package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeletionReceiptRecoveryPolicyTest {
  @Test
  fun `slot absence resumes a pre-wipe deletion after process death`() {
    assertEquals(
      OPERATION_ID,
      DeletionReceiptRecoveryPolicy.interruptedOperationId(
        DeletionReceiptLookup.None,
        operation("remote-draining"),
      ),
    )
  }

  @Test
  fun `receipt evidence and terminal or unrelated operations never restart deletion`() {
    assertNull(
      DeletionReceiptRecoveryPolicy.interruptedOperationId(
        DeletionReceiptLookup.Unavailable,
        operation("remote-draining"),
      ),
    )
    assertNull(
      DeletionReceiptRecoveryPolicy.interruptedOperationId(
        DeletionReceiptLookup.None,
        operation("complete"),
      ),
    )
    assertNull(
      DeletionReceiptRecoveryPolicy.interruptedOperationId(
        DeletionReceiptLookup.None,
        operation("remote-draining").copy(action = "disconnect-contacts"),
      ),
    )
  }

  private fun operation(state: String) = DurablePrivacyOperation(
    id = OPERATION_ID,
    action = "delete-account",
    state = state,
    reason = null,
    updatedAtMillis = 1_000,
    completedAtMillis = 1_000L.takeIf { state == "complete" },
  )

  private companion object {
    const val OPERATION_ID = "privacy_0123456789abcdef0123456789abcdef"
  }
}
