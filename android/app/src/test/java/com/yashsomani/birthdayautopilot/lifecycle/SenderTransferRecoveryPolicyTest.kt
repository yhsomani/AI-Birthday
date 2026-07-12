package com.yashsomani.birthdayautopilot.lifecycle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SenderTransferRecoveryPolicyTest {
  @Test
  fun writeAheadCompletionMarkerRequiresRegistrationBeforeAnotherMutation() {
    assertTrue(SenderTransferRecoveryPolicy.requiresAuthoritativeRegistration("remote-pending"))
    assertFalse(SenderTransferRecoveryPolicy.requiresAuthoritativeRegistration("remote-draining"))
    assertFalse(SenderTransferRecoveryPolicy.requiresAuthoritativeRegistration("complete"))
  }
}
