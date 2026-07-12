package com.yashsomani.birthdayautopilot.lifecycle

/** Any independent deletion receipt routes to deletion support, never generic lifecycle repair. */
internal object LifecycleRepairIdentityPolicy {
  fun explicitRepairAllowed(
    lookup: DeletionReceiptLookup,
    hasPreexistingActiveAccount: Boolean,
  ): Boolean = hasPreexistingActiveAccount && lookup == DeletionReceiptLookup.None
}
