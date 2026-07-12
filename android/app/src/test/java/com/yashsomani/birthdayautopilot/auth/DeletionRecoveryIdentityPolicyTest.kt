package com.yashsomani.birthdayautopilot.auth

import org.junit.Assert.assertEquals
import org.junit.Test

class DeletionRecoveryIdentityPolicyTest {
  @Test
  fun `only an existing exact firebase binding is accepted`() {
    assertEquals(
      DeletionRecoveryPostExchangeDecision.ACCEPT_EXACT_ACCOUNT,
      DeletionRecoveryIdentityPolicy.afterFirebaseExchange(
        exactBindingMatches = true,
        isNewUser = false,
      ),
    )
  }

  @Test
  fun `only a user freshly created by this exchange may be removed`() {
    assertEquals(
      DeletionRecoveryPostExchangeDecision.REMOVE_REPLACEMENT_USER,
      DeletionRecoveryIdentityPolicy.afterFirebaseExchange(
        exactBindingMatches = false,
        isNewUser = true,
      ),
    )
    assertEquals(
      DeletionRecoveryPostExchangeDecision.REJECT_EXISTING_MISMATCH,
      DeletionRecoveryIdentityPolicy.afterFirebaseExchange(
        exactBindingMatches = false,
        isNewUser = false,
      ),
    )
    assertEquals(
      DeletionRecoveryPostExchangeDecision.REMOVE_REPLACEMENT_USER,
      DeletionRecoveryIdentityPolicy.afterFirebaseExchange(
        exactBindingMatches = true,
        isNewUser = true,
      ),
    )
  }
}
