package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderReleaseRecoveryStartupPolicyTest {
  @Test
  fun `only local-erased nonterminal sender release clears restored identity`() {
    val pending = operation(state = "remote-pending", localDataErased = true)
    assertTrue(SenderReleaseRecoveryStartupPolicy.requiresIdentitySessionClear(pending))
    assertTrue(
      SenderReleaseRecoveryStartupPolicy.requiresIdentitySessionClear(
        pending.copy(state = "remote-draining"),
      ),
    )
    assertFalse(
      SenderReleaseRecoveryStartupPolicy.requiresIdentitySessionClear(
        operation(state = "local-wiping", localDataErased = false),
      ),
    )
    assertFalse(
      SenderReleaseRecoveryStartupPolicy.requiresIdentitySessionClear(
        operation(state = "complete", localDataErased = true),
      ),
    )
    assertFalse(
      SenderReleaseRecoveryStartupPolicy.requiresIdentitySessionClear(
        pending.copy(action = "delete-account"),
      ),
    )
  }

  private fun operation(
    state: String,
    localDataErased: Boolean,
  ) = DurablePrivacyOperation(
    id = "privacy_0123456789abcdef0123456789abcdef",
    action = "sign-out-wipe",
    state = state,
    reason = "coordination-unavailable".takeIf { state == "remote-pending" },
    updatedAtMillis = 1_000,
    completedAtMillis = 2_000L.takeIf { state == "complete" },
    requestId = "00000000-0000-4000-8000-000000000001",
    localDataErased = localDataErased,
  )
}
