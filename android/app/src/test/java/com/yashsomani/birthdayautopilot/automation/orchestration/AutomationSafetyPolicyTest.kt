package com.yashsomani.birthdayautopilot.automation.orchestration

import com.yashsomani.birthdayautopilot.storage.database.ClockTrustEntity
import com.yashsomani.birthdayautopilot.storage.database.ClockTrustStatus
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitEntity
import com.yashsomani.birthdayautopilot.storage.database.CoordinationPermitState
import com.yashsomani.birthdayautopilot.storage.database.OperationPurpose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSafetyPolicyTest {
  @Test
  fun `only never-dispatched cloud claim can produce the one Arm dispatch action`() {
    assertEquals(
      ArmRecoveryAction.DISPATCH_ONCE,
      ArmRecoveryPolicy.decide(cloudClaimed(), BOOT, 1_100),
    )
    assertEquals(
      ArmRecoveryAction.CLOSE_UNKNOWN,
      ArmRecoveryPolicy.decide(
        cloudClaimed().copy(armDispatched = true, armRequestId = ARM_REQUEST_ID),
        BOOT,
        1_100,
      ),
    )
  }

  @Test
  fun `persisted Arm dispatch is status-query-only and never redispatched`() {
    val dispatched = armReconciling()
    assertEquals(
      ArmRecoveryAction.QUERY_EXACT_STATUS,
      ArmRecoveryPolicy.decide(dispatched, BOOT, 2_000),
    )
    assertNotEquals(
      ArmRecoveryAction.DISPATCH_ONCE,
      ArmRecoveryPolicy.decide(dispatched, BOOT, 2_000),
    )
  }

  @Test
  fun `known cutoff gets one exact status query before fail-closed terminalization`() {
    assertEquals(
      ArmRecoveryAction.QUERY_EXACT_STATUS_THEN_CLOSE,
      ArmRecoveryPolicy.decide(armReconciling(), BOOT, 6_000),
    )
  }

  @Test
  fun `reboot or elapsed rollback closes ambiguity with zero capability`() {
    assertEquals(
      ArmRecoveryAction.CLOSE_UNKNOWN,
      ArmRecoveryPolicy.decide(armReconciling(), BOOT + 1, 2_000),
    )
    assertEquals(
      ArmRecoveryAction.CLOSE_UNKNOWN,
      ArmRecoveryPolicy.decide(armReconciling(), BOOT, 999),
    )
  }

  @Test
  fun `trusted time advances only on the same boot and monotonic elapsed clock`() {
    val trust = ClockTrustEntity(
      accountId = "account",
      status = ClockTrustStatus.TRUSTED,
      greatestTrustedServerMillis = 10_000,
      lastDeviceWallMillis = 10_000,
      lastElapsedRealtimeMillis = 1_000,
      trustedBootCount = BOOT,
      lastVerificationMillis = 10_000,
      observedDriftMillis = 0,
      revision = 0,
    )
    assertEquals(11_500L, TrustedTimeEstimator.estimate(trust, 2_500, BOOT))
    assertNull(TrustedTimeEstimator.estimate(trust, 2_500, BOOT + 1))
    assertNull(TrustedTimeEstimator.estimate(trust, 999, BOOT))
    assertNull(
      TrustedTimeEstimator.estimate(
        trust.copy(status = ClockTrustStatus.DRIFTED),
        2_500,
        BOOT,
      ),
    )
  }

  @Test
  fun `opaque request ids are deterministic purpose-separated and schema-valid`() {
    val birthday = AutomationOpaqueIds.uuid("BirthdayClaimRequest.v1", "account", "operation")
    val replay = AutomationOpaqueIds.uuid("BirthdayClaimRequest.v1", "account", "operation")
    val arm = AutomationOpaqueIds.uuid("ArmRequest.v1", "account", "operation")
    assertEquals(birthday, replay)
    assertNotEquals(birthday, arm)
    assertTrue(UUID_V5.matches(birthday))
    assertTrue(AutomationOpaqueIds.sha256("basis", "value").matches(SHA_256))
  }

  private fun cloudClaimed() = CoordinationPermitEntity(
    permitId = "permit",
    accountId = "account",
    installationId = "0123456789abcdef0123456789abcdef",
    senderEpoch = 1,
    resetGeneration = 1,
    purpose = OperationPurpose.BIRTHDAY,
    operationId = "operation",
    attemptNumber = 1,
    payloadHash = "a".repeat(64),
    opaqueClaimId = "opaque-claim",
    opaqueDestinationGuardId = null,
    claimRequestId = CLAIM_REQUEST_ID,
    armRequestId = null,
    state = CoordinationPermitState.CLOUD_CLAIMED,
    armDispatched = false,
    armStartBlockerRevision = null,
    claimExpiresAtMillis = 5_000,
    maxPossibleSubmitNotAfterMillis = 6_000,
    unresolvedArmCutoffMillis = 6_000,
    trustedServerNowMillis = 1_000,
    requestStartElapsedMillis = 1_000,
    bootCount = BOOT,
    serverSubmitNotAfterMillis = null,
    effectiveSubmitNotAfterMillis = null,
    noWriteReason = null,
    revision = 0,
    createdAtMillis = 1_000,
    updatedAtMillis = 1_000,
    barrierConsumedAtMillis = null,
    retentionUntilMillis = 100_000,
  )

  private fun armReconciling() = cloudClaimed().copy(
    armRequestId = ARM_REQUEST_ID,
    state = CoordinationPermitState.ARM_RECONCILING,
    armDispatched = true,
    armStartBlockerRevision = 4,
    revision = 1,
  )

  private companion object {
    const val BOOT = 7
    const val CLAIM_REQUEST_ID = "6001d891-d2c8-5fad-a4fb-2a6ef1d03cf8"
    const val ARM_REQUEST_ID = "1b22e526-0a51-56bf-bf72-d7f3c03c5f3e"
    val UUID_V5 = Regex(
      "^[a-f0-9]{8}-[a-f0-9]{4}-5[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$",
    )
    val SHA_256 = Regex("^[a-f0-9]{64}$")
  }
}
