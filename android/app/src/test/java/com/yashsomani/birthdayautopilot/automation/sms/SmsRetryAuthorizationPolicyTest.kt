package com.yashsomani.birthdayautopilot.automation.sms

import com.yashsomani.birthdayautopilot.coordination.CoordinationPurpose
import com.yashsomani.birthdayautopilot.coordination.RetryProof
import com.yashsomani.birthdayautopilot.coordination.ServerClaim
import com.yashsomani.birthdayautopilot.coordination.ServerClaimState
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitState
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRetryAuthorizationPolicyTest {
  @Test
  fun `retry unresolved arm cutoff never outlives birthday window`() {
    assertEquals(
      3_500,
      BirthdayRetryPermitPolicy.unresolvedArmCutoffMillis(
        maxPossibleSubmitNotAfterMillis = 4_000,
        resolvedWindowEndMillis = 3_500,
      ),
    )
    assertEquals(
      3_500,
      BirthdayRetryPermitPolicy.unresolvedArmCutoffMillis(
        maxPossibleSubmitNotAfterMillis = 3_500,
        resolvedWindowEndMillis = 4_000,
      ),
    )
  }

  @Test
  fun `only exact bound attempt two retry claim is accepted`() {
    val permit = permit()
    val claim = claim()
    assertTrue(
      SmsRetryAuthorizationPolicy.isValid(
        claim,
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim.copy(ownerInstallationId = "other-installation"),
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim,
        permit,
        "40000000-0000-4000-8000-000000000004",
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim,
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_RADIO_OFF,
        2_000,
      ),
    )
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim.copy(state = ServerClaimState.ARMED, attempt = 1),
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim.copy(serverSubmitNotAfterMillis = 3_500),
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
  }

  @Test
  fun `invalid retry deadline ordering and implausible future server time fail closed`() {
    val permit = permit()
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim().copy(claimExpiresAtMillis = 1_500),
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim().copy(maxPossibleSubmitNotAfterMillis = 2_500),
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
    assertFalse(
      SmsRetryAuthorizationPolicy.isValid(
        claim().copy(serverObservedAtMillis = 400_001),
        permit,
        RETRY_REQUEST_ID,
        RetryProof.ALL_PARTS_NO_SERVICE,
        2_000,
      ),
    )
  }

  @Test
  fun `retry request identity is stable per original permit and distinct across permits`() {
    val first = SmsRetryRequestIdentity.forPermit("permit-1")
    assertTrue(first == SmsRetryRequestIdentity.forPermit("permit-1"))
    assertFalse(first == SmsRetryRequestIdentity.forPermit("permit-2"))
  }

  private fun claim() = ServerClaim(
    claimId = "opaque-claim-123",
    purpose = CoordinationPurpose.BIRTHDAY,
    ownerInstallationId = "installation-1",
    ownerEpoch = 7,
    resetGeneration = 3,
    state = ServerClaimState.RETRY_CLAIMED,
    attempt = 2,
    claimExpiresAtMillis = 3_000,
    maxPossibleSubmitNotAfterMillis = 4_000,
    serverSubmitNotAfterMillis = 2_000,
    testBarrierOutcome = null,
    serverObservedAtMillis = 2_000,
    retryRequestId = RETRY_REQUEST_ID,
    retryProof = RetryProof.ALL_PARTS_NO_SERVICE,
  )

  private fun permit() = CoordinationPermitEntity(
    permitId = "permit-1",
    accountId = "account-1",
    installationId = "installation-1",
    senderEpoch = 7,
    resetGeneration = 3,
    purpose = OperationPurpose.BIRTHDAY,
    operationId = "occurrence-1",
    attemptNumber = 1,
    payloadHash = "payload",
    opaqueClaimId = "opaque-claim-123",
    opaqueDestinationGuardId = "guard-1",
    claimRequestId = "request-1",
    armRequestId = "arm-1",
    state = CoordinationPermitState.BARRIER_CONSUMED,
    armDispatched = true,
    armStartBlockerRevision = 1,
    claimExpiresAtMillis = 1_500,
    maxPossibleSubmitNotAfterMillis = 2_500,
    unresolvedArmCutoffMillis = 2_500,
    trustedServerNowMillis = 1_000,
    requestStartElapsedMillis = 100,
    bootCount = 1,
    serverSubmitNotAfterMillis = 2_000,
    effectiveSubmitNotAfterMillis = 2_000,
    noWriteReason = null,
    revision = 3,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
    barrierConsumedAtMillis = 1_100,
    retentionUntilMillis = 99_000,
  )

  private companion object {
    const val RETRY_REQUEST_ID = "30000000-0000-4000-8000-000000000003"
  }
}
