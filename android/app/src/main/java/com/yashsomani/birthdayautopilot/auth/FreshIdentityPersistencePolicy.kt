package com.yashsomani.birthdayautopilot.auth

internal enum class FreshIdentityAttachFailureDecision {
  ACCEPT_DURABLE_BINDING,
  REMOVE_FRESH_USER,
  SIGN_OUT_ONLY,
}

/** Only Firebase's per-exchange isNewUser proof can authorize deleting an Auth user. */
internal object FreshIdentityPersistencePolicy {
  fun afterAccountConflict(isNewUser: Boolean): FreshIdentityAttachFailureDecision =
    if (isNewUser) {
      FreshIdentityAttachFailureDecision.REMOVE_FRESH_USER
    } else {
      FreshIdentityAttachFailureDecision.SIGN_OUT_ONLY
    }

  fun afterStorageFailure(
    isNewUser: Boolean,
    persistenceStatus: IdentityBindingPersistenceStatus,
  ): FreshIdentityAttachFailureDecision = when {
    !isNewUser -> FreshIdentityAttachFailureDecision.SIGN_OUT_ONLY
    persistenceStatus == IdentityBindingPersistenceStatus.PRESENT ->
      FreshIdentityAttachFailureDecision.ACCEPT_DURABLE_BINDING
    persistenceStatus == IdentityBindingPersistenceStatus.ABSENT ->
      FreshIdentityAttachFailureDecision.REMOVE_FRESH_USER
    else -> FreshIdentityAttachFailureDecision.SIGN_OUT_ONLY
  }
}
