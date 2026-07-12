package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeletionReceiptAccountPolicyTest {
  @Test
  fun `pending and unreadable receipts block every account setup projection`() {
    assertEquals(
      "coordination-unavailable",
      DeletionReceiptAccountPolicy.blockerCode(DeletionReceiptLookup.Unavailable),
    )
    assertEquals(
      "firebase-account-deleting",
      DeletionReceiptAccountPolicy.blockerCode(
        DeletionReceiptLookup.Present(receipt(DurableDeletionReceipt.State.PENDING)),
      ),
    )
  }

  @Test
  fun `completed and expired receipts do not block a new setup`() {
    assertNull(DeletionReceiptAccountPolicy.blockerCode(DeletionReceiptLookup.None))
    assertNull(
      DeletionReceiptAccountPolicy.blockerCode(
        DeletionReceiptLookup.Present(receipt(DurableDeletionReceipt.State.COMPLETED)),
      ),
    )
  }

  private fun receipt(state: DurableDeletionReceipt.State) = DurableDeletionReceipt(
    operationId = "privacy_0123456789abcdef0123456789abcdef",
    receiptId = "00000000-0000-4000-8000-000000000001",
    state = state,
    updatedAtMillis = 1_000,
    completedAtMillis = 1_000L.takeIf { state == DurableDeletionReceipt.State.COMPLETED },
    cleanupAtMillis = (1_000L + DeletionReceiptStore.COMPLETED_RETENTION_MILLIS)
      .takeIf { state == DurableDeletionReceipt.State.COMPLETED },
  )
}
