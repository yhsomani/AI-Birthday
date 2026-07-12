package com.yashsomani.birthdayautopilot.lifecycle

internal object DeletionReceiptAccountPolicy {
  fun blockerCode(lookup: DeletionReceiptLookup): String? = when (lookup) {
    DeletionReceiptLookup.None -> null
    DeletionReceiptLookup.Unavailable -> "coordination-unavailable"
    is DeletionReceiptLookup.Present -> when (lookup.receipt.state) {
      DurableDeletionReceipt.State.PENDING -> "firebase-account-deleting"
      DurableDeletionReceipt.State.COMPLETED -> null
    }
  }
}
