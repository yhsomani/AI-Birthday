package com.yashsomani.birthdayautopilot.storage.database

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.Serializable

class ArmedAttemptPermitTest {
  @Test
  fun `issued capability is bound and not serializable`() {
    val permit = ArmedAttemptPermitIssuer.issue(
      barrierPermit(),
      "send-1",
      foregroundConfirmationNonceHash = "nonce-hash",
    )

    assertEquals("permit-1", permit.permitId)
    assertEquals("send-1", permit.sendAttemptId)
    assertEquals("nonce-hash", permit.foregroundConfirmationNonceHash)
    assertEquals(1_900L, permit.deadlineElapsedRealtimeMillis)
    assertFalse(permit is Serializable)
    assertEquals("ArmedAttemptPermit(<redacted>)", permit.toString())
  }

  @Test
  fun `elapsed deadline overflow fails closed`() {
    val overflowing = barrierPermit().copy(
      requestStartElapsedMillis = Long.MAX_VALUE - 10,
      trustedServerNowMillis = 1_000,
      effectiveSubmitNotAfterMillis = 2_000,
    )

    val error = assertThrows(IllegalStateException::class.java) {
      ArmedAttemptPermitIssuer.issue(
        overflowing,
        "send-overflow",
        foregroundConfirmationNonceHash = "nonce-hash",
      )
    }
    assertEquals("elapsed-deadline-overflow", error.message)
  }

  @Test
  fun `test capability cannot be issued without its foreground confirmation binding`() {
    val error = assertThrows(IllegalStateException::class.java) {
      ArmedAttemptPermitIssuer.issue(barrierPermit(), "send-without-confirmation")
    }
    assertEquals("foreground-confirmation-binding-invalid", error.message)
  }

  private fun barrierPermit() = CoordinationPermitEntity(
    permitId = "permit-1",
    accountId = "account-1",
    installationId = "installation-1",
    senderEpoch = 1,
    resetGeneration = 1,
    purpose = OperationPurpose.TEST,
    operationId = "test-1",
    attemptNumber = 1,
    payloadHash = "payload",
    opaqueClaimId = "claim",
    opaqueDestinationGuardId = null,
    claimRequestId = "claim-request",
    armRequestId = "4f013554-82c7-4d50-88e1-0257c16ba484",
    state = CoordinationPermitState.BARRIER_CONSUMED,
    armDispatched = true,
    armStartBlockerRevision = 0,
    claimExpiresAtMillis = 1_500,
    maxPossibleSubmitNotAfterMillis = 2_000,
    unresolvedArmCutoffMillis = 2_000,
    trustedServerNowMillis = 1_100,
    requestStartElapsedMillis = 1_000,
    bootCount = 1,
    serverSubmitNotAfterMillis = 2_000,
    effectiveSubmitNotAfterMillis = 2_000,
    noWriteReason = null,
    revision = 2,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_100,
    barrierConsumedAtMillis = 1_100,
    retentionUntilMillis = 10_000,
  )
}
