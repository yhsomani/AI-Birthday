package com.yashsomani.birthdayautopilot.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class FreshIdentityPersistencePolicyTest {
  @Test
  fun `conflict deletes only a user proven fresh by this exchange`() {
    assertEquals(
      FreshIdentityAttachFailureDecision.REMOVE_FRESH_USER,
      FreshIdentityPersistencePolicy.afterAccountConflict(isNewUser = true),
    )
    assertEquals(
      FreshIdentityAttachFailureDecision.SIGN_OUT_ONLY,
      FreshIdentityPersistencePolicy.afterAccountConflict(isNewUser = false),
    )
  }

  @Test
  fun `ambiguous storage accepts durable reread and deletes only proven absence`() {
    assertEquals(
      FreshIdentityAttachFailureDecision.ACCEPT_DURABLE_BINDING,
      FreshIdentityPersistencePolicy.afterStorageFailure(
        isNewUser = true,
        persistenceStatus = IdentityBindingPersistenceStatus.PRESENT,
      ),
    )
    assertEquals(
      FreshIdentityAttachFailureDecision.REMOVE_FRESH_USER,
      FreshIdentityPersistencePolicy.afterStorageFailure(
        isNewUser = true,
        persistenceStatus = IdentityBindingPersistenceStatus.ABSENT,
      ),
    )
    assertEquals(
      FreshIdentityAttachFailureDecision.SIGN_OUT_ONLY,
      FreshIdentityPersistencePolicy.afterStorageFailure(
        isNewUser = true,
        persistenceStatus = IdentityBindingPersistenceStatus.UNAVAILABLE,
      ),
    )
    assertEquals(
      FreshIdentityAttachFailureDecision.SIGN_OUT_ONLY,
      FreshIdentityPersistencePolicy.afterStorageFailure(
        isNewUser = false,
        persistenceStatus = IdentityBindingPersistenceStatus.ABSENT,
      ),
    )
  }
}
