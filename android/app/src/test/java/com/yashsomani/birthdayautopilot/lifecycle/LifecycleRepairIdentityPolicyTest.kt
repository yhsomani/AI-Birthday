package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifecycleRepairIdentityPolicyTest {
  @Test
  fun `explicit exact-account repair requires no independent deletion receipt`() {
    assertFalse(
      LifecycleRepairIdentityPolicy.explicitRepairAllowed(
        DeletionReceiptLookup.Unavailable,
        hasPreexistingActiveAccount = true,
      ),
    )
    assertFalse(
      LifecycleRepairIdentityPolicy.explicitRepairAllowed(
        receipt(pending = true),
        hasPreexistingActiveAccount = true,
      ),
    )
    assertFalse(
      LifecycleRepairIdentityPolicy.explicitRepairAllowed(
        receipt(pending = false),
        hasPreexistingActiveAccount = true,
      ),
    )
    assertTrue(
      LifecycleRepairIdentityPolicy.explicitRepairAllowed(
        DeletionReceiptLookup.None,
        hasPreexistingActiveAccount = true,
      ),
    )
    assertFalse(
      LifecycleRepairIdentityPolicy.explicitRepairAllowed(
        receipt(pending = false),
        hasPreexistingActiveAccount = false,
      ),
    )
  }

  private fun receipt(pending: Boolean) = DeletionReceiptLookup.Present(
    DurableDeletionReceipt(
      operationId = "privacy_0123456789abcdef0123456789abcdef",
      receiptId = "00000000-0000-4000-8000-000000000001",
      state = if (pending) {
        DurableDeletionReceipt.State.PENDING
      } else {
        DurableDeletionReceipt.State.COMPLETED
      },
      updatedAtMillis = 1_000,
      completedAtMillis = 1_000L.takeUnless { pending },
      cleanupAtMillis = (1_000L + DeletionReceiptStore.COMPLETED_RETENTION_MILLIS)
        .takeUnless { pending },
    ),
  )
}
