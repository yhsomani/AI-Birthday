package com.yashsomani.birthdayautopilot.auth

internal enum class DeletionRecoveryPostExchangeDecision {
  ACCEPT_EXACT_ACCOUNT,
  REMOVE_REPLACEMENT_USER,
  REJECT_EXISTING_MISMATCH,
}

/** A matching Google subject is prechecked before this Firebase post-exchange decision. */
internal object DeletionRecoveryIdentityPolicy {
  fun afterFirebaseExchange(
    exactBindingMatches: Boolean,
    isNewUser: Boolean,
  ): DeletionRecoveryPostExchangeDecision = when {
    isNewUser -> DeletionRecoveryPostExchangeDecision.REMOVE_REPLACEMENT_USER
    exactBindingMatches -> DeletionRecoveryPostExchangeDecision.ACCEPT_EXACT_ACCOUNT
    else -> DeletionRecoveryPostExchangeDecision.REJECT_EXISTING_MISMATCH
  }
}
